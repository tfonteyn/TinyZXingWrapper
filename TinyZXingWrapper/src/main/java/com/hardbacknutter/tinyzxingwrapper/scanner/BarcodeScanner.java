package com.hardbacknutter.tinyzxingwrapper.scanner;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.hardbacknutter.tinyzxingwrapper.BuildConfig;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings({"ClassWithOnlyPrivateConstructors", "WeakerAccess"})
public class BarcodeScanner
        implements LifecycleEventObserver {

    private static final String TAG = "BarcodeScanner";
    /**
     * Executor used by the image analyser.
     */
    @NonNull
    private final ExecutorService cameraExecutor;
    @NonNull
    private final ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    @NonNull
    private final Preview.SurfaceProvider surfaceProvider;
    @NonNull
    private final Executor mainExecutor;
    @NonNull
    private final LifecycleOwner lifecycleOwner;
    private final Object lock = new Object();

    @NonNull
    private final DecoderFactory decoderFactory;
    @NonNull
    private final ScanMode scanMode;
    /**
     * Default is {@code null} which lets the device decide.
     * Otherwise one of {@link CameraSelector#LENS_FACING_FRONT} or
     * {@link CameraSelector#LENS_FACING_BACK}
     */
    @Nullable
    private Integer lensFacing;
    private boolean enableTorch;
    @Nullable
    private DecoderResultPointsListener resultPointsListener;
    @GuardedBy("lock")
    @Nullable
    private ProcessCameraProvider cameraProvider;
    @GuardedBy("lock")
    @Nullable
    private CameraControl cameraControl;

    private BarcodeScanner(@NonNull final Context context,
                           @NonNull final LifecycleOwner lifecycleOwner,
                           @NonNull final Preview.SurfaceProvider surfaceProvider,
                           @NonNull final Builder builder) {
        cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        mainExecutor = ContextCompat.getMainExecutor(context);
        cameraExecutor = Executors.newSingleThreadExecutor();

        this.lifecycleOwner = lifecycleOwner;
        this.surfaceProvider = surfaceProvider;

        decoderFactory = new DefaultDecoderFactory(builder.decoderType, builder.hints);

        scanMode = builder.scanMode;
    }

    /**
     * Optionally set the listener to be informed of possible {@link ResultPoint}s found.
     */
    public void setResultPointListener(@Nullable final DecoderResultPointsListener listener) {
        this.resultPointsListener = listener;
    }

    /**
     * Switch the torch (flashlight) on or off. Takes effect immediately.
     *
     * @param enable flag
     */
    public void setTorch(final boolean enable) {
        enableTorch = enable;
        synchronized (lock) {
            if (cameraControl != null) {
                cameraControl.enableTorch(enableTorch);
            }
        }
    }

    /**
     * Set the preferred camera (lens-facing) to use.
     * Only takes effect when called before {@link #startScan(DecoderResultListener)}.
     * <p>
     * One of:
     * <ul>
     *     <li>{@link CameraSelector#LENS_FACING_FRONT}</li>
     *     <li>{@link CameraSelector#LENS_FACING_BACK}</li>
     * </ul>
     *
     * @param lensFacing preferred
     */
    public void setCameraLensFacing(final int lensFacing) {
        if (lensFacing == CameraSelector.LENS_FACING_BACK
                || lensFacing == CameraSelector.LENS_FACING_FRONT) {
            this.lensFacing = lensFacing;
        } else {
            this.lensFacing = null;
        }
    }

    public void startScan(@NonNull final DecoderResultListener resultListener) {
        cameraProviderFuture.addListener(
                () -> {
                    try {
                        final boolean isImageFlipped;
                        final CameraSelector.Builder csb = new CameraSelector.Builder();
                        if (lensFacing != null) {
                            csb.requireLensFacing(lensFacing);
                            isImageFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT;
                        } else {
                            isImageFlipped = false;
                        }
                        final CameraSelector cameraSelector = csb.build();

                        final Decoder decoder = decoderFactory.createDecoder();

                        final Preview preview = new Preview.Builder().build();
                        preview.setSurfaceProvider(surfaceProvider);

                        final ImageCapture imageCapture = new ImageCapture.Builder().build();

                        final ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder().build();
                        imageAnalyzer.setAnalyzer(cameraExecutor, image -> {
                            // we need to close() the image in this method. Don't pass it outside!
                            try (image) {
                                RawImageData riData;

                                try {
                                    final ByteBuffer pixelBuffer = image.getPlanes()[0].getBuffer();
                                    pixelBuffer.rewind();
                                    final byte[] data = new byte[pixelBuffer.remaining()];
                                    pixelBuffer.get(data);

                                    riData = new RawImageData(
                                            data, image.getWidth(), image.getHeight());

                                    final int rotationDegrees = image.getImageInfo()
                                            .getRotationDegrees();
                                    if (rotationDegrees != 0) {
                                        riData = riData.rotate(rotationDegrees);
                                    }
                                } catch (@NonNull final Exception e) {
                                    mainExecutor.execute(() -> resultListener.onError(
                                            " ImageAnalysis.Analyzer|image-processing", e));
                                    return;
                                }

                                try {
                                    final Result result = decoder.decode(
                                            riData.toLuminanceSource());
                                    if (result != null) {
                                        mainExecutor.execute(() -> resultListener.onResult(result));
                                        if (scanMode == ScanMode.Single) {
                                            stopScanning();
                                            return;
                                        }
                                    }
                                } catch (@NonNull final Exception e) {
                                    mainExecutor.execute(() -> resultListener.onError(
                                            " ImageAnalysis.Analyzer|decoding", e));
                                    return;
                                }

                                try {
                                    // When using the DefaultDecoderFactory,
                                    // the zxing "MultiFormatReader" will send the possible
                                    // to the decoder during the above decoder.decode() call
                                    // When the decode() call is done (successful or failure),
                                    // we take that collection of ResultPoint's and
                                    // after potentially mirroring the points, forward
                                    // them to the user-settable listener.
                                    final List<ResultPoint> possibleResultPoints = decoder
                                            .getPossibleResultPoints();
                                    if (resultPointsListener != null
                                            && !possibleResultPoints.isEmpty()) {
                                        mainExecutor.execute(() -> {
                                            resultPointsListener.setImageSize(image.getWidth(),
                                                    image.getHeight());

                                            possibleResultPoints.forEach(point -> {
                                                if (isImageFlipped) {
                                                    final float x = image.getWidth() - point.getX();
                                                    final float y = point.getY();
                                                    resultPointsListener.foundPossibleResultPoint(
                                                            new ResultPoint(x, y));
                                                } else {
                                                    resultPointsListener.foundPossibleResultPoint(
                                                            point);
                                                }
                                            });
                                        });
                                    }
                                } catch (@NonNull final Exception e) {
                                    mainExecutor.execute(() -> resultListener.onError(
                                            " ImageAnalysis.Analyzer|result-points", e));
                                }
                            }
                        });

                        synchronized (lock) {
                            cameraProvider = cameraProviderFuture.get();
                            cameraProvider.unbindAll();

                            final Camera camera = cameraProvider
                                    .bindToLifecycle(lifecycleOwner, cameraSelector,
                                            preview,
                                            imageCapture,
                                            imageAnalyzer);

                            cameraControl = camera.getCameraControl();
                            cameraControl.enableTorch(enableTorch);
                        }


                    } catch (@NonNull final ExecutionException | InterruptedException e) {
                        mainExecutor.execute(() -> resultListener.onError(
                                "cameraProviderFuture.addListener", e));
                    }
                },
                mainExecutor);
    }

    public void stopScanning() {
        synchronized (lock) {
            cameraControl = null;
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            }
        }
    }

    @Override
    public void onStateChanged(@NonNull final LifecycleOwner source,
                               @NonNull final Lifecycle.Event event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            cameraExecutor.shutdown();
        }
    }

    /**
     * The builder prepares all/any arguments related to the actual barcode decoding.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static class Builder {
        private final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);

        @Nullable
        private DecoderType decoderType;

        @NonNull
        private ScanMode scanMode = ScanMode.Single;

        public void setScanMode(@NonNull final ScanMode scanMode) {
            this.scanMode = scanMode;
        }

        @NonNull
        public Builder setDecoderType(final int decoderType) {
            this.decoderType = DecoderType.get(decoderType);
            return this;
        }

        @NonNull
        public Builder setDecoderType(@NonNull final DecoderType decoderType) {
            this.decoderType = decoderType;
            return this;
        }

        @NonNull
        public Builder setCodeFamily(@Nullable final BarcodeFamily codeFamily) {
            if (codeFamily != null) {
                this.hints.put(DecodeHintType.POSSIBLE_FORMATS, codeFamily.formats);
            } else {
                this.hints.remove(DecodeHintType.POSSIBLE_FORMATS);
            }
            return this;
        }

        @NonNull
        public Builder setHints(@Nullable final Map<DecodeHintType, Object> hints) {
            if (hints != null) {
                this.hints.putAll(hints);
            }
            return this;
        }

        @NonNull
        public Builder setHints(@Nullable final Bundle hints) {
            this.hints.putAll(parseHints(hints));
            return this;
        }

        @NonNull
        private Map<DecodeHintType, Object> parseHints(@Nullable final Bundle args) {
            final Map<DecodeHintType, Object> result = new EnumMap<>(DecodeHintType.class);

            if (args == null || args.isEmpty()) {
                return result;
            }

            Arrays.stream(DecodeHintType.values())
                    // This one is configured/used internally
                    .filter(hintType -> hintType != DecodeHintType.NEED_RESULT_POINT_CALLBACK)
                    .forEach(hintType -> {
                        final String hintName = hintType.name();
                        if (args.containsKey(hintName)) {
                            if (hintType.getValueType().equals(Void.class)) {
                                // Void hints are just flags: use the constant
                                // specified by the DecodeHintType
                                result.put(hintType, Boolean.TRUE);

                            } else {
                                final Object hintData = args.get(hintName);
                                if (hintType.getValueType().isInstance(hintData)) {
                                    result.put(hintType, hintData);
                                } else {
                                    if (BuildConfig.DEBUG) {
                                        Log.w(TAG, "Ignoring hint " + hintType
                                                + " because it is not assignable from " + hintData);
                                    }
                                }
                            }
                        }
                    });

            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Hints from the Intent: " + result);
            }
            return result;
        }

        @NonNull
        public BarcodeScanner build(@NonNull final Context context,
                                    @NonNull final LifecycleOwner lifecycleOwner,
                                    @NonNull final Preview.SurfaceProvider surfaceProvider) {
            return new BarcodeScanner(context, lifecycleOwner, surfaceProvider, this);
        }
    }

    /**
     * Raw preview data from a camera. Immutable.
     * The image data is always ImageFormat.YUV_420_888.
     * <p>
     * Provide fast rotation and cropping functionality
     * <p>
     * The rotation routines are adapted from
     * <a href="http://stackoverflow.com/a/15775173">stackoverflow</a>
     * but stripped to only use the 'Y' data of the image.
     */
    private static class RawImageData {

        @NonNull
        private final byte[] data;
        private final int width;
        private final int height;

        /**
         * @param data   the image data in ImageFormat.YUV_420_888 format.
         *               The first [width * height] bytes being the luminance data.
         * @param width  the width of the image
         * @param height the height of the image
         */
        RawImageData(@NonNull final byte[] data,
                     final int width,
                     final int height) {
            this.data = data;
            this.width = width;
            this.height = height;

            if (width * height > data.length) {
                throw new IllegalArgumentException("Image data does not match the resolution. "
                        + width + "x" + height + " > "
                        + data.length);
            }
        }

        @NonNull
        LuminanceSource toLuminanceSource() {
            return new PlanarYUVLuminanceSource(
                    data, width, height, 0, 0, width, height, false);
        }

        @SuppressWarnings("unused")
        @NonNull
        RawImageData crop(@NonNull final Rect cropRect) {
            final int cropWidth = cropRect.width();
            final int cropHeight = cropRect.height();

            final byte[] matrix = new byte[cropWidth * cropHeight];

            int inputOffset = cropRect.top * this.width + cropRect.left;
            // Copy one cropped row at a time.
            for (int y = 0; y < cropHeight; y++) {
                final int outputOffset = y * cropWidth;
                System.arraycopy(this.data, inputOffset, matrix, outputOffset, cropWidth);
                inputOffset += this.width;
            }
            return new RawImageData(matrix, cropWidth, cropHeight);
        }

        @NonNull
        RawImageData rotate(final int rotationDegrees) {
            switch (rotationDegrees) {
                case 90:
                    return rotateCW();
                case 180:
                    return rotate180();
                case 270:
                    return rotateCCW();
                case 0:
                default:
                    return this;
            }
        }

        /**
         * Rotate an image by 90 degrees CW. Only the 'Y' data is rotated.
         *
         * @return the rotated data
         */
        private RawImageData rotateCW() {
            final byte[] yuv = new byte[width * height];

            int i = 0;
            for (int x = 0; x < width; x++) {
                for (int y = height - 1; y >= 0; y--) {
                    yuv[i] = data[y * width + x];
                    i++;
                }
            }
            return new RawImageData(yuv, width, height);
        }

        /**
         * Rotate an image by 180 degrees. Only the 'y' data is rotated.
         *
         * @return the rotated data
         */
        private RawImageData rotate180() {
            final int n = width * height;
            final byte[] yuv = new byte[n];

            int i = n - 1;
            for (int j = 0; j < n; j++) {
                yuv[i] = data[j];
                i--;
            }
            return new RawImageData(yuv, width, height);
        }

        /**
         * Rotate an image by 90 degrees CCW. Only the 'y' data is rotated.
         *
         * @return the rotated data
         */
        private RawImageData rotateCCW() {
            final int n = width * height;
            final byte[] yuv = new byte[n];

            int i = n - 1;
            for (int x = 0; x < width; x++) {
                for (int y = height - 1; y >= 0; y--) {
                    yuv[i] = data[y * width + x];
                    i--;
                }
            }
            return new RawImageData(yuv, width, height);
        }
    }

}

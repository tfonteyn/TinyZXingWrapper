package com.hardbacknutter.tinyzxingwrapper.scanner;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    /**
     * Default is {@code null} which lets the device decide.
     * Otherwise one of {@link CameraSelector#LENS_FACING_FRONT} or
     * {@link CameraSelector#LENS_FACING_BACK}
     */
    private final Integer lensFacing;
    private final boolean enableTorch;
    @Nullable
    private final ResultPointListener resultPointCallback;
    @GuardedBy("lock")
    @Nullable
    private ProcessCameraProvider cameraProvider;

    private BarcodeScanner(@NonNull final Context context,
                          @NonNull final LifecycleOwner lifecycleOwner,
                          @NonNull final Preview.SurfaceProvider surfaceProvider,
                           @NonNull final Builder builder) {
        cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        mainExecutor = ContextCompat.getMainExecutor(context);
        cameraExecutor = Executors.newSingleThreadExecutor();

        this.lifecycleOwner = lifecycleOwner;
        this.surfaceProvider = surfaceProvider;

        lensFacing = builder.lensFacing;
        enableTorch = builder.enableTorch;
        decoderFactory = new DefaultDecoderFactory(builder.decoderType, builder.hints);

        resultPointCallback = builder.resultPointCallback;
    }

    @SuppressWarnings("unused")
    public void cancel() {
        synchronized (lock) {
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            }
        }
    }

    public void startScan(@NonNull final ScanResultListener resultListener) {
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
                                    if (resultPointCallback != null
                                            && !possibleResultPoints.isEmpty()) {
                                        mainExecutor.execute(() -> {
                                            resultPointCallback.setImageSize(image.getWidth(),
                                                    image.getHeight());

                                            for (ResultPoint point : possibleResultPoints) {
                                                if (isImageFlipped) {
                                                    final float x = image.getWidth() - point.getX();
                                                    final float y = point.getY();
                                                    resultPointCallback.foundPossibleResultPoint(
                                                            new ResultPoint(x, y));
                                                } else {
                                                    resultPointCallback.foundPossibleResultPoint(
                                                            point);
                                                }
                                            }
                                        });
                                    }
                                } catch (@NonNull final Exception e) {
                                    mainExecutor.execute(() -> resultListener.onError(
                                            " ImageAnalysis.Analyzer|result-points", e));
                                }
                            }
                        });

                        final Camera camera;

                        synchronized (lock) {
                            cameraProvider = cameraProviderFuture.get();
                            cameraProvider.unbindAll();

                            camera = cameraProvider
                                    .bindToLifecycle(lifecycleOwner, cameraSelector,
                                            preview,
                                            imageCapture,
                                            imageAnalyzer);
                        }

                        camera.getCameraControl().enableTorch(enableTorch);

                    } catch (@NonNull final ExecutionException | InterruptedException e) {
                        mainExecutor.execute(() -> resultListener.onError(
                                "cameraProviderFuture.addListener", e));
                    }
                },
                mainExecutor);
    }

    @Override
    public void onStateChanged(@NonNull LifecycleOwner source,
                               @NonNull Lifecycle.Event event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            cameraExecutor.shutdown();
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
                int outputOffset = y * cropWidth;
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


            byte[] yuv = new byte[width * height];

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
            int n = width * height;
            byte[] yuv = new byte[n];

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
            int n = width * height;
            byte[] yuv = new byte[n];

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

    @SuppressWarnings({"UnusedReturnValue", "unused"})
    public static class Builder {
        private final Map<DecodeHintType, Object> hints = new HashMap<>();

        /**
         * a debug flag to enable Log.w() messages
         */
        private boolean loggingEnabled;

        private boolean enableTorch;
        @Nullable
        private Integer lensFacing;

        @Nullable
        private DecoderType decoderType;

        @Nullable
        private ResultPointListener resultPointCallback;

        @SuppressWarnings("unused")
        @NonNull
        public Builder setLoggingEnabled(final boolean loggingEnabled) {
            this.loggingEnabled = loggingEnabled;
            return this;
        }

        @NonNull
        public Builder setTorch(final boolean enable) {
            enableTorch = enable;
            return this;
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
        public Builder setHints(@Nullable final Bundle hints) {
            this.hints.putAll(parseHints(hints));
            return this;
        }

        @NonNull
        public Builder  setResultPointCallback(@Nullable final ResultPointListener resultPointCallback) {
            this.resultPointCallback = resultPointCallback;
            return this;
        }

        @NonNull
        private Map<DecodeHintType, Object> parseHints(@Nullable final Bundle args) {
            final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);

            if (args == null || args.isEmpty()) {
                return hints;
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
                                hints.put(hintType, Boolean.TRUE);

                            } else {
                                final Object hintData = args.get(hintName);
                                if (hintType.getValueType().isInstance(hintData)) {
                                    hints.put(hintType, hintData);
                                } else {
                                    if (loggingEnabled) {
                                        Log.w(TAG, "Ignoring hint " + hintType
                                                + " because it is not assignable from " + hintData);
                                    }
                                }
                            }
                        }
                    });

            if (loggingEnabled) {
                Log.i(TAG, "Hints from the Intent: " + hints);
            }
            return hints;
        }

        @NonNull
        public Builder setCameraLensFacing(final int lensFacing) {
            if (lensFacing == CameraSelector.LENS_FACING_BACK
                    || lensFacing == CameraSelector.LENS_FACING_FRONT) {
                this.lensFacing = lensFacing;
            } else {
                this.lensFacing = null;
            }
            return this;
        }

        public BarcodeScanner build(@NonNull final Context context,
                                    @NonNull final LifecycleOwner lifecycleOwner,
                                    @NonNull final Preview.SurfaceProvider surfaceProvider) {
            return new BarcodeScanner(context, lifecycleOwner, surfaceProvider, this);
        }
    }
}

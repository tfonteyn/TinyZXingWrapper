package com.hardbacknutter.tinyzxingwrapper.scanner;

import android.content.Context;
import android.os.Bundle;

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
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;

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
    private final Executor mainExecutor;
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
                           @NonNull final Builder builder) {
        mainExecutor = ContextCompat.getMainExecutor(context);
        cameraExecutor = Executors.newSingleThreadExecutor();

        cameraProviderFuture = ProcessCameraProvider.getInstance(context);

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
     * Only takes effect if called before
     * {@link #startScan(LifecycleOwner, PreviewView, DecoderResultListener)}.
     * <p>
     * One of:
     * <ul>
     *     <li>{@link CameraSelector#LENS_FACING_FRONT}</li>
     *     <li>{@link CameraSelector#LENS_FACING_BACK}</li>
     *     <li>{@code null} : let the device decide (this is the default)</li>
     * </ul>
     *
     * @param lensFacing preferred
     */
    public void setCameraLensFacing(@Nullable final Integer lensFacing) {
        if (lensFacing == null) {
            this.lensFacing = null;
        } else if (lensFacing == CameraSelector.LENS_FACING_BACK
                   || lensFacing == CameraSelector.LENS_FACING_FRONT) {
            this.lensFacing = lensFacing;
        } else {
            this.lensFacing = null;
        }
    }

    public void startScan(@NonNull final LifecycleOwner lifecycleOwner,
                          @NonNull final PreviewView previewView,
                          @NonNull final DecoderResultListener resultListener) {
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
                        preview.setSurfaceProvider(previewView.getSurfaceProvider());


                        final ImageCapture imageCapture = new ImageCapture.Builder().build();

                        final ImageAnalysis.Analyzer analyzer = image -> {
                            // The image provided has format ImageFormat.YUV_420_888.
                            try (image) {
                                // so we only take the Y data from plane 0
                                final ByteBuffer yByteBuffer = image.getPlanes()[0].getBuffer();
                                yByteBuffer.rewind();
                                final byte[] yData = new byte[yByteBuffer.remaining()];
                                yByteBuffer.get(yData);

                                final LuminanceSource luminanceSource =
                                        new SimpleYLuminanceSource(yData,
                                                                   image.getWidth(),
                                                                   image.getHeight())
                                                .flipHorizontal(isImageFlipped)
                                                .rotate(image.getImageInfo().getRotationDegrees());

                                final Result result = decoder.decode(luminanceSource);
                                if (result != null) {
                                    mainExecutor.execute(() -> {
                                        resultListener.onResult(result);
                                        if (scanMode == ScanMode.Single) {
                                            stopScanning();
                                        }
                                    });
                                    if (scanMode == ScanMode.Single) {
                                        // all done
                                        return;
                                    }
                                }

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

                            } catch (@NonNull final Throwable e) {
                                // catching Throwable, as we see StackOverflowError
                                // on some devices.
                                mainExecutor.execute(() -> {
                                    resultListener.onError(e);
                                    stopScanning();
                                });
                            }
                        };

                        final ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder().build();
                        imageAnalyzer.setAnalyzer(cameraExecutor, analyzer);

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
                        mainExecutor.execute(() -> resultListener.onError(e));
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
                              }
                          }
                      }
                  });

            return result;
        }

        @NonNull
        public BarcodeScanner build(@NonNull final Context context) {
            return new BarcodeScanner(context, this);
        }
    }
}

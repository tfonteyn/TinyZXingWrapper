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
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.hardbacknutter.tinyzxingwrapper.ScanContract;
import com.hardbacknutter.tinyzxingwrapper.ScanOptions;

/**
 * The main scanner code.
 * <ul>
 *     <li>Use {@link ScanContract} with the default capture activity.</li>
 *     <li>Use {@link ScanContract} with your own custom capture activity.</li>
 *     <li>Use directly by constructing an instance with the {@link Builder}</li>
 * </ul>
 */
@SuppressWarnings({"ClassWithOnlyPrivateConstructors", "WeakerAccess"})
public class BarcodeScanner
        implements LifecycleEventObserver {

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
    private final Integer lensFacing;
    @Nullable
    private final ResultPointCallback resultPointCallback;

    private boolean enableTorch;
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

        scanMode = Objects.requireNonNullElse(builder.scanMode, ScanMode.Single);

        decoderFactory = Objects.requireNonNullElseGet(
                builder.decoderFactory,
                () -> new DefaultDecoderFactory(builder.hints));

        this.lensFacing = builder.lensFacing;
        this.resultPointCallback = builder.resultPointCallback;
    }

    /**
     * Switch the torch (flashlight) on or off. Takes effect immediately.
     * <p>
     * * By default disabled.
     *
     * @param enable {@code true} to enable
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
     * Start the scanner.
     *
     * @param lifecycleOwner the caller
     * @param previewView    where to show the preview
     * @param resultListener to receive the result
     */
    public void start(@NonNull final LifecycleOwner lifecycleOwner,
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

                        final Decoder decoder = decoderFactory.createDecoder(resultPointCallback);

                        final Preview preview = new Preview.Builder().build();
                        preview.setSurfaceProvider(previewView.getSurfaceProvider());

                        final ImageCapture imageCapture = new ImageCapture.Builder().build();

                        final ImageAnalysis.Analyzer analyzer =
                                new MyAnalyzer(decoder, isImageFlipped, resultListener);

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

    /**
     * Stop the scanner.
     */
    public void stop() {
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
     * The builder prepares all/any arguments related to the barcode decoding.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static class Builder {
        private final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);

        @Nullable
        private DecoderFactory decoderFactory;
        @Nullable
        private ScanMode scanMode;
        @Nullable
        private Integer lensFacing;
        @Nullable
        private ResultPointCallback resultPointCallback;

        /**
         * Set the {@link ScanMode}.
         *
         * @param mode to use
         */
        public void setScanMode(@NonNull final ScanMode mode) {
            this.scanMode = mode;
        }

        /**
         * Set a custom {@link DecoderFactory}.
         * <p>
         * If not set, a default factory will be created using the provided hints.
         *
         * @param decoderFactory to use
         *
         * @return this
         */
        @NonNull
        public Builder setDecoderFactory(@NonNull final DecoderFactory decoderFactory) {
            this.decoderFactory = decoderFactory;
            return this;
        }

        /**
         * Set the desired barcode formats to scan.
         * <p>
         * Only used if {@link #setDecoderFactory(DecoderFactory)} is <strong>NOT</strong> called.
         *
         * @param barcodeFormats the {@link BarcodeFormat}s to scan for
         *
         * @return this
         */
        @NonNull
        public Builder setBarcodeFormats(@NonNull final List<BarcodeFormat> barcodeFormats) {
            // Used directly, so we just store the enums.
            // For intent use, see #addHints
            hints.put(DecodeHintType.POSSIBLE_FORMATS, new ArrayList<>(barcodeFormats));
            return this;
        }

        /**
         * Set a hint making the decoder try both normal (black on white)
         * and inverse scanning (white on black).
         * <p>
         * Only used if {@link #setDecoderFactory(DecoderFactory)} is <strong>NOT</strong> called.
         *
         * @param enabled flag
         *
         * @return this
         */
        @NonNull
        public Builder setAlsoTryInverted(final boolean enabled) {
            hints.put(DecodeHintType.ALSO_INVERTED, enabled);
            return this;
        }

        /**
         * Set a hint making the decoder try a number of extra ways to get a result.
         * <p>
         * Only used if {@link #setDecoderFactory(DecoderFactory)} is <strong>NOT</strong> called.
         *
         * @param enabled flag
         *
         * @return this
         */
        @NonNull
        public Builder setTryHarder(final boolean enabled) {
            hints.put(DecodeHintType.TRY_HARDER, enabled);
            return this;
        }

        /**
         * Add a generic hint.
         * If the data type does not match the hint type, the hint is quietly ignored.
         * <p>
         * {@link DecodeHintType#NEED_RESULT_POINT_CALLBACK} is NOT supported
         * as it's used internally.
         * <p>
         * Only used if {@link #setDecoderFactory(DecoderFactory)} is <strong>NOT</strong> called.
         *
         * @param hintType to add
         * @param hintData to add
         *
         * @return this
         */
        @NonNull
        public Builder addHint(@NonNull final DecodeHintType hintType,
                               @NonNull final Object hintData) {
            if (hintType.getValueType().equals(Void.class)) {
                if (hintData instanceof Boolean && (Boolean) hintData) {
                    this.hints.put(hintType, Boolean.TRUE);
                } else {
                    this.hints.remove(hintType);
                }
            } else if (hintType.getValueType().isInstance(hintData)) {
                this.hints.put(hintType, hintData);
            }
            return this;
        }

        /**
         * Add a collection of hints.
         * If the data type does not match the hint type, the hint is quietly ignored.
         * <p>
         * <strong>IMPORTANT:</strong>
         * {@link DecodeHintType#POSSIBLE_FORMATS} is expected to have as value
         * an {@link ArrayList} with the {@code String} values of the {@link BarcodeFormat}
         * enum names !
         * <p>
         * Note that {@link DecodeHintType#NEED_RESULT_POINT_CALLBACK} is NOT supported
         * as it's used internally.
         * <p>
         * Only used if {@link #setDecoderFactory(DecoderFactory)} is <strong>NOT</strong> called.
         *
         * @param args a Bundle with hints; may contain other options which will be ignored.
         *
         * @return this
         *
         * @see ScanOptions#setBarcodeFormats(List)
         */
        @NonNull
        public Builder addHints(@Nullable final Bundle args) {
            if (args != null && !args.isEmpty()) {
                Arrays.stream(DecodeHintType.values())
                      // This one is configured/used internally
                      .filter(hintType -> hintType != DecodeHintType.NEED_RESULT_POINT_CALLBACK)
                      .forEach(hintType -> {
                          final String hintName = hintType.name();
                          if (args.containsKey(hintName)) {
                              // A switch 'hint': if present, store it with a boolean 'True'
                              // (this is faster than 6 string equality test)
                              // PURE_BARCODE
                              // TRY_HARDER
                              // ASSUME_CODE_39_CHECK_DIGIT
                              // ASSUME_GS1
                              // RETURN_CODABAR_START_END
                              // ALSO_INVERTED
                              if (hintType.getValueType().equals(Void.class)) {
                                  this.hints.put(hintType, Boolean.TRUE);

                              } else if ("POSSIBLE_FORMATS".equals(hintName)) {
                                  // the value is ArrayList of strings with the enum names.
                                  // Convert them back to the actual enums.
                                  final ArrayList<String> list = args.getStringArrayList(
                                          hintName);
                                  if (list != null) {
                                      final List<BarcodeFormat> formats =
                                              list.stream()
                                                  .map(BarcodeFormat::valueOf)
                                                  .collect(Collectors.toList());
                                      this.hints.put(hintType, formats);
                                  }
                              } else {
                                  // OTHER
                                  // CHARACTER_SET
                                  // ALLOWED_LENGTHS
                                  // ALLOWED_EAN_EXTENSIONS
                                  final Object hintData = args.get(hintName);
                                  if (hintType.getValueType().isInstance(hintData)) {
                                      this.hints.put(hintType, hintData);
                                  }
                              }
                          }
                      });
            }
            return this;
        }

        /**
         * Set the preferred camera (lens-facing) to use.
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
        @NonNull
        public Builder setCameraLensFacing(@Nullable final Integer lensFacing) {
            if (lensFacing == null) {
                this.lensFacing = null;
            } else if (lensFacing == CameraSelector.LENS_FACING_BACK
                       || lensFacing == CameraSelector.LENS_FACING_FRONT) {
                this.lensFacing = lensFacing;
            } else {
                this.lensFacing = null;
            }
            return this;
        }

        /**
         * Set a listener to be informed of possible {@link ResultPoint}s found.
         *
         * @param listener a listener; can be {@code null} for none.
         *
         * @return this
         */
        @NonNull
        public Builder setResultPointCallback(@Nullable final ResultPointCallback listener) {
            this.resultPointCallback = listener;
            return this;
        }

        /**
         * Create the scanner.
         *
         * @param context Current context
         *
         * @return an initialized scanner
         */
        @NonNull
        public BarcodeScanner build(@NonNull final Context context) {
            return new BarcodeScanner(context, this);
        }
    }

    private class MyAnalyzer
            implements ImageAnalysis.Analyzer {

        @NonNull
        private final Decoder decoder;
        @NonNull
        private final DecoderResultListener resultListener;

        private final boolean isImageFlipped;

        /** Prevent duplicate scans in {@link ScanMode#Continuous}. */
        @Nullable
        private String lastBarcodeText;

        private MyAnalyzer(@NonNull final Decoder decoder,
                           final boolean isImageFlipped,
                           @NonNull final DecoderResultListener resultListener) {
            this.decoder = decoder;
            this.resultListener = resultListener;
            this.isImageFlipped = isImageFlipped;
        }

        @Override
        public void analyze(@NonNull final ImageProxy image) {
            // Must close the image after processing.
            try (image) {
                final LuminanceSource luminanceSource = process(image);
                final Result result = decoder.decode(luminanceSource);
                if (result != null) {
                    forwardResult(result);
                }
            } catch (@NonNull final Throwable e) {
                // catching Throwable, as we see StackOverflowError
                // on some devices.
                mainExecutor.execute(() -> {
                    resultListener.onError(e);
                    BarcodeScanner.this.stop();
                });
            }
        }

        @NonNull
        private SimpleLuminanceSource process(@NonNull final ImageProxy image) {
            // The image provided has format ImageFormat.YUV_420_888.
            // so we only take the Y data from plane 0
            final ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];

            final ByteBuffer yByteBuffer = yPlane.getBuffer();
            yByteBuffer.rewind();
            final byte[] yData = new byte[yByteBuffer.remaining()];
            yByteBuffer.get(yData);

            return new SimpleLuminanceSource(yData,
                                             image.getWidth(),
                                             image.getHeight(),
                                             yPlane.getRowStride(),
                                             yPlane.getPixelStride())
                    .flipHorizontal(isImageFlipped)
                    .rotate(image.getImageInfo().getRotationDegrees());
        }

        private void forwardResult(@NonNull final Result result) {
            mainExecutor.execute(() -> {
                if (scanMode == ScanMode.Single) {
                    resultListener.onResult(result);
                    BarcodeScanner.this.stop();
                } else {
                    // don't check on null/blank
                    if (!Objects.equals(lastBarcodeText, result.getText())) {
                        lastBarcodeText = result.getText();
                        resultListener.onResult(result);
                    }
                }
            });
        }
    }
}

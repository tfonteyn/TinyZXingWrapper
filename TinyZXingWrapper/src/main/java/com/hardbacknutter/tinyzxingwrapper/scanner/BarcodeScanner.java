package com.hardbacknutter.tinyzxingwrapper.scanner;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;

import androidx.annotation.FloatRange;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.math.MathUtils;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
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
import java.util.concurrent.TimeUnit;
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
    private final boolean autoFocus;
    private float linearZoom;
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

        this.autoFocus = builder.autoFocus;
        this.lensFacing = builder.lensFacing;
        this.resultPointCallback = builder.resultPointCallback;
    }

    /**
     * Switch the torch (flashlight) on or off.
     * Will be ignored if the device has no torch.
     * <p>
     * Takes effect immediately.
     * <p>
     * By default disabled.
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
     * Set the linear zoom for the camera.
     * Will be ignored if the camera has no zoom-function.
     * <p>
     * Takes effect immediately.
     *
     * @param zoom value to set; {@code 0} no zoom, {@code 1} maximum zoom.
     */
    public void setLinearZoom(@FloatRange(from = 0.0, to = 1.0) final float zoom) {
        this.linearZoom = MathUtils.clamp(zoom, 0f, 1f);
        synchronized (lock) {
            if (cameraControl != null) {
                cameraControl.setLinearZoom(linearZoom);
            }
        }
    }

    public boolean hasTorch(@NonNull final Context context) {
        return context.getPackageManager()
                      .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    public boolean hasZoom(@NonNull final Context context) {
        // we'll presume the default of the device is always the back camera.
        final Integer ourLens = Objects.requireNonNullElse(
                lensFacing, CameraCharacteristics.LENS_FACING_BACK);
        final CameraManager cameraManager = (CameraManager)
                context.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : cameraManager.getCameraIdList()) {
                final CameraCharacteristics characteristics =
                        cameraManager.getCameraCharacteristics(cameraId);
                if (ourLens.equals(characteristics.get(CameraCharacteristics.LENS_FACING))) {
                    final Float maxZoom = characteristics.get(
                            CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                    return maxZoom != null && maxZoom > 1.0f;
                }
            }
        } catch (@NonNull final CameraAccessException ignore) {
            // ignore
        }
        return false;
    }

    private boolean isImageFlipped() {
        return lensFacing != null && lensFacing == CameraSelector.LENS_FACING_FRONT;
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
                        final CameraSelector.Builder csb = new CameraSelector.Builder();
                        if (lensFacing != null) {
                            csb.requireLensFacing(lensFacing);
                        }
                        final CameraSelector cameraSelector = csb.build();

                        final Decoder decoder = decoderFactory.createDecoder(resultPointCallback);

                        final Preview preview = new Preview.Builder().build();
                        preview.setSurfaceProvider(previewView.getSurfaceProvider());

                        final ImageAnalysis.Analyzer analyzer =
                                new BarcodeAnalyzer(previewView.getHeight(), previewView.getWidth(),
                                                    decoder, isImageFlipped(), resultListener);

                        final ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                                .setOutputImageRotationEnabled(true)
                                .build();
                        imageAnalyzer.setAnalyzer(cameraExecutor, analyzer);

                        synchronized (lock) {
                            cameraProvider = cameraProviderFuture.get();
                            cameraProvider.unbindAll();

                            final Camera camera = cameraProvider
                                    .bindToLifecycle(lifecycleOwner, cameraSelector,
                                                     // use-cases:
                                                     preview,
                                                     imageAnalyzer);

                            cameraControl = camera.getCameraControl();

                            // initial settings
                            cameraControl.enableTorch(enableTorch);
                            cameraControl.setLinearZoom(linearZoom);

                            if (autoFocus) {
                                configureAutoFocus(previewView);
                            }
                        }
                    } catch (@NonNull final ExecutionException | InterruptedException e) {
                        mainExecutor.execute(() -> resultListener.onError(e));
                    }
                },
                mainExecutor);
    }

    private void configureAutoFocus(@NonNull final PreviewView previewView) {

        final float previewViewWidth = previewView.getWidth();
        final float previewViewHeight = previewView.getHeight();

        final MeteringPoint autoFocusPoint =
                new SurfaceOrientedMeteringPointFactory(previewViewWidth, previewViewHeight)
                        .createPoint(previewViewWidth / 2.0f, previewViewHeight / 2.0f);

        //noinspection DataFlowIssue
        cameraControl.startFocusAndMetering(
                new FocusMeteringAction.Builder(autoFocusPoint, FocusMeteringAction.FLAG_AF)
                        .setAutoCancelDuration(2, TimeUnit.SECONDS)
                        .build());
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
        private boolean autoFocus;
        @Nullable
        private ResultPointCallback resultPointCallback;

        /**
         * Set the {@link ScanMode}.
         *
         * @param mode to use
         *
         * @return {@code this}
         */
        @NonNull
        public Builder setScanMode(@NonNull final ScanMode mode) {
            this.scanMode = mode;
            return this;
        }

        /**
         * Set a custom {@link DecoderFactory}.
         * <p>
         * If not set, a default factory will be created using the provided hints.
         *
         * @param decoderFactory to use
         *
         * @return {@code this}
         */
        @NonNull
        public Builder setDecoderFactory(@NonNull final DecoderFactory decoderFactory) {
            this.decoderFactory = decoderFactory;
            return this;
        }

        /**
         * Set the desired barcode formats to scan.
         * <p>
         * Ignored if {@link #setDecoderFactory(DecoderFactory)} is used.
         *
         * @param barcodeFormats the {@link BarcodeFormat}s to scan for
         *
         * @return {@code this}
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
         * Ignored if {@link #setDecoderFactory(DecoderFactory)} is used.
         *
         * @param enabled flag
         *
         * @return {@code this}
         */
        @NonNull
        public Builder setAlsoTryInverted(final boolean enabled) {
            hints.put(DecodeHintType.ALSO_INVERTED, enabled);
            return this;
        }

        /**
         * Set a hint making the decoder try a number of extra ways to get a result.
         * <p>
         * Ignored if {@link #setDecoderFactory(DecoderFactory)} is used.
         *
         * @param enabled flag
         *
         * @return {@code this}
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
         * Ignored if {@link #setDecoderFactory(DecoderFactory)} is used.
         *
         * @param hintType to add
         * @param hintData to add
         *
         * @return {@code this}
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
         * Ignored if {@link #setDecoderFactory(DecoderFactory)} is used.
         *
         * @param args a Bundle with hints; may contain other options which will be ignored.
         *
         * @return {@code this}
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
                              // A switch 'hint': if present, store it with a boolean 'True'.
                              // ZXing checks "contains", not the actual value!
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
                                  // Convert the String values back to the actual enums.
                                  final ArrayList<String> values = args.getStringArrayList(
                                          hintName);
                                  if (values != null) {
                                      final List<BarcodeFormat> formats =
                                              values.stream()
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
         * Enable/disable auto-focus.
         *
         * @param enable {@code true} to enable
         *
         * @return {@code this}
         */
        @NonNull
        public Builder setAutoFocus(final boolean enable) {
            this.autoFocus = enable;
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
         *
         * @return {@code this}
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
         * @return {@code this}
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

    private class BarcodeAnalyzer
            implements ImageAnalysis.Analyzer {

        private static final String TAG = "BarcodeAnalyzer";

        private final int previewHeightPx;
        private final int previewWidthPx;
        @NonNull
        private final Decoder decoder;
        @NonNull
        private final DecoderResultListener resultListener;

        private final boolean isImageFlipped;

        /** Prevent duplicate scans in {@link ScanMode#Continuous}. */
        @Nullable
        private String lastBarcodeText;

        private BarcodeAnalyzer(final int previewHeightPx,
                                final int previewWidthPx,
                                @NonNull final Decoder decoder,
                                final boolean isImageFlipped,
                                @NonNull final DecoderResultListener resultListener) {
            this.previewHeightPx = previewHeightPx;
            this.previewWidthPx = previewWidthPx;
            this.decoder = decoder;
            this.isImageFlipped = isImageFlipped;
            this.resultListener = resultListener;
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
        private LuminanceSource process(@NonNull final ImageProxy image) {
            // The image provided has format ImageFormat.YUV_420_888.
            // Take the Y data from plane 0
            final ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
            final byte[] yData = getBytes(yPlane);

            // the image buffer as received from the camera.
            final int imageWidth = image.getWidth();
            final int imageHeight = image.getHeight();
            final int h;
            final int w;
            // swap width and height depending on device orientation
            if (imageWidth > imageHeight) {
                // Landscape image buffer
                h = previewWidthPx;
                w = previewHeightPx;
            } else {
                // Portrait image buffer
                w = previewWidthPx;
                h = previewHeightPx;
            }

            // Crop the buffer to the size of the preview.
            final float scaleX = (float) imageWidth / w;
            final float scaleY = (float) imageHeight / h;
            // Use the smaller scale factor to match centerCrop behavior
            final float scale = Math.min(scaleX, scaleY);
            // Now, calculate the crop size in the image buffer
            int cropWidth = (int) (w * scale);
            int cropHeight = (int) (h * scale);

            // Center the crop rectangle, ensuring it is fully inside the image buffer
            final int left = Math.max(0, (imageWidth - cropWidth) / 2);
            final int top = Math.max(0, (imageHeight - cropHeight) / 2);
            cropWidth = Math.min(cropWidth, imageWidth - left);
            cropHeight = Math.min(cropHeight, imageHeight - top);

            // Example values as measured in a test holding the phone in portrait:
            // imageWidth=480,     imageHeight=640,      yPlane.getRowStride()=512
            // previewWidthPx=900, previewHeightPx=574
            // scaleX=0.53333336, scaleY=1.1149826, scale=0.53333336
            // cropped: left=0, top=167, cropWidth=480, cropHeight=306

            return new PlanarYUVLuminanceSource(
                    yData,
                    yPlane.getRowStride(), imageHeight,
                    left, top, cropWidth, cropHeight,
                    isImageFlipped);
        }

        /**
         * Convert the given plane to a byte buffer.
         *
         * @param yPlane to convert
         *
         * @return byte[]
         */
        @NonNull
        private byte[] getBytes(@NonNull final ImageProxy.PlaneProxy yPlane) {
            final ByteBuffer yByteBuffer = yPlane.getBuffer();
            yByteBuffer.rewind();
            final byte[] yData = new byte[yByteBuffer.remaining()];
            yByteBuffer.get(yData);
            return yData;
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

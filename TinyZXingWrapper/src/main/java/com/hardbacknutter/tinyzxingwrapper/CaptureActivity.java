package com.hardbacknutter.tinyzxingwrapper;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.zxing.Result;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeScanner;
import com.hardbacknutter.tinyzxingwrapper.scanner.DecoderResultListener;
import com.hardbacknutter.tinyzxingwrapper.scanner.TzwViewfinderView;

/**
 * A simple (default) capture Activity.
 *
 * @see ScanContract
 * @see ScanOptions
 */
public class CaptureActivity
        extends AppCompatActivity {

    private static final String PK_TORCH =
            "com.hardbacknutter.tinyzxingwrapper.CaptureActivity.TORCH";
    private static final String PK_ZOOM =
            "com.hardbacknutter.tinyzxingwrapper.CaptureActivity.ZOOM";

    private static final long TIMEOUT_NOT_SET = -1;
    private long inactivityTimeOutInMs = TIMEOUT_NOT_SET;
    private long hardTimeOutInMs = TIMEOUT_NOT_SET;

    @SuppressWarnings("FieldCanBeLocal")
    @Nullable
    private InactivityTimer inactivityTimer;

    private PreviewView previewView;

    @Nullable
    private MaterialButton torchButton;

    /** Allows changing while scanning. */
    private boolean torchEnabled;
    /** Allows changing while scanning. */
    private float zoomValue = 0.3f;

    private BarcodeScanner scanner;

    @Nullable
    private List<String> metaDataToReturn;

    private final DecoderResultListener decoderResultListener = new DecoderResultListener() {
        @Override
        public void onResult(@NonNull final Result result) {
            final String text = result.getText();
            if (text != null && !text.isBlank()) {
                final Intent intent = ScanIntentResult.createActivityResultIntent(
                        CaptureActivity.this, result, metaDataToReturn);
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        }

        @Override
        public void onError(@NonNull final Throwable e) {
            final Intent intent = new Intent()
                    .putExtra(ScanIntentResult.Failure.FAILURE_EXCEPTION, e);
            setResult(Activity.RESULT_CANCELED, intent);
            finish();
        }
    };

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(), isGranted -> {
                        if (isGranted) {
                            startScanner();
                        } else {
                            final Intent intent = new Intent().putExtra(
                                    ScanIntentResult.Failure.FAILURE_REASON,
                                    ScanIntentResult.Failure.REASON_MISSING_CAMERA_PERMISSION);
                            setResult(Activity.RESULT_CANCELED, intent);
                            finish();
                        }
                    });

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        // EdgeToEdge on Android pre-15; but only starting Android 11 up
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            EdgeToEdge.enable(this);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tzw_activity_scan);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            initInsets();
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        previewView = Objects.requireNonNull(findViewById(R.id.tzw_preview),
                                             "Missing R.id.tzw_preview");

        @Nullable
        final Bundle args = getIntent().getExtras();

        readSettings(args);
        initScanner(args);
        initZoomSlider(args);
        initTorchButton();
        initStatusText(args);
        initTimeoutHandlers(args);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startScanner();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void initInsets() {
        getWindow().setNavigationBarContrastEnforced(false);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        final ConstraintLayout rootLayout = findViewById(R.id.capture_root);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (view, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());

            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);

            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void initScanner(@Nullable final Bundle args) {
        // Note that the ScanMode is kept as default (Single)
        // and that we always use the default DecoderFactory
        final BarcodeScanner.Builder builder = new BarcodeScanner.Builder();

        if (args != null) {
            metaDataToReturn = args.getStringArrayList(ScanOptions.Option.RETURN_META_DATA);

            // only set if present, otherwise let the device decide.
            if (args.containsKey(ScanOptions.Option.CAMERA_LENS_FACING)) {
                builder.setCameraLensFacing(args.getInt(ScanOptions.Option.CAMERA_LENS_FACING,
                                                        CameraSelector.LENS_FACING_BACK));
            }

            builder.setAutoFocus(args.getBoolean(ScanOptions.Option.AUTO_FOCUS, false));
            builder.addHints(args);
        }

        final TzwViewfinderView viewFinderView = findViewById(R.id.tzw_viewfinder_view);
        if (viewFinderView != null && viewFinderView.isShowResultPoints()) {
            builder.setResultPointCallback(viewFinderView);
        }

        scanner = builder.build(this);

        scanner.setTorch(torchEnabled);
        scanner.setLinearZoom(zoomValue);

        getLifecycle().addObserver(scanner);
    }

    private void initZoomSlider(@Nullable final Bundle args) {
        final Slider sliderView = findViewById(R.id.tzw_zoom_slider);
        if (sliderView != null) {
            final boolean wantZoom = args == null || args.getBoolean(Option.SHOW_ZOOM, true);
            final boolean hasZoom = scanner.hasZoom(this);

            final boolean showZoom = wantZoom && hasZoom;
            sliderView.setVisibility(showZoom ? View.VISIBLE : View.GONE);
            if (showZoom) {
                sliderView.setValue(zoomValue);
                sliderView.addOnChangeListener((slider, value, fromUser) -> {
                    if (fromUser) {
                        this.zoomValue = value;
                        writeSettings();
                        //noinspection DataFlowIssue
                        scanner.setLinearZoom(this.zoomValue);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            slider.performHapticFeedback(
                                    HapticFeedbackConstants.SEGMENT_FREQUENT_TICK);
                        } else {
                            slider.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        }
                    }
                });
            }
        }
    }

    private void initTorchButton() {
        torchButton = findViewById(R.id.tzw_btn_torch);
        if (torchButton != null) {
            final boolean hasTorch = scanner.hasTorch(this);
            torchButton.setVisibility(hasTorch ? View.VISIBLE : View.GONE);
            if (hasTorch) {
                // set the initial state which depends on incoming args
                updateTorchIcon();
                torchButton.setOnClickListener(v -> {
                    // flip the state
                    torchEnabled = !torchEnabled;
                    writeSettings();
                    updateTorchIcon();
                    if (scanner != null) {
                        scanner.setTorch(torchEnabled);
                    }
                });
            }
        }
    }

    private void updateTorchIcon() {
        // We're not using checkable and StateLists as managing the background
        // color then makes things needlessly complicated.
        // Hence simply swap the icon manually here.
        //noinspection DataFlowIssue
        torchButton.setIconResource(torchEnabled
                                    ? R.drawable.tzw_ic_baseline_flashlight_off_24
                                    : R.drawable.tzw_ic_baseline_flashlight_on_24);
    }

    /**
     * Set the specified prompt, or if {@code null} sets the default text.
     *
     * @param args method will parse its own options
     */
    private void initStatusText(@Nullable final Bundle args) {
        final TextView statusTextView = findViewById(R.id.tzw_status_view);
        if (statusTextView != null) {
            String statusText = null;
            if (args != null) {
                statusText = args.getString(Option.PROMPT);
            }
            if (statusText == null) {
                statusTextView.setText(R.string.tzw_status_text);
            } else {
                statusTextView.setText(statusText);
            }
        }
    }

    /**
     * Setup the optional hard-timeout and inactivity (soft) timeout.
     */
    private void initTimeoutHandlers(@Nullable final Bundle args) {
        if (args != null) {
            inactivityTimeOutInMs = args.getLong(Option.INACTIVITY_TIMEOUT_MS, TIMEOUT_NOT_SET);
            hardTimeOutInMs = args.getLong(Option.TIMEOUT_MS, TIMEOUT_NOT_SET);
        }

        // unless explicitly disabled,
        if (inactivityTimeOutInMs != 0) {
            // enabled the timer using the default or the specified setting
            inactivityTimer = new InactivityTimer(this, () -> {
                setResult(Activity.RESULT_CANCELED,
                          new Intent().putExtra(ScanIntentResult.Failure.FAILURE_REASON,
                                                ScanIntentResult.Failure.REASON_INACTIVITY));
                finish();
            });

            if (inactivityTimeOutInMs > 0) {
                inactivityTimer.setInactivityDelayMs(inactivityTimeOutInMs);
            }
            getLifecycle().addObserver(inactivityTimer);
        }

        // only enabled if explicitly set
        if (hardTimeOutInMs > 0) {
            new Handler().postDelayed(() -> {
                setResult(Activity.RESULT_CANCELED,
                          new Intent().putExtra(ScanIntentResult.Failure.FAILURE_REASON,
                                                ScanIntentResult.Failure.REASON_TIMEOUT));
                finish();
            }, hardTimeOutInMs);
        }
    }

    // @RequiresPermission(Manifest.permission.CAMERA)
    private void startScanner() {
        //noinspection DataFlowIssue
        scanner.start(this, previewView, decoderResultListener);
    }

    private void readSettings(@Nullable final Bundle args) {
        final SharedPreferences p = getPreferences(Context.MODE_PRIVATE);
        torchEnabled = p.getBoolean(PK_TORCH, false);
        zoomValue = p.getFloat(PK_ZOOM, zoomValue);
    }

    /**
     * Store the torch and zoom-value.
     * Called each time one of them is changed by the user.
     */
    private void writeSettings() {
        getPreferences(Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PK_TORCH, torchEnabled)
                .putFloat(PK_ZOOM, zoomValue)
                .apply();
    }

    /**
     * Arguments implemented by the default {@link CaptureActivity}.
     */
    @SuppressWarnings("WeakerAccess")
    public static final class Option {

        /**
         * Should the zoom-control slider be shown.
         * <p>
         * Default: show the slider
         * <p>
         * Type: boolean, use {@code false} to hide.
         */
        public static final String SHOW_ZOOM = "SHOW_ZOOM";

        /**
         * Prompt to show while scanning. Set to {@code ""} for none.
         * <p>
         * Default: use the predefined message.
         * <p>
         * Type: String
         *
         * @see ScanOptions#setPrompt(String)
         */
        public static final String PROMPT = "PROMPT";

        /**
         * Set a (hard) timeout in milliseconds to finish the scan screen.
         * If no scan is done within this timeout, the attempt will be cancelled.
         *
         * <p>
         * Default: not set.
         * <p>
         * Type: long (milliseconds)
         *
         * @see ScanOptions#setTimeout(long)
         */
        public static final String TIMEOUT_MS = "TIMEOUT_MS";

        /**
         * Set a (soft) timeout in milliseconds to cancel the scan.
         * Lets the device decide if the user has been inactive for longer than this timeout.
         * Set to {@code 0} to explicitly disable.
         * <p>
         * Default: see {@link InactivityTimer}, currently defined at 3 minutes.
         * <p>
         * Type: long (milliseconds)
         *
         * @see ScanOptions#setInactivityTimeout(long)
         */
        public static final String INACTIVITY_TIMEOUT_MS = "INACTIVITY_TIMEOUT_MS";

        private Option() {
        }
    }
}
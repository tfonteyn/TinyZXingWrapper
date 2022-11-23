package com.hardbacknutter.tinyzxingwrapper;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.zxing.Result;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeScanner;
import com.hardbacknutter.tinyzxingwrapper.scanner.DecoderResultListener;
import com.hardbacknutter.tinyzxingwrapper.scanner.TzwViewfinderView;

public class CaptureActivity
        extends AppCompatActivity {

    private static final long TIMEOUT_NOT_SET = -1;
    private PreviewView previewView;
    @SuppressWarnings("FieldCanBeLocal")
    @Nullable
    private TzwViewfinderView viewFinderView;
    @SuppressWarnings("FieldCanBeLocal")
    @Nullable
    private TextView statusTextView;
    @Nullable
    private MaterialButton torchButton;
    @SuppressWarnings("FieldCanBeLocal")
    @Nullable
    private InactivityTimer inactivityTimer;
    private boolean torchEnabled;
    @Nullable
    private Integer lensFacing;
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
    @Nullable
    private BarcodeScanner scanner;
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
    private long inactivityTimeOutInMs = TIMEOUT_NOT_SET;
    private long hardTimeOutInMs = TIMEOUT_NOT_SET;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.tzw_activity_scan);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final PreviewView view = findViewById(R.id.tzw_preview);
        previewView = Objects.requireNonNull(view, "Missing R.id.tzw_preview");


        // Note that the ScanMode is kept as default (Single)
        // and that we always use the default DecoderFactory
        final BarcodeScanner.Builder builder = new BarcodeScanner.Builder();

        Bundle args = getIntent().getExtras();
        if (args != null) {
            metaDataToReturn = args.getStringArrayList(ScanOptions.Option.RETURN_META_DATA);

            builder.addHints(args);
        }
        scanner = builder.build(this);


        args = savedInstanceState != null ? savedInstanceState : args;
        if (args != null) {
            torchEnabled = args.getBoolean(ScanOptions.Option.TORCH_ENABLED, false);

            // only set if present, otherwise let the device decide.
            if (args.containsKey(ScanOptions.Option.CAMERA_LENS_FACING)) {
                lensFacing = args.getInt(ScanOptions.Option.CAMERA_LENS_FACING,
                                         CameraSelector.LENS_FACING_BACK);
            }

            inactivityTimeOutInMs = args.getLong(Option.INACTIVITY_TIMEOUT_MS, TIMEOUT_NOT_SET);
            hardTimeOutInMs = args.getLong(Option.TIMEOUT_MS, TIMEOUT_NOT_SET);
        }

        scanner.setTorch(torchEnabled);
        scanner.setCameraLensFacing(lensFacing);

        viewFinderView = findViewById(R.id.tzw_viewfinder_view);
        if (viewFinderView != null && viewFinderView.isShowResultPoints()) {
            scanner.setResultPointListener(viewFinderView);
        }

        getLifecycle().addObserver(scanner);

        initTorchButton();
        initStatusText(args);
        initTimeoutHandlers();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startScanner();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startScanner() {
        //noinspection ConstantConditions
        scanner.start(this, previewView, decoderResultListener);
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(ScanOptions.Option.TORCH_ENABLED, torchEnabled);
        if (lensFacing != null) {
            outState.putInt(ScanOptions.Option.CAMERA_LENS_FACING, lensFacing);
        }
        if (inactivityTimeOutInMs > TIMEOUT_NOT_SET) {
            outState.putLong(Option.INACTIVITY_TIMEOUT_MS, inactivityTimeOutInMs);
        }
        if (hardTimeOutInMs > TIMEOUT_NOT_SET) {
            outState.putLong(Option.TIMEOUT_MS, hardTimeOutInMs);
        }
    }

    private void initTorchButton() {
        torchButton = findViewById(R.id.tzw_btn_torch);
        if (torchButton != null) {
            final boolean hasFlash = getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);

            torchButton.setVisibility(hasFlash ? View.VISIBLE : View.GONE);
            if (hasFlash) {
                // We're not using checkable and StateLists as managing the background
                // color then makes things needlessly complicated.
                // Hence simply swap the icon manually here.
                torchButton.setIconResource(torchEnabled
                                            ? R.drawable.tzw_ic_baseline_flashlight_off_24
                                            : R.drawable.tzw_ic_baseline_flashlight_on_24);
                torchButton.setOnClickListener(v -> {
                    torchEnabled = !torchEnabled;
                    torchButton.setIconResource(torchEnabled
                                                ? R.drawable.tzw_ic_baseline_flashlight_off_24
                                                : R.drawable.tzw_ic_baseline_flashlight_on_24);
                    if (scanner != null) {
                        scanner.setTorch(torchEnabled);
                    }
                });
            }
        }
    }

    /**
     * Set the specified prompt, or if {@code null} sets the default text.
     *
     * @param args method will parse its own options
     */
    private void initStatusText(@Nullable final Bundle args) {
        statusTextView = findViewById(R.id.tzw_status_view);
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
    private void initTimeoutHandlers() {
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

    /**
     * Arguments implemented by the default {@link CaptureActivity}.
     */
    @SuppressWarnings("WeakerAccess")
    public static final class Option {

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
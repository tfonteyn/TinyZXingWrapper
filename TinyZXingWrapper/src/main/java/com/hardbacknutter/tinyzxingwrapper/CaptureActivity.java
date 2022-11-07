package com.hardbacknutter.tinyzxingwrapper;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
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
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeFamily;
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeScanner;
import com.hardbacknutter.tinyzxingwrapper.scanner.DecoderResultListener;
import com.hardbacknutter.tinyzxingwrapper.scanner.DecoderType;
import com.hardbacknutter.tinyzxingwrapper.scanner.TzwViewfinderView;

import java.util.Objects;

public class CaptureActivity
        extends AppCompatActivity {

    private PreviewView previewView;
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
    private String metaDataToReturn;

    private final DecoderResultListener decoderResultListener = new DecoderResultListener() {
        @Override
        public void onResult(@NonNull final Result result) {
            final String text = result.getText();
            if (text != null && !text.isBlank()) {
                final Intent intent = ScanContract.createResultIntent(CaptureActivity.this,
                        result, metaDataToReturn);
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        }

        @Override
        public void onError(@NonNull final String info,
                            @NonNull final Exception e) {
            final Intent intent = new Intent()
                    .putExtra(ScanIntentResult.Failure.REASON, info + "|" + e.getMessage())
                    .putExtra(ScanIntentResult.Failure.EXCEPTION, e);
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
                            scanner.startScan(decoderResultListener);
                        } else {
                            final Intent intent = new Intent().putExtra(
                                    ScanIntentResult.Failure.REASON,
                                    ScanIntentResult.Failure.REASON_MISSING_CAMERA_PERMISSION);
                            setResult(Activity.RESULT_CANCELED, intent);
                            finish();
                        }
                    });


    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.tzw_activity_scan);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final PreviewView view = findViewById(R.id.tzw_preview);
        previewView = Objects.requireNonNull(view, "Missing R.id.tzw_preview");
        viewFinderView = findViewById(R.id.tzw_viewfinder_view);

        Bundle args = getIntent().getExtras();
        if (args != null) {
            metaDataToReturn = args.getString(ScanIntent.OptionKey.RETURN_META_DATA, null);
        }
        args = savedInstanceState != null ? savedInstanceState : args;
        if (args != null) {
            torchEnabled = args.getBoolean(ScanIntent.OptionKey.TORCH_ENABLED, false);
            // only set if present, otherwise let the device decide.
            if (args.containsKey(ScanIntent.OptionKey.CAMERA_LENS_FACING)) {
                lensFacing = args.getInt(ScanIntent.OptionKey.CAMERA_LENS_FACING,
                        CameraSelector.LENS_FACING_BACK);
            }
        }
        initScanner(args);

        initTorchButton();
        initStatusText(args);
        initTimeoutHandlers(args);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            //noinspection ConstantConditions
            scanner.startScan(decoderResultListener);
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(ScanIntent.OptionKey.TORCH_ENABLED, torchEnabled);
        if (lensFacing != null) {
            outState.putInt(ScanIntent.OptionKey.CAMERA_LENS_FACING, lensFacing);
        }
    }

    private void initScanner(@Nullable final Bundle args) {
        final BarcodeScanner.Builder builder = new BarcodeScanner.Builder()
                .setHints(args);
        if (args != null) {
            final String codeFamily = args.getString(ScanIntent.OptionKey.CODE_FAMILY);
            if (codeFamily != null) {
                builder.setCodeFamily(BarcodeFamily.valueOf(codeFamily));
            }

            if (args.containsKey(ScanIntent.OptionKey.DECODER_TYPE)) {
                final int scanType = args.getInt(ScanIntent.OptionKey.DECODER_TYPE,
                        DecoderType.Normal.type);
                builder.setDecoderType(scanType);
            }
        }
        scanner = builder.build(this, this, previewView.getSurfaceProvider());
        scanner.setTorch(torchEnabled);
        scanner.setCameraLensFacing(lensFacing);

        if (viewFinderView != null && viewFinderView.isShowResultPoints()) {
            scanner.setResultPointListener(viewFinderView);
        }

        getLifecycle().addObserver(scanner);
    }

    private void initTorchButton() {
        torchButton = findViewById(R.id.tzw_btn_torch);
        if (torchButton != null) {
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
                statusText = args.getString(ScanIntent.ToolOptionKey.PROMPT_MESSAGE);
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
     *
     * @param args method will parse its own options
     */
    private void initTimeoutHandlers(@Nullable final Bundle args) {

        long inactivityTimeOutInMs = 0;
        long hardTimeOutInMs = 0;

        if (args != null) {
            inactivityTimeOutInMs = args.getLong(ScanIntent.ToolOptionKey.INACTIVITY_TIMEOUT_MS,
                    0L);
            hardTimeOutInMs = args.getLong(ScanIntent.ToolOptionKey.TIMEOUT_MS,
                    0L);
        }

        // always enabled using the default or the specified setting
        inactivityTimer = new InactivityTimer(this, () -> {
            setResult(Activity.RESULT_CANCELED,
                    new Intent().putExtra(ScanIntentResult.Failure.REASON,
                            ScanIntentResult.Failure.REASON_INACTIVITY));
            finish();
        });

        if (inactivityTimeOutInMs > 0) {
            inactivityTimer.setInactivityDelayMs(inactivityTimeOutInMs);
        }
        getLifecycle().addObserver(inactivityTimer);


        // only enabled if explicitly set
        if (hardTimeOutInMs > 0) {
            new Handler().postDelayed(() -> {
                setResult(Activity.RESULT_CANCELED,
                        new Intent().putExtra(ScanIntentResult.Failure.REASON,
                                ScanIntentResult.Failure.REASON_TIMEOUT));
                finish();
            }, hardTimeOutInMs);
        }
    }

}
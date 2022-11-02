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
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.zxing.Result;

import java.util.Objects;

public class CaptureActivity
        extends AppCompatActivity {

    private PreviewView previewView;
    @Nullable
    private TzwViewfinderView viewFinderView;
    @SuppressWarnings("FieldCanBeLocal")
    @Nullable
    private TextView statusTextView;

    @SuppressWarnings("FieldCanBeLocal")
    @Nullable
    private InactivityTimer inactivityTimer;
    private CaptureViewModel vm;
    private final ScanResultListener scanResultListener = new ScanResultListener() {
        @Override
        public void onResult(@NonNull final Result result) {
            final String text = result.getText();
            if (text != null && !text.isBlank()) {
                final Intent intent = ScanContract.createResultIntent(CaptureActivity.this,
                        result, vm.getWithMetaData());
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        }

        @Override
        public void onError(@NonNull final String info,
                            @NonNull final Exception e) {
            final Intent intent = new Intent()
                    .putExtra(ScanResult.Failure.REASON, info + "|" + e.getMessage())
                    .putExtra(ScanResult.Failure.EXCEPTION, e);
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
                                    ScanResult.Failure.REASON,
                                    ScanResult.Failure.REASON_MISSING_CAMERA_PERMISSION);
                            setResult(Activity.RESULT_CANCELED, intent);
                            finish();
                        }
                    });

    /**
     * Override as needed.
     */
    protected void initContentView() {
        setContentView(R.layout.tzw_activity_scan);
    }

    /**
     * REQUIRED - Override as needed.
     */
    @NonNull
    protected PreviewView getPreviewView() {
        final PreviewView view = findViewById(R.id.tzw_preview);
        return Objects.requireNonNull(view, "Missing R.id.tzw_barcode_surface");
    }

    /**
     * Optional - Override as needed.
     */
    @Nullable
    protected TzwViewfinderView getViewFinderView() {
        return findViewById(R.id.tzw_viewfinder_view);
    }

    /** Optional - Override as needed. */
    @Nullable
    protected TextView getStatusTextView() {
        return findViewById(R.id.tzw_status_view);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initContentView();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        previewView = getPreviewView();
        viewFinderView = getViewFinderView();
        statusTextView = getStatusTextView();

        vm = new ViewModelProvider(this).get(CaptureViewModel.class);
        vm.init(this, getIntent().getExtras());

        if (statusTextView != null) {
            statusTextView.setText(vm.getStatusText());
        }

        initTimeoutHandlers();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startScanner();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /**
     * Setup the optional hard-timeout and inactivity (soft) timeout.
     */
    private void initTimeoutHandlers() {
        inactivityTimer = new InactivityTimer(this, () -> {
            setResult(Activity.RESULT_CANCELED,
                    new Intent().putExtra(ScanResult.Failure.REASON,
                            ScanResult.Failure.REASON_INACTIVITY));
            finish();
        });
        getLifecycle().addObserver(inactivityTimer);


        final long inactivityTimeOutInMs = vm.getInactivityTimeOutInMs();
        if (inactivityTimeOutInMs > 0) {
            inactivityTimer.setInactivityDelayMs(inactivityTimeOutInMs);
        }

        final long hardTimeOutInMs = vm.getTimeOutInMs();
        if (hardTimeOutInMs > 0) {
            new Handler().postDelayed(() -> {
                setResult(Activity.RESULT_CANCELED,
                        new Intent().putExtra(ScanResult.Failure.REASON,
                                ScanResult.Failure.REASON_TIMEOUT));
                finish();
            }, hardTimeOutInMs);
        }
    }

    private void startScanner() {
        final BarcodeScanner scanner = new BarcodeScanner(this, this,
                previewView.getSurfaceProvider());
        scanner.init(getIntent().getExtras());
        getLifecycle().addObserver(scanner);

        if (viewFinderView != null && viewFinderView.isEnableResultPoints()) {
            scanner.setResultPointCallback(viewFinderView);
        }
        scanner.startScan(scanResultListener);
    }
}
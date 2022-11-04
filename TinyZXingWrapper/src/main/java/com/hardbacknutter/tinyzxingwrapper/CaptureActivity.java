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
import androidx.lifecycle.ViewModelProvider;

import com.google.zxing.Result;
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeFamily;
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeScanner;
import com.hardbacknutter.tinyzxingwrapper.scanner.DecoderType;
import com.hardbacknutter.tinyzxingwrapper.scanner.ScanResultListener;
import com.hardbacknutter.tinyzxingwrapper.scanner.TzwViewfinderView;

import java.util.Map;
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
                    .putExtra(ScanIntentResult.Failure.REASON, info + "|" + e.getMessage())
                    .putExtra(ScanIntentResult.Failure.EXCEPTION, e);
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
                                    ScanIntentResult.Failure.REASON,
                                    ScanIntentResult.Failure.REASON_MISSING_CAMERA_PERMISSION);
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

    /**
     * Optional - Override as needed.
     */
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
                    new Intent().putExtra(ScanIntentResult.Failure.REASON,
                            ScanIntentResult.Failure.REASON_INACTIVITY));
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
                        new Intent().putExtra(ScanIntentResult.Failure.REASON,
                                ScanIntentResult.Failure.REASON_TIMEOUT));
                finish();
            }, hardTimeOutInMs);
        }
    }
    private static final Map<String, BarcodeFamily> BARCODE_FAMILY_INTENT_VALUE_TO_ENUM_MAP =
            Map.of("PRODUCT_MODE", BarcodeFamily.Product,
                    "ONE_D_MODE", BarcodeFamily.OneD,
                    "QR_CODE_MODE", BarcodeFamily.QrCode,
                    "DATA_MATRIX_MODE", BarcodeFamily.DataMatrix,
                    "AZTEC_MODE", BarcodeFamily.Aztec,
                    "PDF417_MODE", BarcodeFamily.Pdf417
            );

    private void startScanner() {
        final Bundle args = getIntent().getExtras();

        final BarcodeScanner.Builder builder = new BarcodeScanner.Builder();

        if (args.containsKey(ScanIntent.OptionKey.CAMERA_LENS_FACING)) {
            builder.setCameraLensFacing(args.getInt(ScanIntent.OptionKey.CAMERA_LENS_FACING,
                    CameraSelector.LENS_FACING_BACK));
        }

        final String codeFamily = args.getString(ScanIntent.OptionKey.CODE_FAMILY);
        if (codeFamily != null) {
            builder.setCodeFamily(BARCODE_FAMILY_INTENT_VALUE_TO_ENUM_MAP.get(codeFamily));
        }

        builder.setTorch(args.getBoolean(ScanIntent.OptionKey.TORCH_ENABLED, false))
                .setHints(args)
                .setDecoderType(args.getInt(ScanIntent.OptionKey.DECODER_TYPE,
                        DecoderType.Normal.type));

        if (viewFinderView != null && viewFinderView.isShowResultPoints()) {
            builder.setResultPointCallback(viewFinderView);
        }

        final BarcodeScanner scanner = builder.build(this, this,
                previewView.getSurfaceProvider());

        getLifecycle().addObserver(scanner);
        scanner.startScan(scanResultListener);
    }
}
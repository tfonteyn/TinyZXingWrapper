package com.hardbacknutter.tinyzxingwrapper.example;

import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;

import com.google.android.material.snackbar.Snackbar;
import com.hardbacknutter.tinyzxingwrapper.ScanContract;
import com.hardbacknutter.tinyzxingwrapper.ScanIntentResult;
import com.hardbacknutter.tinyzxingwrapper.ScanOptions;
import com.hardbacknutter.tinyzxingwrapper.scanner.DecoderType;
import com.hardbacknutter.tinyzxingwrapper.example.databinding.ActivityMainBinding;

public class MainActivity
        extends AppCompatActivity {

    private ActivityMainBinding vb;

    private final ActivityResultLauncher<ScanContract.Input> barcodeLauncher =
            registerForActivityResult(new ScanContract(), o -> o.ifPresent(result -> {
                if (result.isSuccess() && result.getText() != null) {
                    Snackbar.make(vb.getRoot(),
                            getString(R.string.msg_barcode, result.getText()),
                            Snackbar.LENGTH_LONG).show();

                } else {
                    switch (result.getFailure()) {
                        case ScanIntentResult.Failure.REASON_MISSING_CAMERA_PERMISSION:
                            Snackbar.make(vb.getRoot(),
                                    R.string.err_permission,
                                    Snackbar.LENGTH_LONG).show();
                            break;
                        case ScanIntentResult.Failure.REASON_TIMEOUT:
                            Snackbar.make(vb.getRoot(),
                                    R.string.err_hard_timeout,
                                    Snackbar.LENGTH_LONG).show();
                            break;
                        case ScanIntentResult.Failure.REASON_INACTIVITY:
                            Snackbar.make(vb.getRoot(),
                                    R.string.err_inactivity,
                                    Snackbar.LENGTH_LONG).show();
                            break;
                        default:
                            Snackbar.make(vb.getRoot(),
                                    getString(R.string.err_unknown, result.getFailure()),
                                    Snackbar.LENGTH_LONG).show();
                            break;
                    }
                }
            }));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vb = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        vb.basicScan.setOnClickListener(this::scanBarcode);
        vb.withScanType.setOnClickListener(this::scanBarcodeInverted);
        vb.frontCamera.setOnClickListener(this::scanBarcodeFrontCamera);
    }

    public void scanBarcode(@NonNull final View view) {
        barcodeLauncher.launch(new ScanOptions());
    }

    public void scanBarcodeInverted(@NonNull final View view) {
        final ScanOptions options = new ScanOptions();
        options.setDecoderType(DecoderType.Inverted);
        barcodeLauncher.launch(options);
    }

    public void scanBarcodeFrontCamera(@NonNull final View view) {
        final ScanOptions options = new ScanOptions();
        options.setPrompt(getString(R.string.msg_scan_prompt));
        options.setUseCameraWithLensFacing(CameraSelector.LENS_FACING_FRONT);
        barcodeLauncher.launch(options);
    }

}

package com.hardbacknutter.tinyzxingwrapper.example;

import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;

import com.hardbacknutter.tinyzxingwrapper.ScanContract;
import com.hardbacknutter.tinyzxingwrapper.ScanIntentResult;
import com.hardbacknutter.tinyzxingwrapper.ScanOptions;
import com.hardbacknutter.tinyzxingwrapper.example.databinding.ActivityMainBinding;
import com.hardbacknutter.tinyzxingwrapper.scanner.DecoderType;

public class MainActivity
        extends AppCompatActivity {

    private ActivityMainBinding vb;

    private final ActivityResultLauncher<ScanContract.Input> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.isSuccess()) {
                    final String text = getString(R.string.msg_barcode,
                                                  result.getText(),
                                                  String.valueOf(result.getFormat()));
                    vb.lastScan.setText(text);

                } else {
                    final String reason = result.getFailure();
                    if (reason != null) {
                        switch (reason) {
                            case ScanIntentResult.Failure.REASON_MISSING_CAMERA_PERMISSION:
                                vb.lastScan.setText(R.string.err_permission);
                                break;
                            case ScanIntentResult.Failure.REASON_TIMEOUT:
                                vb.lastScan.setText(R.string.err_hard_timeout);
                                break;
                            case ScanIntentResult.Failure.REASON_INACTIVITY:
                                vb.lastScan.setText(R.string.err_inactivity);
                                break;
                            default:
                                vb.lastScan.setText(
                                        getString(R.string.err_unknown, reason));
                                break;
                        }
                    } else {
                        vb.lastScan.setText(
                                getString(R.string.err_unknown, "???"));
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vb = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        vb.basicScan.setOnClickListener(this::scanBarcode);
        vb.withScanType.setOnClickListener(this::scanBarcodeInverted);
        vb.frontCamera.setOnClickListener(this::scanBarcodeFrontCamera);
    }

    private void scanBarcode(@NonNull final View view) {
        barcodeLauncher.launch(new ScanOptions());
    }

    private void scanBarcodeInverted(@NonNull final View view) {
        final ScanOptions options = new ScanOptions();
        options.setDecoderType(DecoderType.Inverted);
        barcodeLauncher.launch(options);
    }

    private void scanBarcodeFrontCamera(@NonNull final View view) {
        final ScanOptions options = new ScanOptions();
        options.setPrompt(getString(R.string.msg_scan_prompt));
        options.setUseCameraWithLensFacing(CameraSelector.LENS_FACING_FRONT);
        barcodeLauncher.launch(options);
    }

}

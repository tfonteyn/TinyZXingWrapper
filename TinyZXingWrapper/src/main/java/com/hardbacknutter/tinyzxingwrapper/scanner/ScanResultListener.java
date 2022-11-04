package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import com.google.zxing.Result;

/**
 * The {@link BarcodeScanner} will call the methods of this interface when a scan is finished.
 */
public interface ScanResultListener {

    /**
     * Barcode was successfully scanned.
     **/
    @UiThread
    void onResult(@NonNull Result result);

    /**
     * duh...
     */
    @UiThread
    void onError(final String s,
                 @NonNull Exception e);
}

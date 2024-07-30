package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import com.google.zxing.Result;

/**
 * The {@link BarcodeScanner} will call the methods of this interface
 * when a scan is decoded.
 */
public interface DecoderResultListener {

    /**
     * Barcode was successfully decoded.
     *
     * @param result from the scanner
     **/
    @UiThread
    void onResult(@NonNull Result result);

    /**
     * Decoding failed.
     *
     * @param e the {@link Exception} or {@link Error} thrown
     */
    @UiThread
    void onError(@NonNull Throwable e);
}

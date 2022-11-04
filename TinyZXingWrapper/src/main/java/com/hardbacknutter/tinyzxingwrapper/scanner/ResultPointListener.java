package com.hardbacknutter.tinyzxingwrapper.scanner;

import com.google.zxing.ResultPointCallback;

/**
 * The {@link BarcodeScanner} will call the methods of this interface with feedback
 * on image size and result-points during the scan process.
 */
public interface ResultPointListener
        extends ResultPointCallback {

    default void setImageSize(int width,
                              int height) {
        // do nothing by default
    }
}

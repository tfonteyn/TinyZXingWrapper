package com.hardbacknutter.tinyzxingwrapper.scanner;

import com.google.zxing.ResultPointCallback;

/**
 * The {@link BarcodeScanner} will call the methods of this interface with feedback
 * on image size and result-points during scan decoding.
 */
@FunctionalInterface
public interface DecoderResultPointsListener
        extends ResultPointCallback {

    /**
     * Receive feedback on the image size.
     *
     * @param width of the scan
     * @param height of the scan
     */
    default void setImageSize(final int width,
                              final int height) {
        // do nothing by default
    }
}

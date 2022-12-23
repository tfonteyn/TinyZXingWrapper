package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;

import com.google.zxing.BarcodeFormat;

import java.util.List;

/**
 * <a href="https://www.peaktech.com/blog/learn-about-the-different-types-of-barcodes/">
 * Barcode Types</a>
 * <p>
 * Groups 1D barcode types into 2 families for easier configuration.
 * <p>
 * 2D codes are not listed here as I presume typical use would be
 * scanning for just a single 2D type.
 * <p>
 * Note these are {@link List}s due to {@link com.google.zxing.DecodeHintType#POSSIBLE_FORMATS}
 * being declared to accept {@link List} data.
 */
@SuppressWarnings("unused")
public final class BarcodeFamily {
    /**
     * Decode only UPC and EAN barcodes.
     * e.g. use for shopping apps which get prices, reviews, etc. for products.
     */
    @NonNull
    public static final List<BarcodeFormat> PRODUCT = List.of(
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.RSS_14,
            BarcodeFormat.RSS_EXPANDED);

    /**
     * 1D industrial usage codes.
     */
    @NonNull
    public static final List<BarcodeFormat> INDUSTRIAL = List.of(
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODE_128,
            BarcodeFormat.ITF,
            BarcodeFormat.CODABAR);

    private BarcodeFamily() {
    }
}

package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;

import com.google.zxing.BarcodeFormat;

import java.util.EnumSet;
import java.util.Set;

/**
 * <a href="https://www.peaktech.com/blog/learn-about-the-different-types-of-barcodes/">
 * Barcode Types</a>
 * <p>
 * Groups 1D barcode types into 3 families for easier configuration.
 * <p>
 * 2D codes are not listed here as I presume typical use would be
 * scanning for just a single 2D type.
 * Use {@link com.google.zxing.DecodeHintType#POSSIBLE_FORMATS} directly for those.
 */
@SuppressWarnings("unused")
public enum BarcodeFamily {
    /**
     * Decode only UPC and EAN barcodes.
     * e.g. use for shopping apps which get prices, reviews, etc. for products.
     */
    Product(EnumSet.of(
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.RSS_14,
            BarcodeFormat.RSS_EXPANDED)),

    /**
     * 1D industrial usage codes.
     */
    Industrial(EnumSet.of(
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODE_128,
            BarcodeFormat.ITF,
            BarcodeFormat.CODABAR)),

    /**
     * Decode all {@link #Product} and {@link #Industrial} barcodes.
     */
    OneD(EnumSet.of(
            // Product
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.RSS_14,
            BarcodeFormat.RSS_EXPANDED,
            // Industrial
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODE_128,
            BarcodeFormat.ITF,
            BarcodeFormat.CODABAR));

    @NonNull
    final Set<BarcodeFormat> formats;

    BarcodeFamily(@NonNull final Set<BarcodeFormat> formats) {
        this.formats = formats;
    }
}

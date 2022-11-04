package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;

import com.google.zxing.BarcodeFormat;

import java.util.EnumSet;
import java.util.Set;

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
     * Decode all {@link #Product} barcodes and in addition a set of industrial codes.
     */
    OneD(EnumSet.of(
            // Product
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.RSS_14,
            BarcodeFormat.RSS_EXPANDED,
            // INDUSTRIAL_FORMATS
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODE_128,
            BarcodeFormat.ITF,
            BarcodeFormat.CODABAR)),

    QrCode( EnumSet.of(BarcodeFormat.QR_CODE)),

    DataMatrix( EnumSet.of(BarcodeFormat.DATA_MATRIX)),

    Aztec(EnumSet.of(BarcodeFormat.AZTEC)),

    Pdf417( EnumSet.of(BarcodeFormat.PDF_417));

    @NonNull
    public final Set<BarcodeFormat> formats;

    BarcodeFamily(@NonNull final Set<BarcodeFormat> formats) {
        this.formats = formats;
    }
}

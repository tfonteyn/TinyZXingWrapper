package com.hardbacknutter.tinyzxingwrapper;

import androidx.annotation.NonNull;

import com.google.zxing.BarcodeFormat;

import java.util.EnumSet;
import java.util.Set;

/**
 * OptionKey values for {@link ScanOptions#setBarcodeFamily(BarcodeFamily)}.
 * <p>
 * Alternatively use individual {@link BarcodeFormat}'s with {@link ScanOptions#setBarcodeFormats}.
 */
@SuppressWarnings("unused")
public enum BarcodeFamily {
    /**
     * Decode only UPC and EAN barcodes.
     * e.g. use for shopping apps which get prices, reviews, etc. for products.
     */
    Product("PRODUCT_MODE", EnumSet.of(
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.RSS_14,
            BarcodeFormat.RSS_EXPANDED)),

    /**
     * Decode all {@link #Product} barcodes and in addition a set of industrial codes.
     */
    OneD("ONE_D_MODE", EnumSet.of(
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

    QrCode("QR_CODE_MODE", EnumSet.of(BarcodeFormat.QR_CODE)),

    DataMatrix("DATA_MATRIX_MODE", EnumSet.of(BarcodeFormat.DATA_MATRIX)),

    Aztec("AZTEC_MODE", EnumSet.of(BarcodeFormat.AZTEC)),

    Pdf417("PDF417_MODE", EnumSet.of(BarcodeFormat.PDF_417));

    @NonNull
    private final String family;
    @NonNull
    private final Set<BarcodeFormat> formats;

    BarcodeFamily(@NonNull final String family,
                  @NonNull final Set<BarcodeFormat> formats) {
        this.family = family;
        this.formats = formats;
    }

    /**
     * Returns the enum constant of this type with the specified name.
     *
     * @param family to get
     *
     * @return a set of {@link BarcodeFormat} for the specified family
     *
     * @throws IllegalArgumentException – if the specified name does not exist.
     */
    @NonNull
    public static Set<BarcodeFormat> getFor(@NonNull final String family) {
        return valueOf(family.trim()).formats;
    }

    @NonNull
    public String getName() {
        return family;
    }
}

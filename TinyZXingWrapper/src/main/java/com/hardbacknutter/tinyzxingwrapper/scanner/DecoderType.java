package com.hardbacknutter.tinyzxingwrapper.scanner;

public enum DecoderType {

    /**
     * Expect to scan normal barcodes: black/dark bars on a white/light background.
     */
    Normal(0),
    /**
     * Expect to scan inverted barcodes: white/light bars on a black/dark background.
     */
    Inverted(1),
    /**
     * The scanner should try alternating to scan for normal and inverted barcodes.
     */
    Mixed(2);

    public final int type;

    DecoderType(final int type) {
        this.type = type;
    }

    public static DecoderType get(final int type) {
        switch (type) {
            case 2:
                return Mixed;
            case 1:
                return Inverted;
            case 0:
            default:
                return Normal;
        }
    }
}

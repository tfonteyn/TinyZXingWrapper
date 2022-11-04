package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.common.HybridBinarizer;

/**
 * Decoder that performs alternating scans in normal and inverted mode.
 */
public class MixedDecoder
        extends DefaultDecoder {

    /** first decode will be 'normal'; then alternate. */
    private boolean isInverted = true;

    public MixedDecoder(@NonNull final Reader reader) {
        super(reader);
    }

    @Override
    @NonNull
    protected BinaryBitmap toBitmap(@NonNull final LuminanceSource source) {
        isInverted = !isInverted;
        return new BinaryBitmap(new HybridBinarizer(isInverted ?
                source.invert() : source));
    }

}

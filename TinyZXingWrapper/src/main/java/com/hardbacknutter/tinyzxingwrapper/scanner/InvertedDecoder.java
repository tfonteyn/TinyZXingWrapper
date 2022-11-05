package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.common.HybridBinarizer;

@SuppressWarnings("WeakerAccess")
public class InvertedDecoder
        extends DefaultDecoder {

    protected InvertedDecoder(@NonNull final Reader reader) {
        super(reader);
    }

    @Override
    @NonNull
    protected BinaryBitmap toBitmap(@NonNull final LuminanceSource source) {
        return new BinaryBitmap(new HybridBinarizer(source.invert()));
    }
}

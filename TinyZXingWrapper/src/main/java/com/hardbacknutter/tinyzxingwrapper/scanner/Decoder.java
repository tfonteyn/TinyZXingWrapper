package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.LuminanceSource;
import com.google.zxing.Result;

/**
 * Either let the scanner use the default implementation {@link DefaultDecoder},
 * or define your own {@link BarcodeScanner.Builder#setDecoderFactory(DecoderFactory)}
 */
@FunctionalInterface
public interface Decoder {

    /**
     * Given an image source, attempt to decode the barcode.
     * <p>
     * Must not raise an exception.
     *
     * @param source to decode
     *
     * @return a Result or {@code null}
     */
    @Nullable
    Result decode(@NonNull LuminanceSource source);
}

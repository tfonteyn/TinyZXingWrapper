package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.ResultPointCallback;

/**
 * Factory to create Decoder instances.
 *
 * @see DefaultDecoderFactory
 */
@FunctionalInterface
public interface DecoderFactory {

    /**
     * Factory constructor.
     *
     * @return new Decode instance
     */
    @NonNull
    Decoder createDecoder(@Nullable ResultPointCallback resultPointCallback);
}

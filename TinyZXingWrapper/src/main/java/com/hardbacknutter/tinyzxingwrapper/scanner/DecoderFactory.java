package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;

/**
 * Factory to create Decoder instances.
 *
 * @see DefaultDecoderFactory
 */
@FunctionalInterface
public interface DecoderFactory {

    @NonNull
    Decoder createDecoder();
}

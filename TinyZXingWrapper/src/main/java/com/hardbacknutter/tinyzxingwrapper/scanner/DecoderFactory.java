package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;

/**
 * Factory to create Decoder instances.
 *
 * @see DefaultDecoderFactory
 */
@FunctionalInterface
public interface DecoderFactory {

    /**
     * Create a new Decoder.
     *
     * @return a new Decoder
     */
    @NonNull
    Decoder createDecoder();
}

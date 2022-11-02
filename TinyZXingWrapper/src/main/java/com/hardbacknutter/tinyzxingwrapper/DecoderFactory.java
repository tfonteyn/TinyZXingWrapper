package com.hardbacknutter.tinyzxingwrapper;

import androidx.annotation.NonNull;

/**
 * Factory to create Decoder instances.
 *
 * @see DefaultDecoderFactory
 */
public interface DecoderFactory {

    /**
     * Create a new Decoder.
     *
     * @return a new Decoder
     */
    @NonNull
    Decoder createDecoder();
}

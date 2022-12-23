package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;

import java.util.EnumMap;
import java.util.Map;

/**
 * DecoderFactory that creates a {@link Decoder}
 * using a {@link MultiFormatReader} and any provided {@link DecodeHintType} hints.
 */
@SuppressWarnings("WeakerAccess")
public class DefaultDecoderFactory
        implements DecoderFactory {

    private final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);

    protected DefaultDecoderFactory(@Nullable final Map<DecodeHintType, Object> hints) {
        if (hints != null) {
            this.hints.putAll(hints);
        }
    }

    @Override
    @NonNull
    public Decoder createDecoder() {
        final MultiFormatReader reader = new MultiFormatReader();
        final Decoder decoder = new DefaultDecoder(reader);

        // Use the decoder itself as the callback
        hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, decoder);

        reader.setHints(hints);

        return decoder;
    }
}

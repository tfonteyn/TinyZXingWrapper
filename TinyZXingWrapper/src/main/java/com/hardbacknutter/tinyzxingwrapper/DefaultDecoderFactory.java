package com.hardbacknutter.tinyzxingwrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;

import java.util.EnumMap;
import java.util.Map;

/**
 * DecoderFactory that creates a {@link Decoder} based on the {@link DecoderType}
 * using a {@link MultiFormatReader} and any provided {@link DecodeHintType} hints.
 */
public class DefaultDecoderFactory implements DecoderFactory {

    private final DecoderType type;

    private final Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);

    public DefaultDecoderFactory() {
        this(DecoderType.Normal, null);
    }

    public DefaultDecoderFactory(@NonNull final DecoderType type,
                                 @Nullable final Map<DecodeHintType, Object> hints) {
        this.type = type;
        if (hints != null) {
            this.hints.putAll(hints);
        }
    }

    @Override
    @NonNull
    public Decoder createDecoder() {
        final MultiFormatReader reader = new MultiFormatReader();
        Decoder decoder;
        switch (type) {
            case Inverted:
                decoder = new InvertedDecoder(reader);
                break;
            case Mixed:
                decoder = new MixedDecoder(reader);
                break;
            case Normal:
            default:
                decoder = new DefaultDecoder(reader);
                break;
        }

        // Use the decoder itself as the callback
        hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, decoder);

        reader.setHints(hints);

        return decoder;
    }
}

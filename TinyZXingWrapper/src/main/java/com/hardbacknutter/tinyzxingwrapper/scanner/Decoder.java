package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.LuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Either let the scanner use the default implementation {@link DefaultDecoder},
 * or define your own {@link BarcodeScanner.Builder#setDecoderFactory(DecoderFactory)}
 */
@FunctionalInterface
public interface Decoder
        extends ResultPointCallback {

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

    /**
     * Call immediately after {@link #decode(LuminanceSource)}, from the same thread.
     * <p>
     * The result is undefined while {@link #decode(LuminanceSource)} is running.
     * <p>
     * Optional to implement, this default implementation returns an empty list.
     *
     * @return possible {@link ResultPoint}'s from the last decode.
     */
    @NonNull
    default List<ResultPoint> getPossibleResultPoints() {
        return new ArrayList<>();
    }

    /**
     * Default do-nothing implementation for receiving points.
     *
     * @param point found
     */
    @Override
    default void foundPossibleResultPoint(@NonNull final ResultPoint point) {

    }
}

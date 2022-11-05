package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.LuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;

import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface Decoder
        extends ResultPointCallback {

    /**
     * Given an image source, attempt to decode the barcode.
     * <p>
     * Must not raise an exception.
     *
     * @return a Result or null
     */
    @Nullable
    Result decode(@NonNull LuminanceSource source);

    /**
     * Call immediately after decode(), from the same thread.
     * <p>
     * The result is undefined while decode() is running.
     * <p>
     * Optional to implement, this default implementation just returns an empty list.
     *
     * @return possible ResultPoint's from the last decode.
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

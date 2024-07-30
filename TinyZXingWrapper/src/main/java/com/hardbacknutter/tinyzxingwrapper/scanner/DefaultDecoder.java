package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;

import java.util.ArrayList;
import java.util.List;

/**
 * The default implementation of a {@link Decoder}.
 */
@SuppressWarnings("WeakerAccess")
public class DefaultDecoder
        implements Decoder {

    @NonNull
    protected final Reader reader;
    protected final List<ResultPoint> points = new ArrayList<>();

    /**
     * Create a new Decoder with the specified Reader.
     *
     * @param reader the reader
     */
    protected DefaultDecoder(@NonNull final Reader reader) {
        this.reader = reader;
    }

    @Override
    @Nullable
    public Result decode(@NonNull final LuminanceSource source) {
        return decode(toBitmap(source));
    }

    /**
     * Given an image source, convert to a binary bitmap.
     *
     * @param source the image source
     *
     * @return a BinaryBitmap
     */
    @NonNull
    protected BinaryBitmap toBitmap(@NonNull final LuminanceSource source) {
        return new BinaryBitmap(new HybridBinarizer(source));
    }

    /**
     * Decode a binary bitmap.
     *
     * @param bitmap the binary bitmap
     *
     * @return a Result or {@code null} on any error
     */
    @Nullable
    protected Result decode(@NonNull final BinaryBitmap bitmap) {
        points.clear();
        try {
            if (reader instanceof MultiFormatReader) {
                // Optimization - MultiFormatReader's normal decode() method is slow.
                return ((MultiFormatReader) reader).decodeWithState(bitmap);
            } else {
                return reader.decode(bitmap);
            }
        } catch (@NonNull final Exception ignore) {
            return null;

        } finally {
            reader.reset();
        }
    }

    @Override
    @NonNull
    public List<ResultPoint> getPossibleResultPoints() {
        return new ArrayList<>(points);
    }

    @Override
    public void foundPossibleResultPoint(@NonNull final ResultPoint point) {
        points.add(point);
    }

}

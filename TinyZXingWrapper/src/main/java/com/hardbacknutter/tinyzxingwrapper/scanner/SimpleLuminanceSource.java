package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.LuminanceSource;

/**
 * Stripped {@link com.google.zxing.PlanarYUVLuminanceSource}
 * with added rotation logic (NOT according to the interface for now!)
 * <p>
 * The rotation routines are adapted from
 * <a href="http://stackoverflow.com/a/15775173">stackoverflow</a>
 * but stripped to only use the 'Y' data of the image.
 */
@SuppressWarnings("WeakerAccess")
public class SimpleLuminanceSource
        extends LuminanceSource {

    /**
     * The Y data only. Padding and u/v data are stripped in the constructor.
     */
    @NonNull
    private final byte[] data;

    /**
     * @param yuvData     The image data. Padding (see rowStride/pixelStride) and u/v data
     *                    is allowed but will be stripped.
     * @param width       Width of the image
     * @param height      Height of the image
     * @param rowStride   The distance between the start of two consecutive rows
     *                    of pixels in the image.
     *                    It may be larger than the width of the image to account for
     *                    interleaved image data or padded formats.
     * @param pixelStride The distance between two consecutive pixel values in a row of pixels.
     *                    It may be larger than the size of a single pixel to account for
     *                    interleaved image data or padded formats.
     */
    public SimpleLuminanceSource(@NonNull final byte[] yuvData,
                                 final int width,
                                 final int height,
                                 final int rowStride,
                                 final int pixelStride) {
        super(width, height);
        if (rowStride == width && pixelStride == 1) {
            data = yuvData;
        } else {
            // normalise and strip any padding and the u/v data
            data = new byte[width * height];
            int dst = 0;
            for (int y = 0; y < height; y++) {
                final int rowStart = y * rowStride;
                for (int x = 0; x < width; x++) {
                    data[dst++] = yuvData[rowStart + (x * pixelStride)];
                }
            }
        }
    }

    private SimpleLuminanceSource(@NonNull final byte[] data,
                                  final int width,
                                  final int height) {
        super(width, height);
        this.data = data;

        if (data.length != (width * height)) {
            throw new IllegalArgumentException("data contains padding and or u/v data");
        }
    }

    @Override
    @NonNull
    public byte[] getRow(final int y,
                         @Nullable byte[] row) {
        if (y < 0 || y >= getHeight()) {
            throw new IllegalArgumentException("Requested row is outside the image: " + y);
        }

        final int width = getWidth();
        if (row == null || row.length < width) {
            row = new byte[width];
        }
        System.arraycopy(data, y * width, row, 0, width);
        return row;
    }

    @Override
    @NonNull
    public byte[] getMatrix() {
        return data;
    }

    /**
     * Flip the data around the vertical axis.
     *
     * @param flip {@code true} to flip; {@code false} will return the original
     *
     * @return the flipped data
     */
    @NonNull
    public SimpleLuminanceSource flipHorizontal(final boolean flip) {
        if (flip) {
            final int width = getWidth();
            final int height = getHeight();

            final int len = width * height;
            final byte[] yData = new byte[len];

            for (int y = 0, rowStart = 0;
                 y < height;
                 y++, rowStart += width) {

                final int middle = rowStart + width / 2;
                for (int x1 = rowStart, x2 = rowStart + width - 1;
                     x1 < middle;
                     x1++, x2--) {

                    yData[x1] = data[x2];
                    yData[x2] = data[x1];
                }
            }
            return new SimpleLuminanceSource(yData, width, height);

        } else {
            return this;
        }
    }

    /**
     * Flip the data around the horizontal axis.
     *
     * @param flip {@code true} to flip; {@code false} will return the original
     *
     * @return the flipped data
     */
    @NonNull
    public SimpleLuminanceSource flipVertical(final boolean flip) {
        if (flip) {
            final int width = getWidth();
            final int height = getHeight();

            final int len = width * height;
            final byte[] yData = new byte[len];

            int dst = len - 1;
            for (int src = 0; src < len; src++) {
                yData[dst] = data[src];
                dst--;
            }
            return new SimpleLuminanceSource(yData, width, height);

        } else {
            return this;
        }
    }

    /**
     * Convenience method; accepts {@code 90, 180, 270} angles.
     * Any other angle and it returns the original. No error is thrown.
     *
     * @param degrees to rotate
     *
     * @return the rotated data
     */
    @NonNull
    public SimpleLuminanceSource rotate(final int degrees) {
        switch (degrees) {
            case 90:
                return rotateClockwise();
            case 180:
                return flipVertical(true);
            case 270:
                return rotateCounterClockwise();
            default:
                return this;
        }
    }

    /**
     * Rotate an image by 90 degrees CW.
     *
     * @return the rotated data
     */
    @NonNull
    private SimpleLuminanceSource rotateClockwise() {
        final int width = getWidth();
        final int height = getHeight();

        final int len = width * height;
        final byte[] yData = new byte[len];

        int dst = 0;
        for (int x = 0; x < width; x++) {
            for (int y = height - 1; y >= 0; y--) {
                yData[dst] = data[y * width + x];
                dst++;
            }
        }
        //noinspection SuspiciousNameCombination
        return new SimpleLuminanceSource(yData, height, width);
    }

    /**
     * Rotate an image by 90 degrees CCW.
     * <p>
     * Note: this one implements the interface, but for now we're missing
     * {@link #rotateCounterClockwise45()} so we cannot add {@link #isRotateSupported()} yet.
     *
     * @return the rotated data
     */
    @Override
    @NonNull
    public SimpleLuminanceSource rotateCounterClockwise() {
        final int width = getWidth();
        final int height = getHeight();

        final int len = width * height;
        final byte[] yData = new byte[len];

        int dst = len - 1;
        for (int x = 0; x < width; x++) {
            for (int y = height - 1; y >= 0; y--) {
                yData[dst] = data[y * width + x];
                dst--;
            }
        }
        //noinspection SuspiciousNameCombination
        return new SimpleLuminanceSource(yData, height, width);
    }
}


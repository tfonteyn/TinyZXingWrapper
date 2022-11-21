package com.hardbacknutter.tinyzxingwrapper.scanner;

import androidx.annotation.NonNull;

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
public class SimpleYLuminanceSource
        extends LuminanceSource {

    private final byte[] yuvData;

    public SimpleYLuminanceSource(@NonNull final byte[] yuvData,
                                  final int width,
                                  final int height) {
        super(width, height);
        this.yuvData = yuvData;
    }



    @Override
    @NonNull
    public byte[] getRow(final int y,
                         byte[] row) {
        if (y < 0 || y >= getHeight()) {
            throw new IllegalArgumentException("Requested row is outside the image: " + y);
        }

        final int width = getWidth();
        if (row == null || row.length < width) {
            row = new byte[width];
        }
        System.arraycopy(yuvData, y * width, row, 0, width);
        return row;
    }

    @Override
    @NonNull
    public byte[] getMatrix() {
        return yuvData;
    }


    /**
     * Flip the data around the vertical axis. Only the 'y' data is rotated; u/v is dropped.
     *
     * @return the flipped data
     */
    @NonNull
    public SimpleYLuminanceSource flipHorizontal(final boolean flip) {
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

                    yData[x1] = yuvData[x2];
                    yData[x2] = yuvData[x1];
                }
            }
            return new SimpleYLuminanceSource(yData, width, height);

        } else {
            return this;
        }
    }

    /**
     * Flip the data around the horizontal axis. Only the 'y' data is rotated; u/v is dropped.
     *
     * @return the flipped data
     */
    @NonNull
    private SimpleYLuminanceSource flipVertical() {
        final int width = getWidth();
        final int height = getHeight();

        final int len = width * height;
        final byte[] yData = new byte[len];

        int dst = len - 1;
        for (int src = 0; src < len; src++) {
            yData[dst] = yuvData[src];
            dst--;
        }
        return new SimpleYLuminanceSource(yData, width, height);
    }


    @NonNull
    public SimpleYLuminanceSource rotate(final int rotationDegrees) {
        switch (rotationDegrees) {
            case 90:
                return rotateClockwise();
            case 180:
                return flipVertical();
            case 270:
                return rotateCounterClockwise();
            case 0:
            default:
                return this;
        }
    }

    /**
     * Rotate an image by 90 degrees CW. Only the 'Y' data is rotated; u/v is dropped.
     *
     * @return the rotated data
     */
    @NonNull
    private SimpleYLuminanceSource rotateClockwise() {
        final int width = getWidth();
        final int height = getHeight();

        final int len = width * height;
        final byte[] yData = new byte[len];

        int dst = 0;
        for (int x = 0; x < width; x++) {
            for (int y = height - 1; y >= 0; y--) {
                yData[dst] = yuvData[y * width + x];
                dst++;
            }
        }
        //noinspection SuspiciousNameCombination
        return new SimpleYLuminanceSource(yData, height, width);
    }

    /**
     * Rotate an image by 90 degrees CCW. Only the 'y' data is rotated; u/v is dropped.
     * <p>
     * Note: this one implements the interface, but for now we're missing
     * {@link #rotateCounterClockwise45()} so we cannot add {@link #isRotateSupported()} yet.
     *
     * @return the rotated data
     */
    @Override
    @NonNull
    public SimpleYLuminanceSource rotateCounterClockwise() {
        final int width = getWidth();
        final int height = getHeight();

        final int len = width * height;
        final byte[] yData = new byte[len];

        int dst = len - 1;
        for (int x = 0; x < width; x++) {
            for (int y = height - 1; y >= 0; y--) {
                yData[dst] = yuvData[y * width + x];
                dst--;
            }
        }
        //noinspection SuspiciousNameCombination
        return new SimpleYLuminanceSource(yData, height, width);
    }
}


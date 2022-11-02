/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hardbacknutter.tinyzxingwrapper;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.ResultPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Mainly meant as cosmetic feedback to the end-user.
 * <p>
 * This view is overlaid on top of the camera preview.
 * It adds a laser scanner animation and (optional) result points.
 */
public class TzwViewfinderView
        extends View
        implements ResultPointListener {

    private static final int[] LASER_COLOR_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
    private static final long ANIMATION_DELAY_MS = 80L;

    private static final int MAX_POINTS = 20;
    private static final int POINT_OPACITY = 0xA0;
    private static final int POINT_SIZE = 6;
    // half of current
    private static final int PREVIOUS_POINT_OPACITY = 0x50;
    private static final int PREVIOUS_POINT_SIZE = 3;

    private final Paint paint;

    private final List<ResultPoint> resultPoints = new ArrayList<>(MAX_POINTS);
    private final List<ResultPoint> previousResultPoints = new ArrayList<>(MAX_POINTS);

    /**
     * Current index into {@link #LASER_COLOR_ALPHA}.
     */
    private int laserColorAlphaIndex;
    @ColorInt
    private int laserColor;
    /**
     * Default to true.
     */
    private boolean enableResultPoints;
    @ColorInt
    private int resultPointColor;
    private int imageWidth;
    private int imageHeight;

    public TzwViewfinderView(@NonNull final Context context) {
        this(context, null);
    }

    public TzwViewfinderView(@NonNull final Context context,
                             @Nullable final AttributeSet attrs) {
        super(context, attrs);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Resources resources = getResources();
        final Resources.Theme theme = getContext().getTheme();

        @SuppressWarnings("resource")
        final TypedArray attributes = getContext()
                .obtainStyledAttributes(attrs, R.styleable.TzwViewfinderView);

        laserColor = attributes.getColor(
                R.styleable.TzwViewfinderView_tzw_laser_color,
                resources.getColor(R.color.tzw_laser, theme));

        enableResultPoints = attributes.getBoolean(
                R.styleable.TzwViewfinderView_tzw_enable_result_points,
                true);
        resultPointColor = attributes.getColor(
                R.styleable.TzwViewfinderView_tzw_result_points_color,
                resources.getColor(R.color.tzw_result_point, theme));

        attributes.recycle();
    }

    @SuppressWarnings("unused")
    public void setLaserColor(@ColorInt final int color) {
        this.laserColor = color;
    }

    public boolean isEnableResultPoints() {
        return enableResultPoints;
    }

    @SuppressWarnings("unused")
    public void setEnableResultPoints(final boolean visible) {
        this.enableResultPoints = visible;
    }

    @SuppressWarnings("unused")
    public void setResultPointColor(@ColorInt final int color) {
        this.resultPointColor = color;
    }

    @Override
    public void onDraw(@NonNull final Canvas canvas) {

        paint.setColor(laserColor);
        // create some variation just like the real thing
        paint.setAlpha(LASER_COLOR_ALPHA[laserColorAlphaIndex]);
        laserColorAlphaIndex = (laserColorAlphaIndex + 1) % LASER_COLOR_ALPHA.length;

        final int middle = getHeight() / 2 + getTop();
        canvas.drawRect(getLeft() + 2, middle - 1,
                getRight() - 2, middle + 1,
                paint);

        if (enableResultPoints) {
            final float scaleX;
            final float scaleY;
            if (imageWidth > 0 && imageHeight > 0) {
                scaleX = getWidth() / (float) imageWidth;
                scaleY = getHeight() / (float) imageHeight;
            } else {
                scaleX = 1;
                scaleY = 1;
            }

            if (!previousResultPoints.isEmpty()) {
                drawResultPoints(canvas, previousResultPoints, scaleX, scaleY,
                        PREVIOUS_POINT_SIZE, PREVIOUS_POINT_OPACITY);
            }

            synchronized (resultPoints) {
                if (!resultPoints.isEmpty()) {
                    previousResultPoints.addAll(resultPoints);
                    drawResultPoints(canvas, resultPoints, scaleX, scaleY,
                            POINT_SIZE, POINT_OPACITY);
                }
            }
        }


        // Request another update at the animation interval,
        postInvalidateDelayed(ANIMATION_DELAY_MS);
    }

    private void drawResultPoints(@NonNull final Canvas canvas,
                                  @NonNull final List<ResultPoint> points,
                                  final float scaleX,
                                  final float scaleY,
                                  final float radius,
                                  final int alpha) {
        paint.setAlpha(alpha);
        paint.setColor(resultPointColor);
        points.forEach(point -> canvas.drawCircle(
                (int) point.getX() * scaleX,
                (int) point.getY() * scaleY,
                radius, paint));
        points.clear();
    }

    @Override
    public void setImageSize(final int width,
                             final int height) {
        imageWidth = width;
        imageHeight = height;
    }

    @Override
    public void foundPossibleResultPoint(@NonNull final ResultPoint point) {
        synchronized (resultPoints) {
            if (resultPoints.size() < MAX_POINTS) {
                resultPoints.add(point);
            }
        }
    }

}

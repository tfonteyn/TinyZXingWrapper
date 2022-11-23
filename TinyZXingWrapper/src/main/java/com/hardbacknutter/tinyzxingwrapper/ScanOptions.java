package com.hardbacknutter.tinyzxingwrapper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeScanner;

/**
 * Input options for {@link ScanContract}.
 */
@SuppressWarnings({"unused", "WeakerAccess", "FieldNotUsedInToString"})
public class ScanOptions {

    /**
     * future compatibility, currently set on the Intent, but not used.
     */
    public static final String ACTION = "com.hardbacknutter.tinyzxingwrapper.action.SCAN";
    private final Intent intent = new Intent();
    @NonNull
    private Class<?> captureActivity = CaptureActivity.class;

    /**
     * Enable the torch.
     *
     * @param enabled {@code true} to enable the torch
     *
     * @return this
     */
    @NonNull
    public ScanOptions setTorchEnabled(final boolean enabled) {
        intent.putExtra(Option.TORCH_ENABLED, enabled);
        return this;
    }

    /**
     * Select a specific camera - i.e. with the lens facing the given direction.
     * Note this is <strong>not</strong> a camera id!
     *
     * @param lensFacing either {@link CameraSelector#LENS_FACING_FRONT}
     *                   or {@link CameraSelector#LENS_FACING_BACK}
     *
     * @return this
     */
    @NonNull
    public ScanOptions setUseCameraWithLensFacing(final int lensFacing) {
        if (lensFacing == CameraSelector.LENS_FACING_FRONT
            || lensFacing == CameraSelector.LENS_FACING_BACK) {
            intent.putExtra(Option.CAMERA_LENS_FACING, lensFacing);
        }
        return this;
    }

    /**
     * Set the desired barcode formats to try and decode.
     *
     * @param list of {@link BarcodeFormat}s to try decoding
     *
     * @return this
     */
    @NonNull
    public ScanOptions setBarcodeFormats(@NonNull final List<BarcodeFormat> list) {
        if (!list.isEmpty()) {
            intent.putStringArrayListExtra(DecodeHintType.POSSIBLE_FORMATS.name(),
                                           list.stream()
                                               .map(Enum::name)
                                               .collect(Collectors.toCollection(ArrayList::new)));
        }
        return this;
    }

    @NonNull
    public ScanOptions setAlsoTryInverted(final boolean enabled) {
        intent.putExtra(DecodeHintType.ALSO_INVERTED.name(), enabled);
        return this;
    }

    @NonNull
    public ScanOptions setTryHarder(final boolean enabled) {
        intent.putExtra(DecodeHintType.TRY_HARDER.name(), enabled);
        return this;
    }

    /**
     * Request extra/available meta data to be returned.
     *
     * @param list of {@link ResultMetadataType} to return if possible
     *
     * @return this
     */
    @NonNull
    public ScanOptions setReturnMetadata(@NonNull final List<ResultMetadataType> list) {
        if (!list.isEmpty()) {
            intent.putStringArrayListExtra(Option.RETURN_META_DATA,
                                           list.stream()
                                               .map(Enum::name)
                                               .collect(Collectors.toCollection(ArrayList::new)));
        }
        return this;
    }

    /**
     * Targets {@link CaptureActivity}.
     * <p>
     * Set a prompt to display on the capture screen.
     * <p>
     * The {@link CaptureActivity} will display this instead of the predefined message.
     * Use {@code ""} for no prompt at all.
     *
     * @param prompt the prompt to display
     */
    @NonNull
    public final ScanOptions setPrompt(@Nullable final String prompt) {
        intent.putExtra(CaptureActivity.Option.PROMPT,
                        Objects.requireNonNullElse(prompt, ""));
        return this;
    }

    /**
     * Targets {@link CaptureActivity}.
     * <p>
     * Enable a (hard) timer to finish/cancel the scan on the given timeout.
     * <p>
     * The returned {@code resultCode} will be Activity.RESULT_CANCELED.
     * The returned {@code intent} will contain the
     * key {@link ScanIntentResult.Failure#FAILURE_REASON} with
     * value {@link ScanIntentResult.Failure#REASON_TIMEOUT}.
     *
     * @return this
     */
    @NonNull
    public ScanOptions setTimeout(final long timeout) {
        intent.putExtra(CaptureActivity.Option.TIMEOUT_MS, timeout);
        return this;
    }

    /**
     * Targets {@link CaptureActivity}.
     * <p>
     * Enable a (soft) timer to finish/cancel the scan on what the device considers
     * inactivity after the given timeout.
     * <p>
     * The returned {@code resultCode} will be Activity.RESULT_CANCELED.
     * The returned {@code intent} will contain the
     * key {@link ScanIntentResult.Failure#FAILURE_REASON} with
     * value {@link ScanIntentResult.Failure#REASON_INACTIVITY}.
     *
     * @return this
     */
    @NonNull
    public ScanOptions setInactivityTimeout(final long timeout) {
        intent.putExtra(CaptureActivity.Option.INACTIVITY_TIMEOUT_MS, timeout);
        return this;
    }

    /**
     * Set the Activity class to use. It should provide equivalent functionality
     * to the default {@link CaptureActivity}.
     *
     * @param captureActivity the class, or {@code null} to use the default.
     */
    @NonNull
    public ScanOptions setCaptureActivity(@Nullable final Class<?> captureActivity) {
        this.captureActivity = Objects.requireNonNullElse(captureActivity, CaptureActivity.class);
        return this;
    }

    /**
     * Retrieve the input 'extras' to set any desired custom arguments (decoder hints)
     *
     * @return the input Intent 'extras' bundle
     */
    @NonNull
    public Bundle getExtras() {
        return intent.getExtras();
    }

    /**
     * Create a scan intent with the specified options.
     *
     * @return the intent
     */
    @NonNull
    Intent build(@NonNull final Context context) {
        intent.setComponent(new ComponentName(context, captureActivity))
              .setAction(ACTION)
              .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        return intent;
    }

    @Override
    @NonNull
    public String toString() {
        return "ScanOptions{"
               + "intent=" + intent
               + '}';
    }

    /**
     * Arguments implemented by the standalone scanner ({@link BarcodeScanner}.
     * <p>
     * Other than the keys in this class, you can also pass in any keys as defined
     * in {@link DecodeHintType} with the exception of
     * {@link DecodeHintType#NEED_RESULT_POINT_CALLBACK} which is used internally.
     */
    public static final class Option {

        /**
         * Enables the torch.
         * <p>
         * Type: boolean
         * <p>
         * Default: {@code false}
         *
         * @see ScanOptions#setTorchEnabled(boolean)
         */
        public static final String TORCH_ENABLED = "TORCH_ENABLED";

        /**
         * Select a specific camera with the lens facing in the desired direction.
         * Type: int,  One of:
         * <ul>
         *     <li>{@link CameraSelector#LENS_FACING_FRONT}</li>
         *     <li>{@link CameraSelector#LENS_FACING_BACK}</li>
         * </ul>
         * Default: not set; the device will normally pick the 'best' back-facing camera.
         * <p>
         *
         * @see ScanOptions#setUseCameraWithLensFacing(int)
         */
        public static final String CAMERA_LENS_FACING = "CAMERA_LENS_FACING";

        /**
         * Advanced usage. Request to include metadata in the result intent.
         * <p>
         * Type: a {@code List<String>} with {@link ResultMetadataType} key names.
         * Unknown (or misspelled) entries will be ignored.
         *
         * @see ScanIntentResult#createActivityResultIntent(Context, Result, List)
         */
        public static final String RETURN_META_DATA = "RETURN_META_DATA";

        private Option() {
        }
    }
}

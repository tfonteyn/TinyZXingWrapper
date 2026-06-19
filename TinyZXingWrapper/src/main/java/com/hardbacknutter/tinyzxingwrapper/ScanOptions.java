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
     * Targets {@link BarcodeScanner}.
     * <p>
     * Select a specific camera - i.e. with the lens facing the given direction.
     * Note this is <strong>not</strong> a camera id!
     *
     * @param lensFacing either {@link CameraSelector#LENS_FACING_FRONT}
     *                   or {@link CameraSelector#LENS_FACING_BACK}
     *
     * @return this
     *
     * @see ScanOptions.Option#CAMERA_LENS_FACING
     */
    @NonNull
    public ScanOptions setUseCameraWithLensFacing(final int lensFacing) {
        if (lensFacing == CameraSelector.LENS_FACING_FRONT
            || lensFacing == CameraSelector.LENS_FACING_BACK) {
            intent.putExtra(ScanOptions.Option.CAMERA_LENS_FACING, lensFacing);
        }
        return this;
    }

    /**
     * Targets {@link BarcodeScanner}.
     * <p>
     * Enable autofocus. By default, disabled (i.e. left to the device to decide).
     *
     * @param enable {@code true} to enable
     *
     * @return this
     *
     * @see ScanOptions.Option#AUTO_FOCUS
     */
    @NonNull
    public ScanOptions setAutoFocus(final boolean enable) {
        intent.putExtra(ScanOptions.Option.AUTO_FOCUS, enable);
        return this;
    }

    /**
     * Targets {@link BarcodeScanner}.
     * <p>
     * Request extra/available metadata to be returned.
     *
     * @param list of {@link ResultMetadataType} to return if possible
     *
     * @return this
     *
     * @see ScanOptions.Option#RETURN_META_DATA
     */
    @NonNull
    public ScanOptions setReturnMetadata(@NonNull final List<ResultMetadataType> list) {
        if (!list.isEmpty()) {
            intent.putStringArrayListExtra(ScanOptions.Option.RETURN_META_DATA,
                                           list.stream()
                                               .map(Enum::name)
                                               .collect(Collectors.toCollection(ArrayList::new)));
        }
        return this;
    }


    /**
     * Targets {@link com.google.zxing.Reader}.
     * <p>
     * Set the desired barcode formats to try and decode.
     * <p>
     * <strong>IMPORTANT:</strong>
     * The value is stored as an {@link ArrayList} with the {@code String} values
     * of the {@link BarcodeFormat} enum names !
     *
     * @param list of {@link BarcodeFormat}s to try decoding
     *
     * @return this
     *
     * @see DecodeHintType#POSSIBLE_FORMATS
     * @see BarcodeScanner.Builder#addHints(Bundle)
     */
    @NonNull
    public ScanOptions setBarcodeFormats(@NonNull final List<BarcodeFormat> list) {
        if (!list.isEmpty()) {
            intent.putStringArrayListExtra(DecodeHintType.POSSIBLE_FORMATS.name(),
                                           list.stream()
                                               // Note we store the NAME of the enum
                                               // so we do not have to parcel the enum values
                                               .map(Enum::name)
                                               .collect(Collectors.toCollection(ArrayList::new)));
        }
        return this;
    }

    /**
     * Targets {@link com.google.zxing.Reader}.
     * <p>
     * If true, also tries to decode as inverted image.
     *
     * @param enabled flag
     *
     * @return this
     *
     * @see DecodeHintType#ALSO_INVERTED
     * @see BarcodeScanner.Builder#addHints(Bundle)
     */
    @NonNull
    public ScanOptions setAlsoTryInverted(final boolean enabled) {
        if (enabled) {
            intent.putExtra(DecodeHintType.ALSO_INVERTED.name(), true);
        } else {
            intent.removeExtra(DecodeHintType.ALSO_INVERTED.name());
        }
        return this;
    }

    /**
     * Targets {@link com.google.zxing.Reader}.
     * <p>
     * Spend more time to try to find a barcode; optimize for accuracy, not speed.
     *
     * @param enabled flag
     *
     * @return this
     *
     * @see DecodeHintType#TRY_HARDER
     * @see BarcodeScanner.Builder#addHints(Bundle)
     */
    @NonNull
    public ScanOptions setTryHarder(final boolean enabled) {
        if (enabled) {
            intent.putExtra(DecodeHintType.TRY_HARDER.name(), true);
        } else {
            intent.removeExtra(DecodeHintType.TRY_HARDER.name());
        }
        return this;
    }

    /**
     * Targets {@link CaptureActivity}.
     * <p>
     * Show the zoom-control-slider on the capture screen. By default, hidden.
     *
     * @param enabled {@code true} to show
     *
     * @return this
     *
     * @see CaptureActivity.Option#SHOW_ZOOM
     */
    @NonNull
    public ScanOptions setShowZoomControl(final boolean enabled) {
        if (enabled) {
            intent.putExtra(CaptureActivity.Option.SHOW_ZOOM, true);
        } else {
            intent.removeExtra(CaptureActivity.Option.SHOW_ZOOM);
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
     *
     * @return this
     *
     * @see CaptureActivity.Option#PROMPT
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
     * @param timeout in milliseconds
     *
     * @return this
     *
     * @see CaptureActivity.Option#TIMEOUT_MS
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
     * @param timeout in milliseconds
     *
     * @return this
     *
     * @see CaptureActivity.Option#INACTIVITY_TIMEOUT_MS
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
     *
     * @return this
     */
    @NonNull
    public ScanOptions setCaptureActivity(@Nullable final Class<?> captureActivity) {
        this.captureActivity = Objects.requireNonNullElse(captureActivity, CaptureActivity.class);
        return this;
    }


    /**
     * Retrieve the input 'extras' to set any desired custom arguments (decoder hints)
     * for the {@link com.google.zxing.Reader}.
     *
     * @return the input Intent 'extras' bundle
     */
    @NonNull
    public Bundle getExtras() {
        //noinspection DataFlowIssue
        return intent.getExtras();
    }

    /**
     * Create a scan intent with the specified options.
     *
     * @param context Current context
     *
     * @return the intent
     */
    @NonNull
    public Intent build(@NonNull final Context context) {
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
     * Arguments implemented by the standalone {@link BarcodeScanner}.
     * <p>
     * Other than the keys in this class, you can also pass in any keys as defined
     * in {@link DecodeHintType} except
     * {@link DecodeHintType#NEED_RESULT_POINT_CALLBACK} which is used internally.
     */
    public static final class Option {

        /**
         * Select a specific camera with the lens facing in the desired direction.
         * Type: int,  One of:
         * <ul>
         *     <li>{@link CameraSelector#LENS_FACING_FRONT}</li>
         *     <li>{@link CameraSelector#LENS_FACING_BACK}</li>
         * </ul>
         * Default: not set; the device will normally pick the 'best' back-facing camera.
         *
         * @see ScanOptions#setUseCameraWithLensFacing(int)
         */
        public static final String CAMERA_LENS_FACING = "CAMERA_LENS_FACING";

        /**
         * Enable autofocus to the centre of the preview.
         * <p>
         * Type: boolean
         * <p>
         * Default: {@code false}
         *
         * @see ScanOptions#setAutoFocus(boolean)
         */
        public static final String AUTO_FOCUS = "AUTO_FOCUS";

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

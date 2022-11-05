package com.hardbacknutter.tinyzxingwrapper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;

import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeFamily;
import com.hardbacknutter.tinyzxingwrapper.scanner.BarcodeScanner;
import com.hardbacknutter.tinyzxingwrapper.scanner.DecoderType;

import java.util.Objects;

/**
 * Low-level Intent keys used to create the input options for {@link ScanContract}.
 */
@SuppressWarnings("WeakerAccess")
public class ScanIntent
        implements ScanContract.Input {

    /**
     * future compatibility, currently set on the Intent, but not used.
     */
    public static final String ACTION = "com.hardbacknutter.tinyzxingwrapper.action.SCAN";

    protected final Intent intent = new Intent();
    @NonNull
    private Class<?> captureActivity = CaptureActivity.class;

    /**
     * Set the Activity class to use. It should provide equivalent functionality
     * to the default {@link CaptureActivity}.
     *
     * @param captureActivity the class, or {@code null} to use the default.
     */
    @SuppressWarnings("unused")
    public void setCaptureActivity(@Nullable final Class<?> captureActivity) {
        this.captureActivity = Objects.requireNonNullElse(captureActivity, CaptureActivity.class);
    }

    /**
     * Retrieve the input arguments.
     * <p>
     * Allows to add extra/custom values.
     *
     * @return the input Intent "extras" bundle
     */
    @SuppressWarnings("unused")
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
    public Intent createScanIntent(@NonNull final Context context) {
        intent.setComponent(new ComponentName(context, captureActivity))
                .setAction(ACTION)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        return intent;
    }

    /**
     * Arguments implemented by the standalone scanner ({@link BarcodeScanner}.
     * <p>
     * Other than the keys in this class, you can also pass in any keys as defined
     * in {@link DecodeHintType} with the exception of
     * {@link DecodeHintType#NEED_RESULT_POINT_CALLBACK} which is used internally.
     * <p>
     * For example, if using the {@link #CODE_FAMILY} is to coarse for your requirements,
     * you can use {@link DecodeHintType#POSSIBLE_FORMATS} to provide a comma separated
     * list of individual barcode formats to (try to) decode.
     */
    public static final class OptionKey {

        /**
         * Enables the torch.
         * <p>
         * Type: boolean
         * <p>
         * Default: {@code false}
         *
         * @see com.hardbacknutter.tinyzxingwrapper.ScanOptions#setTorchEnabled(boolean)
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
         * @see com.hardbacknutter.tinyzxingwrapper.ScanOptions#setUseCameraWithLensFacing(int)
         */
        public static final String CAMERA_LENS_FACING = "CAMERA_LENS_FACING";

        /**
         * Set the decoder type.
         * <p>
         * Type: int; one of the {@link DecoderType#type} values.
         * <p>
         * Default: {@link DecoderType#Normal} type
         *
         * @see DecoderType
         * @see com.hardbacknutter.tinyzxingwrapper.ScanOptions#setDecoderType(DecoderType)
         */
        public static final String DECODER_TYPE = "DECODER_TYPE";

        /**
         * Limit scanning to a set of predefined formats.
         * <p>
         * Setting this is effectively shorthand for setting explicit formats
         * with {@link DecodeHintType#POSSIBLE_FORMATS}.
         * The decoder uses the combined list of these options.
         * <p>
         * Type: String
         * <p>
         * Default: not set; all supported formats will be tried.
         *
         * @see BarcodeFamily
         * @see com.hardbacknutter.tinyzxingwrapper.ScanOptions#setBarcodeFamily(BarcodeFamily)
         */
        public static final String CODE_FAMILY = "CODE_FAMILY";

        /**
         * Advanced usage. Request to include metadata in the result intent.
         * <p>
         * Type: csv String list with {@link ResultMetadataType} key names.
         * Unknown (or misspelled) entries will be ignored.
         *
         * @see ScanContract#createResultIntent(Context, Result, String)
         */
        public static final String RETURN_META_DATA = "RETURN_META_DATA";

        private OptionKey() {
        }
    }

    /**
     * Arguments implemented by the default {@link CaptureActivity}.
     * Optional for custom implementations.
     */
    public static final class ToolOptionKey {

        /**
         * Prompt to show while scanning. Set to {@code ""} for none.
         * <p>
         * Default: use the predefined message.
         * <p>
         * Type: String
         *
         * @see com.hardbacknutter.tinyzxingwrapper.ScanOptions#setPrompt(String)
         */
        public static final String PROMPT_MESSAGE = "PROMPT_MESSAGE";
        /**
         * Set a (hard) timeout in milliseconds to finish the scan screen.
         * If no scan is done within this timeout, the attempt will be cancelled.
         *
         * <p>
         * Default: not set.
         * <p>
         * Type: long (milliseconds)
         *
         * @see com.hardbacknutter.tinyzxingwrapper.ScanOptions#setTimeout(long)
         */
        public static final String TIMEOUT_MS = "TIMEOUT_MS";
        /**
         * Set a (soft) timeout in milliseconds to finish the scan screen.
         * Let the device decide if the user has been inactive for longer than this timeout.
         * If so, the attempt will be cancelled.
         * <p>
         * Default: see {@link InactivityTimer}, currently defined at 3 minutes.
         * <p>
         * Type: long (milliseconds)
         *
         * @see com.hardbacknutter.tinyzxingwrapper.ScanOptions#setInactivityTimeout(long)
         */
        public static final String INACTIVITY_TIMEOUT_MS = "INACTIVITY_TIMEOUT_MS";

        private ToolOptionKey() {
        }
    }

}

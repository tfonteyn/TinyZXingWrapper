package com.hardbacknutter.tinyzxingwrapper;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.ResultMetadataType;

/**
 * Encapsulates the result of a barcode scan.
 */
@SuppressWarnings("unused")
public final class ScanIntentResult {

    @NonNull
    private final Intent intent;
    private final int resultCode;

    ScanIntentResult(final int resultCode,
                     @NonNull final Intent intent) {
        this.intent = intent;
        this.resultCode = resultCode;
    }

    @NonNull
    public static ScanIntentResult parseActivityResult(final int resultCode,
                                                       @NonNull final Intent intent) {
        return new ScanIntentResult(resultCode, intent);
    }

    /**
     * Success.
     *
     * @return text of barcode
     */
    @Nullable
    public String getText() {
        return intent.getStringExtra(Success.TEXT);
    }

    /**
     * Success.
     *
     * @return name of format, like "QR_CODE", "UPC_A".
     * See {@code BarcodeFormat} for more format names.
     */
    @Nullable
    public String getFormat() {
        return intent.getStringExtra(Success.FORMAT);
    }

    /**
     * Success.
     *
     * @return UPC EAN extension if applicable, or null otherwise
     */
    @Nullable
    public String getUpcEanExtension() {
        return intent.getStringExtra(Success.UPC_EAN_EXTENSION);
    }

    /**
     * @return the full result intent
     */
    @NonNull
    public Intent getIntent() {
        return intent;
    }

    public int getResultCode() {
        return resultCode;
    }

    public boolean isSuccess() {
        return resultCode == Activity.RESULT_OK;
    }

    /**
     * Failure.
     * <p>
     * The returned value will be one of the predefined REASON_x codes
     * as defined in {@link Failure}, or a generic exception message.
     * <p>
     * Do <strong>NOT</strong> rely on the generic exception message however,
     * it's only meant for logging/debug purposes!
     *
     * @return the reason code
     */
    public String getFailure() {
        return intent.getStringExtra(Failure.REASON);
    }

    @NonNull
    @Override
    public String toString() {
        return "ScanIntentResult{"
                + "resultCode=" + resultCode
                + ", intent=" + intent
                + '}';
    }

    public static final class Success {

        /**
         * The text of the barcode.
         * <p>
         * Type: String
         */
        public static final String TEXT = "RESULT_TEXT";

        /**
         * Which barcode format was found.
         * See {@link com.google.zxing.BarcodeFormat} for possible values.
         * <p>
         * Type: String
         */
        public static final String FORMAT = "RESULT_FORMAT";

        /**
         * The content of any UPC extension barcode that was also found.
         * Only applicable to {@link com.google.zxing.BarcodeFormat#UPC_A}
         * and {@link com.google.zxing.BarcodeFormat#EAN_13} formats.
         * <p>
         * Type: String
         */
        public static final String UPC_EAN_EXTENSION = "RESULT_UPC_EAN_EXTENSION";

        /**
         * Key name prefix for {@link ResultMetadataType} entries.
         */
        public static final String META_KEY_PREFIX = "META_";
    }

    public static final class Failure {

        /**
         * Key returned upon failure to scan; the value will be one of the
         * predefined reason codes below, or a generic exception message.
         * <p>
         * Do NOT rely on the generic exception message,
         * it's only meant for logging/debug purposes!
         */
        public static final String REASON = "REASON";

        /**
         * The {@link ScanIntent.ToolOptionKey#TIMEOUT_MS} was exceeded.
         * <p>
         * Type: boolean
         */
        public static final String REASON_TIMEOUT = "TIMEOUT";

        /**
         * The device triggered an inactivity event.
         * <p>
         * Type: boolean
         */
        public static final String REASON_INACTIVITY = "INACTIVITY";

        /**
         * The user refused permission to use the camera.
         * <p>
         * Type: boolean
         */
        public static final String REASON_MISSING_CAMERA_PERMISSION = "MISSING_CAMERA_PERMISSION";


        /**
         * A serialized Exception; might be present in addition to a generic "REASON"/message.
         * <p>
         * Do NOT rely on this key being present,
         * it's only meant for logging/debug purposes!
         */
        public static final String EXCEPTION = "EXCEPTION";

    }
}

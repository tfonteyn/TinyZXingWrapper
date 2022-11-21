package com.hardbacknutter.tinyzxingwrapper;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultMetadataType;

/**
 * Encapsulates the result of a barcode scan.
 */
@SuppressWarnings({"WeakerAccess", "unused", "ProhibitedExceptionCaught", "FieldNotUsedInToString"})
public final class ScanIntentResult {

    @Nullable
    private final Intent intent;
    private final int resultCode;

    private final boolean success;
    @Nullable
    private final String barcodeText;

    private ScanIntentResult(final int resultCode,
                             @Nullable final Intent intent) {
        this.intent = intent;
        this.resultCode = resultCode;

        if (resultCode == Activity.RESULT_OK && intent != null) {
            final String tmpText = intent.getStringExtra(Success.TEXT);
            success = (tmpText != null && !tmpText.isBlank());
            barcodeText = success ? tmpText : null;

        } else {
            success = false;
            barcodeText = null;
        }
    }

    @NonNull
    public static ScanIntentResult parseActivityResult(final int resultCode,
                                                       @Nullable final Intent intent) {
        return new ScanIntentResult(resultCode, intent);
    }

    /**
     * The scan is considered successful if we have a barcode text.
     *
     * @return {@code true} if {@link #getText()} will return a valid barcode.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Success.
     *
     * @return (non - blank) text of barcode
     *
     * @see #isSuccess()
     */
    @Nullable
    public String getText() {
        return barcodeText;
    }

    /**
     * Success.
     *
     * @return {@code BarcodeFormat} or {@code null} if none found
     */
    @Nullable
    public BarcodeFormat getFormat() {
        if (success) {
            try {
                //noinspection ConstantConditions
                return BarcodeFormat.valueOf(intent.getStringExtra(Success.FORMAT));
            } catch (@NonNull final IllegalArgumentException | NullPointerException ignore) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Success.
     *
     * @return (non - blank) text of UPC EAN extension or {@code null} if none found
     */
    @Nullable
    public String getUpcEanExtension() {
        if (success) {
            //noinspection ConstantConditions
            final String text = intent.getStringExtra(Success.UPC_EAN_EXTENSION);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /**
     * @return the full result intent
     */
    @Nullable
    public Intent getIntent() {
        return intent;
    }

    public int getResultCode() {
        return resultCode;
    }


    /**
     * Failure.
     * <p>
     * This method is likely going to change in a future release!
     * <p>
     * The returned value will be one of the predefined REASON_x codes
     * as defined in {@link Failure}, or a generic exception message.
     * <p>
     * Do <strong>NOT</strong> rely on the generic exception message however,
     * it's only meant for logging/debug purposes!
     *
     * @return the reason code or {@code null} if there is none
     */
    @Nullable
    public String getFailure() {
        return intent != null ? intent.getStringExtra(Failure.REASON) : null;
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

        private Success() {
        }
    }

    public static final class Failure {

        /**
         * Key returned upon failure to scan; the value will be one of the
         * predefined reason codes below.
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
         * A serialized Exception; might be present in addition to
         * or instead of a generic "REASON".
         * <p>
         * Do NOT rely on this key being present,
         * it's only meant for logging/debug purposes!
         */
        public static final String EXCEPTION = "EXCEPTION";

        private Failure() {
        }
    }
}

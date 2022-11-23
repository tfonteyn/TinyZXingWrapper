package com.hardbacknutter.tinyzxingwrapper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;

import java.util.Map;

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
            final String tmpText = intent.getStringExtra(Success.BARCODE_TEXT);
            success = (tmpText != null && !tmpText.isBlank());
            barcodeText = success ? tmpText : null;

        } else {
            success = false;
            barcodeText = null;
        }
    }

    /**
     * Decode an intent as received by {@link ScanContract#parseResult(int, Intent)}
     * into a user friendly value object {@link ScanIntentResult}.
     */
    @NonNull
    public static ScanIntentResult parseActivityResultIntent(final int resultCode,
                                                             @Nullable final Intent intent) {
        return new ScanIntentResult(resultCode, intent);
    }

    /**
     * Encode an intent to return as the Activity result.
     * <p>
     * Picks relevant parts of the {@link Result} and adds them as intent extras.
     * Will always contain {@link Success#BARCODE_TEXT} and {@link Success#BARCODE_FORMAT}.
     * Anything else depends on what is requested with {@link ScanOptions.Option#RETURN_META_DATA}.
     *
     * @param context             Current context
     * @param result              the ZXing result value object
     * @param csvWithMetadataKeys a csv String list with meta-data keys to send back
     *                            if available.
     *
     * @return the Intent
     */
    @NonNull
    public static Intent createActivityResultIntent(@SuppressWarnings("unused")
                                                    @NonNull final Context context,
                                                    @NonNull final Result result,
                                                    @Nullable final String csvWithMetadataKeys) {

        final Intent intent = new Intent()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                .putExtra(Success.BARCODE_TEXT, result.getText())
                .putExtra(Success.BARCODE_FORMAT, result.getBarcodeFormat().toString());

        final Map<ResultMetadataType, ?> metadata = result.getResultMetadata();
        if (metadata != null && csvWithMetadataKeys != null) {
            metadata.entrySet()
                    .stream()
                    // only the ones the client requested
                    .filter(entry -> csvWithMetadataKeys.contains(entry.getKey().name()))
                    // paranoia...
                    .filter(entry -> entry.getValue() != null)
                    .forEach(entry -> {
                        final ResultMetadataType type = entry.getKey();
                        switch (type) {
                            case ORIENTATION:
                            case ISSUE_NUMBER: {
                                intent.putExtra(type.name(), (int) entry.getValue());
                                break;
                            }
                            case ERROR_CORRECTION_LEVEL:
                            case SUGGESTED_PRICE:
                            case POSSIBLE_COUNTRY:
                            case UPC_EAN_EXTENSION:
                            case SYMBOLOGY_IDENTIFIER: {
                                intent.putExtra(type.name(), (String) entry.getValue());
                                break;
                            }
                            case BYTE_SEGMENTS: {
                                // Stored as a list of numbered keys each containing
                                // one segment (byte[]).
                                // e.g. the first byte segment is under key
                                // "BYTE_SEGMENTS_PREFIX_0" and so on.
                                //
                                // The amount of keys (i.e. the length) is passed in as
                                // "BYTE_SEGMENTS_PREFIX" with type int.
                                int i = 0;
                                //noinspection unchecked
                                for (final byte[] segment : (Iterable<byte[]>) entry.getValue()) {
                                    intent.putExtra(type.name() + "_" + i, segment);
                                    i++;
                                }
                                // The amount of numbered keys 0..[len-1]
                                intent.putExtra(type.name(), i - 1);
                                break;
                            }

                            case OTHER:
                            case PDF417_EXTRA_METADATA:
                            case STRUCTURED_APPEND_SEQUENCE:
                            case STRUCTURED_APPEND_PARITY:
                            default:
                                // undefined object type, can't add those.
                                break;
                        }
                    });
        }

        return intent;
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
     * If {@link #isSuccess()}, returns the text of the barcode.
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
     * If {@link #isSuccess()}, returns the format of the barcode.
     *
     * @return {@code BarcodeFormat} or {@code null} if none found
     */
    @Nullable
    public BarcodeFormat getFormat() {
        if (success) {
            try {
                //noinspection ConstantConditions
                return BarcodeFormat.valueOf(intent.getStringExtra(Success.BARCODE_FORMAT));
            } catch (@NonNull final IllegalArgumentException | NullPointerException ignore) {
                // ignore
            }
        }
        return null;
    }

    /**
     * If {@link #isSuccess()}, returns the UPC EAN extension of the barcode.
     *
     * @return (non - blank) text of UPC EAN extension or {@code null} if none found
     */
    @Nullable
    public String getUpcEanExtension() {
        if (success) {
            //noinspection ConstantConditions
            final String text = intent.getStringExtra(ResultMetadataType.UPC_EAN_EXTENSION.name());
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
        return intent != null ? intent.getStringExtra(Failure.FAILURE_REASON) : null;
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
        public static final String BARCODE_TEXT = "BARCODE_TEXT";

        /**
         * Which barcode format was found.
         * See {@link com.google.zxing.BarcodeFormat} for possible values.
         * <p>
         * Type: String
         */
        public static final String BARCODE_FORMAT = "BARCODE_FORMAT";

        private Success() {
        }
    }

    public static final class Failure {

        /**
         * Key returned upon failure to scan; the value will be one of the
         * predefined reason codes below.
         */
        public static final String FAILURE_REASON = "FAILURE_REASON";

        /**
         * The {@link CaptureActivity.Option#TIMEOUT_MS} was exceeded.
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
         * or instead of {@link #FAILURE_REASON}.
         * <p>
         * Do NOT rely on this key being present,
         * it's only meant for logging/debug purposes!
         */
        public static final String FAILURE_EXCEPTION = "FAILURE_EXCEPTION";

        private Failure() {
        }
    }
}

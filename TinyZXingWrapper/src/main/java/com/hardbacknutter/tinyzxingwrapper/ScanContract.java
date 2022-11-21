package com.hardbacknutter.tinyzxingwrapper;

import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;

import java.util.Map;

public class ScanContract
        extends ActivityResultContract<ScanContract.Input, ScanIntentResult> {


    /**
     * Create an intent to return as the Activity result.
     * <p>
     * Picks relevant parts of the {@link Result} and adds them as intent extras.
     * Once this intent is delivered, {@link #parseResult(int, Intent)}
     * will transform it into a user friendly value object {@link ScanIntentResult}.
     *
     * @param context             Current context
     * @param rawResult           the ZXing result value object
     * @param csvWithMetadataKeys a csv String list with meta-data keys to send back
     *                            if available.
     *
     * @return the Intent
     */
    @NonNull
    static Intent createResultIntent(@SuppressWarnings("unused") @NonNull final Context context,
                                     @NonNull final Result rawResult,
                                     @Nullable final String csvWithMetadataKeys) {

        final Intent intent = new Intent()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                .putExtra(ScanIntentResult.Success.TEXT, rawResult.getText())
                .putExtra(ScanIntentResult.Success.FORMAT, rawResult.getBarcodeFormat().toString());

        final Map<ResultMetadataType, ?> metadata = rawResult.getResultMetadata();
        if (metadata != null && csvWithMetadataKeys != null) {
            metadata.entrySet()
                    .stream()
                    // only the ones the client requested
                    .filter(entry -> csvWithMetadataKeys.contains(entry.getKey().name()))
                    // paranoia...
                    .filter(entry -> entry.getValue() != null)
                    .forEach(entry -> {
                        final ResultMetadataType type = entry.getKey();
                        final String key = ScanIntentResult.Success.META_KEY_PREFIX + type.name();
                        switch (type) {
                            case ORIENTATION:
                            case ISSUE_NUMBER: {
                                intent.putExtra(key, (int) entry.getValue());
                                break;
                            }
                            case ERROR_CORRECTION_LEVEL:
                            case SUGGESTED_PRICE:
                            case POSSIBLE_COUNTRY:
                            case UPC_EAN_EXTENSION:
                            case SYMBOLOGY_IDENTIFIER: {
                                intent.putExtra(key, (String) entry.getValue());
                                break;
                            }
                            case BYTE_SEGMENTS: {
                                // Stored as a list of numbered keys each containing
                                // one segment (byte[]).
                                // e.g. the first byte segment is under key
                                // "META_RESULT_BYTE_SEGMENTS_PREFIX_0" and so on.
                                //
                                // The amount of keys (i.e. the length) is passed in as
                                // "META_RESULT_BYTE_SEGMENTS_PREFIX" with type int.
                                int i = 0;
                                //noinspection unchecked
                                for (final byte[] segment : (Iterable<byte[]>) entry.getValue()) {
                                    intent.putExtra(key + "_" + i, segment);
                                    i++;
                                }
                                // The amount of numbered keys 0..[len-1]
                                intent.putExtra(key, i - 1);
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

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Input input) {
        return input.createScanIntent(context);
    }

    @Override
    @NonNull
    public ScanIntentResult parseResult(final int resultCode,
                                        @Nullable final Intent intent) {
        return ScanIntentResult.parseActivityResult(resultCode, intent);
    }

    @FunctionalInterface
    public interface Input {

        @NonNull
        Intent createScanIntent(@NonNull Context context);
    }
}

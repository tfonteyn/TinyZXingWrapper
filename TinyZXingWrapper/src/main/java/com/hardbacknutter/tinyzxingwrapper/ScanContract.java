package com.hardbacknutter.tinyzxingwrapper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ScanContract
        extends ActivityResultContract<ScanContract.Input, Optional<ScanResult>> {


    /**
     * Create an intent to return as the Activity result.
     * <p>
     * Picks relevant parts of the {@link Result} and adds them as intent extras.
     * Once this intent is delivered, {@link #parseResult(int, Intent)}
     * will transform it into a user friendly value object {@link ScanResult}.
     *
     * @param context             Current context (not used for now)
     * @param rawResult           the ZXing result value object
     * @param csvWithMetadataKeys a csv String list with meta-data keys to send back
     *                            if available.
     *
     * @return the Intent
     */
    @NonNull
    public static Intent createResultIntent(@NonNull final Context context,
                                            @NonNull final Result rawResult,
                                            @Nullable final String csvWithMetadataKeys) {

        Intent intent = new Intent()
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                .putExtra(ScanResult.Success.TEXT, rawResult.getText())
                .putExtra(ScanResult.Success.FORMAT, rawResult.getBarcodeFormat().toString());

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
                        final String key = ScanResult.Success.META_KEY_PREFIX + type.name();
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
                                for (byte[] byteSegment : (List<byte[]>) entry.getValue()) {
                                    intent.putExtra(key + "_" + i, byteSegment);
                                    i++;
                                }
                                // The amount of numbered keys 0..[len-1]
                                intent.putExtra(key, i - 1);
                                break;
                            }

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
    public Optional<ScanResult> parseResult(final int resultCode,
                                            @Nullable final Intent intent) {
        if (intent == null || resultCode != Activity.RESULT_OK) {
            return Optional.empty();
        } else {
            return Optional.of(new ScanResult(resultCode, intent));
        }
    }

    public interface Input {

        @NonNull
        Intent createScanIntent(@NonNull Context context);
    }
}

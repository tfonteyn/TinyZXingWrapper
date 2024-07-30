package com.hardbacknutter.tinyzxingwrapper;

import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A simple {@link ActivityResultContract}.
 *
 * @see ScanOptions
 * @see ScanIntentResult
 */
public class ScanContract
        extends ActivityResultContract<ScanOptions, ScanIntentResult> {

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final ScanOptions options) {
        return options.build(context);
    }

    @Override
    @NonNull
    public ScanIntentResult parseResult(final int resultCode,
                                        @Nullable final Intent intent) {
        return ScanIntentResult.parseActivityResultIntent(resultCode, intent);
    }
}

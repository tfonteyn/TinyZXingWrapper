package com.hardbacknutter.tinyzxingwrapper;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.tinyzxingwrapper.R;
import com.hardbacknutter.tinyzxingwrapper.ScanIntent;

public class CaptureViewModel
        extends ViewModel {

    private long timeOutInMs;
    private long inactivityTimeOutInMs;

    private String withMetaData;

    @Nullable
    private String statusText;

    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {
        if (args != null) {
            statusText = args.getString(ScanIntent.ToolOptionKey.PROMPT_MESSAGE);
            timeOutInMs = args.getLong(ScanIntent.ToolOptionKey.TIMEOUT_MS, 0L);
            inactivityTimeOutInMs = args.getLong(ScanIntent.ToolOptionKey.INACTIVITY_TIMEOUT_MS, 0L);
            withMetaData = args.getString(ScanIntent.OptionKey.RETURN_META_DATA, "");
        }

        if (statusText == null) {
            statusText = context.getString(R.string.tzw_status_text);
        }
    }

    public long getTimeOutInMs() {
        return timeOutInMs;
    }

    public long getInactivityTimeOutInMs() {
        return inactivityTimeOutInMs;
    }

    public String getWithMetaData() {
        return withMetaData;
    }

    @Nullable
    public String getStatusText() {
        return statusText;
    }
}

/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hardbacknutter.tinyzxingwrapper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.concurrent.atomic.AtomicBoolean;

final class InactivityTimer
        implements LifecycleEventObserver {

    /**
     * 3 minutes.
     */
    private static final long INACTIVITY_DELAY_MS = 180_000L;

    @NonNull
    private final Context context;
    @NonNull
    private final Handler handler;
    @NonNull
    private final Runnable callback;
    private boolean startTimer;
    private final AtomicBoolean registered = new AtomicBoolean();
    private long inactivityDelayMs = INACTIVITY_DELAY_MS;
    @NonNull
    private final BroadcastReceiver powerStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(@NonNull final Context context,
                              @NonNull final Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {

                final boolean lowBattery;
                if (Build.VERSION.SDK_INT >= 28) {
                    // check for actually being on low-battery.
                    lowBattery = intent.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false);
                } else {
                    // just check if we're on battery or not.
                    lowBattery = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) <= 0;
                }
                // post on handler to run in main thread
                if (registered.get()) {
                    handler.post(() -> startTimer(lowBattery));
                }
            }
        }
    };

    InactivityTimer(@NonNull final Context context,
                    @NonNull final Runnable callback) {
        this.context = context;
        this.callback = callback;
        handler = new Handler();
    }

    void setInactivityDelayMs(final long inactivityDelayMs) {
        this.inactivityDelayMs = inactivityDelayMs;
    }

    /**
     * Reset the timer, and trigger the callback.
     */
    private void reset() {
        handler.removeCallbacksAndMessages(null);
        if (startTimer) {
            handler.postDelayed(callback, inactivityDelayMs);
        }
    }

    private void startTimer(final boolean startTimer) {
        this.startTimer = startTimer;
        reset();
    }

    @Override
    public void onStateChanged(@NonNull final LifecycleOwner source,
                               @NonNull final Lifecycle.Event event) {
        //noinspection EnumSwitchStatementWhichMissesCases,SwitchStatementWithoutDefaultBranch
        switch (event) {
            case ON_RESUME:
                synchronized (registered) {
                    if (!registered.get()) {
                        context.registerReceiver(powerStatusReceiver,
                                                 new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                        registered.set(true);
                    }
                }
                reset();
                break;

            case ON_PAUSE:
            case ON_DESTROY:
                handler.removeCallbacksAndMessages(null);
                synchronized (registered) {
                    if (registered.get()) {
                        context.unregisterReceiver(powerStatusReceiver);
                        registered.set(false);
                    }
                }
                break;
        }
    }
}

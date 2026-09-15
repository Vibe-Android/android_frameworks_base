/*
 * Copyright (C) 2026 The Vibe Project
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

package com.android.systemui.statusbar.vibe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.systemui.Dependency;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.vibe.VibeSettingsConstants;

import org.lineageos.internal.statusbar.LineageStatusBarItem;

import java.util.ArrayList;
import java.util.Locale;

public class VibeNetworkTrafficView extends TextView implements
        DarkIconDispatcher.DarkReceiver,
        LineageStatusBarItem.DarkReceiver,
        LineageStatusBarItem.VisibilityReceiver {

    private static final long UPDATE_INTERVAL_MS = 1000L;

    private boolean mAttached;
    private boolean mIsEnabled;
    private int mDisplayMode = VibeSettingsConstants.DEFAULT_STATUSBAR_NETWORK_TRAFFIC_MODE;

    private boolean mScreenOn = true;
    private boolean mStatusBarVisible = true;
    private boolean mIsTrafficRunning = false;

    private long mLastTxBytes;
    private long mLastRxBytes;
    private long mLastUpdateTime;

    private int mCurrentUserId;

    private final Handler mTrafficHandler = new Handler(Looper.getMainLooper());
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final UserTracker mUserTracker;

    private final ContentObserver mSettingsObserver = new ContentObserver(mTrafficHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    };

    private final UserTracker.Callback mUserChangedCallback = new UserTracker.Callback() {
        @Override
        public void onUserChanged(int newUser, @NonNull Context userContext) {
            mCurrentUserId = newUser;
            updateSettings();
        }
    };

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenOn = false;
                evaluateTrafficState();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenOn = true;
                evaluateTrafficState();
            }
        }
    };

    private final Runnable mTrafficRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsTrafficRunning) {
                return;
            }

            final long now = SystemClock.elapsedRealtime();
            final long timeDelta = now - mLastUpdateTime;
            if (timeDelta <= 0) {
                mTrafficHandler.postDelayed(this, UPDATE_INTERVAL_MS);
                return;
            }

            final long currentTxBytes = TrafficStats.getTotalTxBytes();
            final long currentRxBytes = TrafficStats.getTotalRxBytes();

            final long txDiff = (currentTxBytes >= mLastTxBytes) ? (currentTxBytes - mLastTxBytes) : 0;
            final long rxDiff = (currentRxBytes >= mLastRxBytes) ? (currentRxBytes - mLastRxBytes) : 0;

            final long uploadSpeed = (txDiff * 1000) / timeDelta;
            final long downloadSpeed = (rxDiff * 1000) / timeDelta;

            mLastTxBytes = currentTxBytes;
            mLastRxBytes = currentRxBytes;
            mLastUpdateTime = now;

            updateText(uploadSpeed, downloadSpeed);

            mTrafficHandler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    public VibeNetworkTrafficView(Context context) {
        this(context, null);
    }

    public VibeNetworkTrafficView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VibeNetworkTrafficView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mBroadcastDispatcher = Dependency.get(BroadcastDispatcher.class);
        mUserTracker = Dependency.get(UserTracker.class);
        mCurrentUserId = mUserTracker.getUserId();

        setMaxLines(2);
        setLineSpacing(0f, 0.85f);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        setVisibility(View.GONE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAttached) return;
        mAttached = true;

        if (getParent() instanceof LineageStatusBarItem.Manager) {
            ((LineageStatusBarItem.Manager) getParent()).addDarkReceiver(this);
            ((LineageStatusBarItem.Manager) getParent()).addVisibilityReceiver(this);
        } else {
            Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);
        }

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(VibeSettingsConstants.KEY_STATUSBAR_NETWORK_TRAFFIC),
                false, mSettingsObserver, UserHandle.USER_ALL);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(VibeSettingsConstants.KEY_STATUSBAR_NETWORK_TRAFFIC_MODE),
                false, mSettingsObserver, UserHandle.USER_ALL);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        mBroadcastDispatcher.registerReceiver(mScreenReceiver, filter);

        mUserTracker.addCallback(mUserChangedCallback, mContext.getMainExecutor());
        mCurrentUserId = mUserTracker.getUserId();

        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!mAttached) return;
        mAttached = false;

        stopTrafficUpdates();

        if (getParent() instanceof LineageStatusBarItem.Manager) {
            ((LineageStatusBarItem.Manager) getParent()).removeDarkReceiver(this);
            ((LineageStatusBarItem.Manager) getParent()).removeVisibilityReceiver(this);
        } else {
            Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(this);
        }

        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        mBroadcastDispatcher.unregisterReceiver(mScreenReceiver);
        mUserTracker.removeCallback(mUserChangedCallback);
    }

    private void updateSettings() {
        mIsEnabled = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                VibeSettingsConstants.KEY_STATUSBAR_NETWORK_TRAFFIC,
                VibeSettingsConstants.DEFAULT_STATUSBAR_NETWORK_TRAFFIC,
                mCurrentUserId) == 1;

        mDisplayMode = Settings.System.getIntForUser(
                mContext.getContentResolver(),
                VibeSettingsConstants.KEY_STATUSBAR_NETWORK_TRAFFIC_MODE,
                VibeSettingsConstants.DEFAULT_STATUSBAR_NETWORK_TRAFFIC_MODE,
                mCurrentUserId);

        evaluateTrafficState();
    }

    private void evaluateTrafficState() {
        final boolean shouldRun = mIsEnabled && mScreenOn && mStatusBarVisible;
        if (shouldRun) {
            if (!mIsTrafficRunning) {
                resetSamplingBaseline();
                startTrafficUpdates();
            }
            if (getVisibility() != View.VISIBLE) {
                setVisibility(View.VISIBLE);
            }
        } else {
            stopTrafficUpdates();
            if (getVisibility() != View.GONE) {
                setVisibility(View.GONE);
            }
        }
    }

    private void resetSamplingBaseline() {
        mLastTxBytes = TrafficStats.getTotalTxBytes();
        mLastRxBytes = TrafficStats.getTotalRxBytes();
        mLastUpdateTime = SystemClock.elapsedRealtime();
    }

    private void startTrafficUpdates() {
        mIsTrafficRunning = true;
        mTrafficHandler.removeCallbacks(mTrafficRunnable);
        mTrafficHandler.postDelayed(mTrafficRunnable, UPDATE_INTERVAL_MS);
    }

    private void stopTrafficUpdates() {
        mIsTrafficRunning = false;
        mTrafficHandler.removeCallbacks(mTrafficRunnable);
    }

    private void updateText(long uploadSpeed, long downloadSpeed) {
        final String text;
        switch (mDisplayMode) {
            case VibeSettingsConstants.NETWORK_TRAFFIC_MODE_DOWNLOAD:
                text = "▼ " + formatSpeed(downloadSpeed);
                break;
            case VibeSettingsConstants.NETWORK_TRAFFIC_MODE_UPLOAD:
                text = "▲ " + formatSpeed(uploadSpeed);
                break;
            case VibeSettingsConstants.NETWORK_TRAFFIC_MODE_COMBINED:
            default:
                text = "▲ " + formatSpeed(uploadSpeed) + "\n▼ " + formatSpeed(downloadSpeed);
                break;
        }
        setText(text);
    }

    private static String formatSpeed(long bytesPerSec) {
        if (bytesPerSec < 1024L) {
            return bytesPerSec + " B/s";
        } else if (bytesPerSec < 1024L * 1024L) {
            final float kb = bytesPerSec / 1024.0f;
            return String.format(Locale.getDefault(), (kb >= 100f ? "%.0f KB/s" : "%.1f KB/s"), kb);
        } else {
            final float mb = bytesPerSec / (1024.0f * 1024.0f);
            return String.format(Locale.getDefault(), (mb >= 100f ? "%.0f MB/s" : "%.1f MB/s"), mb);
        }
    }

    // LineageStatusBarItem.DarkReceiver & DarkIconDispatcher.DarkReceiver
    @Override
    public void onDarkChanged(ArrayList<Rect> areas, float darkIntensity, int tint) {
        final int textColor = DarkIconDispatcher.getTint(areas, this, tint);
        setTextColor(textColor);
    }

    @Override
    public void setFillColors(int darkColor, int lightColor) {
        // Dual-tone fill colors; onDarkChanged handles primary text color tint
    }

    // LineageStatusBarItem.VisibilityReceiver
    @Override
    public void onVisibilityChanged(boolean isVisible) {
        mStatusBarVisible = isVisible;
        evaluateTrafficState();
    }
}

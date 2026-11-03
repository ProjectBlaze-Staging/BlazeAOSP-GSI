/*
 * Copyright (C) 2026 The BlazeAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.quickstep.util;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.RemoteAnimationTarget;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Coordinates the "App-to-Dynamic-Island Swallow" flight animation when a closing app is
 * actively playing media/audio/video in the background.
 */
public final class DynamicIslandFlightHelper {

    private static final String KEY_DYNAMIC_ISLAND_SECURE = "dynamic_island_enabled";
    private static final String KEY_DYNAMIC_ISLAND_LEGACY = "custom_D_bar";
    private static final String KEY_DYNAMIC_ISLAND_MEDIA = "dynamic_island_media_controls";
    private static final String KEY_ACTIVE_MEDIA_PKG = "blaze_island_active_media_pkg";
    private static final String KEY_MEDIA_PLAYING = "blaze_island_media_playing";
    private static final String KEY_SWALLOW_SEQ = "blaze_island_swallow_seq";

    private static final Set<String> KNOWN_MEDIA_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "app.revanced.android.youtube",
            "app.revanced.android.apps.youtube.music",
            "app.rvx.android.youtube",
            "app.rvx.android.apps.youtube.music",
            "org.schabi.newpipe",
            "com.spotify.music",
            "com.apple.android.music",
            "deezer.android.app",
            "com.aspiro.tidal",
            "com.soundcloud.android",
            "tv.twitch.android.app",
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "org.videolan.vlc",
            "com.mxtech.videoplayer.ad",
            "com.mxtech.videoplayer.pro",
            "com.maxmpz.audioplayer",
            "in.krosbits.musicolet",
            "code.name.monkey.retromusic",
            "com.zionhuang.music",
            "com.fast4x.rimusic",
            "it.fast4x.rimusic",
            "org.lineageos.eleven",
            "org.lineageos.twelve",
            "com.android.chrome",
            "com.brave.browser",
            "org.mozilla.firefox",
            "com.sec.android.app.sbrowser",
            "org.lineageos.jelly"
    ));

    private DynamicIslandFlightHelper() {}

    /**
     * Returns true when Dynamic Island is enabled and the closing task is actively playing
     * music/audio/video so its closing window should fly into the Dynamic Island pill.
     */
    public static boolean shouldFlyToDynamicIsland(
            Context context,
            @Nullable RemoteAnimationTarget runningTaskTarget,
            @Nullable String fallbackPackageName) {
        if (context == null || !isDynamicIslandMediaEnabled(context)) {
            return false;
        }
        String pkg = resolvePackageName(runningTaskTarget, fallbackPackageName);
        if (TextUtils.isEmpty(pkg)) {
            return false;
        }

        ContentResolver cr = context.getContentResolver();
        try {
            String activeIslandPkg = Settings.Global.getString(cr, KEY_ACTIVE_MEDIA_PKG);
            int isIslandPlaying = Settings.Global.getInt(cr, KEY_MEDIA_PLAYING, 0);
            if (isIslandPlaying == 1 && pkg.equals(activeIslandPkg)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            MediaSessionManager msm = context.getSystemService(MediaSessionManager.class);
            if (msm != null) {
                List<MediaController> controllers = msm.getActiveSessions(null);
                if (controllers != null) {
                    for (MediaController controller : controllers) {
                        if (controller != null && pkg.equals(controller.getPackageName())) {
                            PlaybackState state = controller.getPlaybackState();
                            if (state != null && isActivePlaybackState(state.getState())) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            AudioManager am = context.getSystemService(AudioManager.class);
            if (am != null && am.isMusicActive() && isMediaCapablePackage(context, pkg)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    /**
     * Returns the target capsule bounds of the Dynamic Island pill in Launcher window coordinates.
     */
    public static RectF getDynamicIslandTargetRect(Context context, DeviceProfile dp) {
        float density = context.getResources().getDisplayMetrics().density;
        float screenWidth = dp.getDeviceProperties().getWidthPx();
        float pillWidth = 148f * density;
        float pillHeight = 32f * density;
        float top = 8f * density;
        float left = (screenWidth - pillWidth) / 2f;
        return new RectF(left, top, left + pillWidth, top + pillHeight);
    }

    /**
     * Signals SystemUI's Dynamic Island to switch to the Media chip and play the elastic
     * "Swallow & Swell" bounce animation as the closing app window enters the pill.
     */
    public static void triggerDynamicIslandSwallow(Context context) {
        if (context == null) return;
        try {
            ContentResolver cr = context.getContentResolver();
            int nextSeq = Settings.Global.getInt(cr, KEY_SWALLOW_SEQ, 0) + 1;
            Settings.Global.putInt(cr, KEY_SWALLOW_SEQ, nextSeq);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isDynamicIslandMediaEnabled(Context context) {
        ContentResolver cr = context.getContentResolver();
        boolean masterEnabled = true;
        try {
            int secureVal = Settings.Secure.getInt(cr, KEY_DYNAMIC_ISLAND_SECURE, -1);
            if (secureVal != -1) {
                masterEnabled = secureVal == 1;
            } else {
                masterEnabled = Settings.System.getInt(cr, KEY_DYNAMIC_ISLAND_LEGACY, 1) == 1;
            }
            if (!masterEnabled) return false;
            return Settings.Secure.getInt(cr, KEY_DYNAMIC_ISLAND_MEDIA, 1) == 1;
        } catch (Throwable ignored) {
            return true;
        }
    }

    @Nullable
    private static String resolvePackageName(
            @Nullable RemoteAnimationTarget target, @Nullable String fallback) {
        if (target != null && target.taskInfo != null) {
            ActivityManager.RunningTaskInfo ti = target.taskInfo;
            ComponentName top = ti.topActivity;
            if (top != null && !TextUtils.isEmpty(top.getPackageName())) {
                return top.getPackageName();
            }
            ComponentName base = ti.baseActivity;
            if (base != null && !TextUtils.isEmpty(base.getPackageName())) {
                return base.getPackageName();
            }
            if (ti.baseIntent != null && ti.baseIntent.getComponent() != null) {
                return ti.baseIntent.getComponent().getPackageName();
            }
        }
        return fallback;
    }

    private static boolean isActivePlaybackState(int state) {
        return state == PlaybackState.STATE_PLAYING
                || state == PlaybackState.STATE_BUFFERING
                || state == PlaybackState.STATE_FAST_FORWARDING
                || state == PlaybackState.STATE_REWINDING
                || state == PlaybackState.STATE_SKIPPING_TO_NEXT
                || state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS;
    }

    private static boolean isMediaCapablePackage(Context context, String pkg) {
        if (KNOWN_MEDIA_PACKAGES.contains(pkg)) {
            return true;
        }
        String lower = pkg.toLowerCase();
        if (lower.contains("youtube") || lower.contains("music") || lower.contains("spotify")
                || lower.contains("video") || lower.contains("player") || lower.contains("media")
                || lower.contains("podcast") || lower.contains("radio")) {
            return true;
        }
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if (ai != null && (ai.category == ApplicationInfo.CATEGORY_AUDIO
                    || ai.category == ApplicationInfo.CATEGORY_VIDEO)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}

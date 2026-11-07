/*
 * SPDX-FileCopyrightText: 2026 The BlazeAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.util;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

/**
 * 120Hz spring physics & haptic feedback helper for OOBE.
 * Uses analytical underdamped second-order spring interpolators on RenderThread.
 */
public final class OobeSpringHelper {

    private OobeSpringHelper() {
    }

    /**
     * Analytical underdamped/nearly-critically-damped spring interpolator:
     * zeta = 0.78, omega0 = 18 rad/s -> silky tactile snap.
     */
    public static final TimeInterpolator LAMINAR_SPRING_INTERPOLATOR = input -> {
        if (input <= 0f) return 0f;
        if (input >= 1f) return 1f;
        final double zeta = 0.78;
        final double omega0 = 18.0;
        final double omegaD = omega0 * Math.sqrt(1.0 - zeta * zeta);
        final double env = Math.exp(-zeta * omega0 * input);
        final double osc = Math.cos(omegaD * input)
                + ((zeta * omega0) / omegaD) * Math.sin(omegaD * input);
        return (float) (1.0 - env * osc);
    };

    /**
     * Attaches default 120Hz tactile press-scale spring physics (0.96f) to a view.
     */
    public static void attachPressSpring(View view) {
        attachTouchSpring(view, 0.96f);
    }

    /**
     * Attaches 120Hz tactile press-scale spring physics to any interactive card or button.
     */
    public static void attachTouchSpring(View view, float pressedScale) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .scaleX(pressedScale)
                            .scaleY(pressedScale)
                            .setDuration(150)
                            .setInterpolator(LAMINAR_SPRING_INTERPOLATOR)
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(360)
                            .setInterpolator(LAMINAR_SPRING_INTERPOLATOR)
                            .start();
                    break;
            }
            return false;
        });
    }

    public static void vibrateTick(Context context) {
        if (context == null) return;
        try {
            Vibrator vib = context.getSystemService(Vibrator.class);
            if (vib != null && vib.hasVibrator()) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            }
        } catch (Throwable ignored) {
        }
    }

    public static void vibrateSuccess(Context context) {
        if (context == null) return;
        try {
            Vibrator vib = context.getSystemService(Vibrator.class);
            if (vib != null && vib.hasVibrator()) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Plays a staggered ColorOS 17 / One UI 9 spatial entrance cascade across a list of views.
     */
    public static void playEntranceCascade(View... views) {
        if (views == null) return;
        float offsetPx = 42f;
        for (int i = 0; i < views.length; i++) {
            View v = views[i];
            if (v == null || v.getVisibility() != View.VISIBLE) continue;
            float density = v.getResources().getDisplayMetrics().density;
            v.setAlpha(0f);
            v.setTranslationY(offsetPx * density);
            v.setScaleX(0.95f);
            v.setScaleY(0.95f);
            v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(55L * i)
                    .setDuration(520)
                    .setInterpolator(LAMINAR_SPRING_INTERPOLATOR)
                    .start();
        }
    }
}

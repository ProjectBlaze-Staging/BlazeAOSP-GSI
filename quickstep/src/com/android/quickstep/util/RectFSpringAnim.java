/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.util;


import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.animation.Animator;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation.OnAnimationEndListener;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.DynamicResource;
import com.android.quickstep.SurfaceReleaseCheck;
import com.android.systemui.plugins.ResourceProvider;

import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.List;


/**
 * Applies spring forces to animate from a starting rect to a target rect,
 * while providing update callbacks to the caller.
 */
public class RectFSpringAnim extends SurfaceReleaseCheck {

    private static final FloatPropertyCompat<RectFSpringAnim> RECT_CENTER_X =
            new FloatPropertyCompat<RectFSpringAnim>("rectCenterXSpring") {
                @Override
                public float getValue(RectFSpringAnim anim) {
                    return anim.mCurrentCenterX;
                }

                @Override
                public void setValue(RectFSpringAnim anim, float currentCenterX) {
                    anim.mCurrentCenterX = currentCenterX;
                    anim.onUpdate();
                }
            };

    private static final FloatPropertyCompat<RectFSpringAnim> RECT_Y =
            new FloatPropertyCompat<RectFSpringAnim>("rectYSpring") {
                @Override
                public float getValue(RectFSpringAnim anim) {
                    return anim.mCurrentY;
                }

                @Override
                public void setValue(RectFSpringAnim anim, float y) {
                    anim.mCurrentY = y;
                    anim.onUpdate();
                }
            };

    private static final FloatPropertyCompat<RectFSpringAnim> RECT_SCALE_PROGRESS =
            new FloatPropertyCompat<RectFSpringAnim>("rectScaleProgress") {
                @Override
                public float getValue(RectFSpringAnim object) {
                    return object.mCurrentScaleProgress;
                }

                @Override
                public void setValue(RectFSpringAnim object, float value) {
                    object.mCurrentScaleProgress = value;
                    object.onUpdate();
                }
            };

    private final RectF mStartRect;
    private final RectF mTargetRect;
    private final RectF mCurrentRect = new RectF();
    private final List<OnUpdateListener> mOnUpdateListeners = new ArrayList<>();
    private final List<Animator.AnimatorListener> mAnimatorListeners = new ArrayList<>();

    private float mCurrentCenterX;
    private float mCurrentY;
    // If true, tracking the bottom of the rects, else tracking the top.
    private float mCurrentScaleProgress;
    private SpringAnimation mRectXSpring;
    private SpringAnimation mRectYSpring;
    private SpringAnimation mRectScaleAnim;
    private boolean mAnimsStarted;
    private boolean mPaused;
    private boolean mRectXAnimEnded;
    private boolean mRectYAnimEnded;
    private boolean mRectScaleAnimEnded;

    /**
     * Indicates which part of the start & target rects we are interpolating between.
     */
    public static final int TRACKING_TOP = 0;
    public static final int TRACKING_CENTER = 1;
    public static final int TRACKING_BOTTOM = 2;

    @Retention(SOURCE)
    @IntDef(value = {TRACKING_TOP,
                    TRACKING_CENTER,
                    TRACKING_BOTTOM})
    public @interface Tracking{}

    @Tracking
    public final int mTracking;
    protected final float mStiffnessX;
    protected final float mStiffnessY;
    protected final float mDampingX;
    protected final float mDampingY;
    protected final float mRectStiffness;
    private final float mRectDamping;
    private final boolean mCos;
    private long mCosStartTime;

    public RectFSpringAnim(SpringConfig config) {
        mStartRect = config.startRect;
        mTargetRect = config.targetRect;
        mCurrentCenterX = mStartRect.centerX();

        setCanRelease(true);

        mTracking = config.tracking;
        mStiffnessX = config.stiffnessX;
        mStiffnessY = config.stiffnessY;
        mDampingX = config.dampingX;
        mDampingY = config.dampingY;
        mRectStiffness = config.rectStiffness;
        mRectDamping = config.rectDamping;
        mCos = config.cos;

        mCurrentY = getTrackedYFromRect(mStartRect);
    }

    public RectF getTargetRect() {
        return mTargetRect;
    }

    private float getTrackedYFromRect(RectF rect) {
        switch (mTracking) {
            case TRACKING_TOP:
                return rect.top;
            case TRACKING_BOTTOM:
                return rect.bottom;
            case TRACKING_CENTER:
            default:
                return rect.centerY();
        }
    }

    public void onTargetPositionChanged() {
        if (isEnded() || mPaused) {
            return;
        }

        if (mRectXSpring != null) {
            mRectXSpring.animateToFinalPosition(mTargetRect.centerX());
            mRectXAnimEnded = false;
        }

        if (mRectYSpring != null) {
            switch (mTracking) {
                case TRACKING_TOP:
                    mRectYSpring.animateToFinalPosition(mTargetRect.top);
                    break;
                case TRACKING_BOTTOM:
                    mRectYSpring.animateToFinalPosition(mTargetRect.bottom);
                    break;
                case TRACKING_CENTER:
                    mRectYSpring.animateToFinalPosition(mTargetRect.centerY());
                    break;
            }
            mRectYAnimEnded = false;
        }
    }

    public void addOnUpdateListener(OnUpdateListener onUpdateListener) {
        mOnUpdateListeners.add(onUpdateListener);
    }

    public void addAnimatorListener(Animator.AnimatorListener animatorListener) {
        mAnimatorListeners.add(animatorListener);
    }

    /**
     * Starts the fling/spring animation.
     * @param context The activity context.
     * @param velocityPxPerMs Velocity of swipe in px/ms.
     */
    public void start(Context context, @Nullable DeviceProfile profile, PointF velocityPxPerMs) {
        startSprings(context, velocityPxPerMs);
        for (Animator.AnimatorListener animatorListener : mAnimatorListeners) {
            animatorListener.onAnimationStart(null);
        }
    }

    /**
     * Holds the rect where it is, without ending the animation, so it can follow a finger.
     */
    public void pause() {
        if (!mAnimsStarted || mPaused || isEnded()) {
            return;
        }
        mPaused = true;
        mRectXSpring.cancel();
        mRectYSpring.cancel();
        mRectScaleAnim.cancel();
    }

    /**
     * Moves a paused rect by the given offset. Dragging down grows it back towards its start size.
     */
    public void dragBy(float dx, float dy) {
        if (!mPaused) {
            return;
        }
        mCurrentCenterX += dx;
        mCurrentY += dy;
        mCurrentScaleProgress = Utilities.boundToRange(
                mCurrentScaleProgress - dy / mStartRect.height(), 0f, 1f);
        onUpdate();
    }

    /**
     * Springs a paused rect on to its target, starting at the given velocity.
     */
    public void resume(Context context, PointF velocityPxPerMs) {
        if (!mPaused) {
            return;
        }
        mPaused = false;
        mRectXAnimEnded = false;
        mRectYAnimEnded = false;
        mRectScaleAnimEnded = false;
        startSprings(context, velocityPxPerMs);
    }

    public boolean isRunning() {
        return mAnimsStarted && !isEnded();
    }

    /**
     * How far the window has given way to its icon. ColorOS 17 starts the swap a couple of frames
     * after the finger lifts and has it half done at 75 ms and all but done by 175 ms.
     */
    public float getCosFadeProgress() {
        if (mCosStartTime == 0) {
            return 0f;
        }
        float t = Math.max(0, SystemClock.uptimeMillis() - mCosStartTime - 17) / 1000f;
        float wt = 30f * t;
        return 1 - (1 + wt) * (float) Math.exp(-wt);
    }

    public boolean isCos() {
        return mCos;
    }

    public RectF getCurrentRect() {
        return mCurrentRect;
    }

    private void startSprings(Context context, PointF velocityPxPerMs) {
        // Only tell caller that we ended if both x and y animations have ended.
        OnAnimationEndListener onXEndListener = ((animation, canceled, centerX, velocityX) -> {
            if (mPaused) return;
            mRectXAnimEnded = true;
            maybeOnEnd();
        });
        OnAnimationEndListener onYEndListener = ((animation, canceled, centerY, velocityY) -> {
            if (mPaused) return;
            mRectYAnimEnded = true;
            maybeOnEnd();
        });

        float xVelocityPxPerS = velocityPxPerMs.x * 1000;
        float yVelocityPxPerS = velocityPxPerMs.y * 1000;
        float startX = mCurrentCenterX;
        float endX = mTargetRect.centerX();
        float startY = mCurrentY;
        float endY = getTrackedYFromRect(mTargetRect);
        float minVisibleChange = Math.abs(1f / mStartRect.height());

        ResourceProvider rp = DynamicResource.provider(context);
        long minVelocityXPxPerS = rp.getInt(R.dimen.swipe_up_min_velocity_x_px_per_s);
        long maxVelocityXPxPerS = rp.getInt(R.dimen.swipe_up_max_velocity_x_px_per_s);
        long minVelocityYPxPerS = rp.getInt(R.dimen.swipe_up_min_velocity_y_px_per_s);
        long maxVelocityYPxPerS = rp.getInt(R.dimen.swipe_up_max_velocity_y_px_per_s);
        float fallOffFactor = rp.getFloat(R.dimen.swipe_up_max_velocity_fall_off_factor);

        // We want the actual initial velocity to never dip below the minimum, and to taper off
        // once it's above the soft cap so that we can prevent the window from flying off
        // screen, while maintaining a natural feel.
        xVelocityPxPerS = adjustVelocity(
                xVelocityPxPerS, minVelocityXPxPerS, maxVelocityXPxPerS, fallOffFactor);
        yVelocityPxPerS = adjustVelocity(
                yVelocityPxPerS, minVelocityYPxPerS, maxVelocityYPxPerS, fallOffFactor);

        if (mCos) {
            if ((endX - startX) * xVelocityPxPerS < 0) xVelocityPxPerS *= 0.25f;
            if ((endY - startY) * yVelocityPxPerS < 0) yVelocityPxPerS *= 0.25f;
        }

        mRectXSpring =
                new SpringAnimation(this, RECT_CENTER_X)
                        .setSpring(
                                new SpringForce(endX)
                                        .setStiffness(mStiffnessX)
                                        .setDampingRatio(mDampingX)
                        ).setStartValue(startX)
                        .setStartVelocity(xVelocityPxPerS)
                        .addEndListener(onXEndListener);

        mRectYSpring =
                new SpringAnimation(this, RECT_Y)
                        .setSpring(
                                new SpringForce(endY)
                                        .setStiffness(mStiffnessY)
                                        .setDampingRatio(mDampingY)
                        )
                        .setStartValue(startY)
                        .setStartVelocity(yVelocityPxPerS)
                        .addEndListener(onYEndListener);

        float scaleVelocity = velocityPxPerMs.y * minVisibleChange;
        if (mCos) {
            scaleVelocity = Utilities.boundToRange(
                    -velocityPxPerMs.y * 1000 / mStartRect.height(), 0f, 8f);
            mCosStartTime = SystemClock.uptimeMillis();
        }
        float dampingZ = mRectDamping >= 0 ? mRectDamping
                : rp.getFloat(R.dimen.swipe_up_rect_scale_damping_ratio_v2);
        mRectScaleAnim =
                new SpringAnimation(this, RECT_SCALE_PROGRESS)
                        .setSpring(
                                new SpringForce(1f)
                                        .setStiffness(mRectStiffness)
                                        .setDampingRatio(dampingZ))
                        .setStartVelocity(scaleVelocity)
                        // Allow the size spring to pass its target for landing bounce when underdamped.
                        .setMaxValue((mCos || dampingZ < 0.99f) ? 1.28f : 1f)
                        .setMinimumVisibleChange(minVisibleChange)
                        .addEndListener((animation, canceled, value, velocity) -> {
                            if (mPaused) return;
                            mRectScaleAnimEnded = true;
                            maybeOnEnd();
                        });

        setCanRelease(false);
        mAnimsStarted = true;

        mRectXSpring.start();
        mRectYSpring.start();

        mRectScaleAnim.start();
    }

    public void end() {
        mPaused = false;
        if (mAnimsStarted) {
            if (mRectXSpring.canSkipToEnd()) {
                mRectXSpring.skipToEnd();
            }
            if (mRectYSpring.canSkipToEnd()) {
                mRectYSpring.skipToEnd();
            }
            if (mRectScaleAnim.canSkipToEnd()) {
                mRectScaleAnim.skipToEnd();
            }
            mCurrentScaleProgress = mRectScaleAnim.getSpring().getFinalPosition();

            // Ensures that we end the animation with the final values.
            mRectXAnimEnded = false;
            mRectYAnimEnded = false;
            mRectScaleAnimEnded = false;
            onUpdate();
        }

        mRectXAnimEnded = true;
        mRectYAnimEnded = true;
        mRectScaleAnimEnded = true;
        maybeOnEnd();
    }

    private boolean isEnded() {
        return mRectXAnimEnded && mRectYAnimEnded && mRectScaleAnimEnded;
    }

    private void onUpdate() {
        if (isEnded()) {
            // Prevent further updates from being called. This can happen between callbacks for
            // ending the x/y/scale animations.
            return;
        }

        if (!mOnUpdateListeners.isEmpty()) {
            float currentWidth = Utilities.mapRange(mCurrentScaleProgress, mStartRect.width(),
                    mTargetRect.width());
            float currentHeight = Utilities.mapRange(mCurrentScaleProgress, mStartRect.height(),
                    mTargetRect.height());
            if (mCos && mStartRect.width() > 0 && mTargetRect.width() > 0) {
                currentHeight = currentWidth * Utilities.mapRange(mCurrentScaleProgress,
                        mStartRect.height() / mStartRect.width(),
                        mTargetRect.height() / mTargetRect.width());
            }
            switch (mTracking) {
                case TRACKING_TOP:
                    mCurrentRect.set(mCurrentCenterX - currentWidth / 2,
                            mCurrentY,
                            mCurrentCenterX + currentWidth / 2,
                            mCurrentY + currentHeight);
                    break;
                case TRACKING_BOTTOM:
                    mCurrentRect.set(mCurrentCenterX - currentWidth / 2,
                            mCurrentY - currentHeight,
                            mCurrentCenterX + currentWidth / 2,
                            mCurrentY);
                    break;
                case TRACKING_CENTER:
                    mCurrentRect.set(mCurrentCenterX - currentWidth / 2,
                            mCurrentY - currentHeight / 2,
                            mCurrentCenterX + currentWidth / 2,
                            mCurrentY + currentHeight / 2);
                    break;
            }
            for (OnUpdateListener onUpdateListener : mOnUpdateListeners) {
                // Only the rect follows the bounce past the target; progress-driven effects
                // (alpha, corner radius, home play fraction) stay within [0, 1].
                onUpdateListener.onUpdate(mCurrentRect, Math.min(1f, mCurrentScaleProgress));
            }
        }
    }

    private void maybeOnEnd() {
        if (mAnimsStarted && isEnded()) {
            mAnimsStarted = false;
            setCanRelease(true);
            for (Animator.AnimatorListener animatorListener : mAnimatorListeners) {
                animatorListener.onAnimationEnd(null);
            }
        }
    }

    public void cancel() {
        if (mAnimsStarted) {
            for (OnUpdateListener onUpdateListener : mOnUpdateListeners) {
                onUpdateListener.onCancel();
            }
        }
        end();
    }

    /**
     * Modify the given velocity so that it's never below the minimum value, and falls off by the
     * given factor once it goes above the maximum value.
     * In order for the max soft cap to be enforced, the fall-off factor must be >1.
     */
    private static float adjustVelocity(float velocity, long min, long max, float factor) {
        float sign = Math.signum(velocity);
        float magnitude = Math.abs(velocity);

        // If the absolute velocity is less than the min, bump it up.
        if (magnitude < min) {
            return min * sign;
        }

        // If the absolute velocity falls between min and max, or the fall-off factor is invalid,
        // do nothing.
        if (magnitude <= max || factor <= 1) {
            return velocity;
        }

        // Scale the excess velocity by the fall-off factor.
        float excess = magnitude - max;
        float scaled = (float) Math.pow(excess, 1f / factor);
        return (max + scaled) * sign;
    }

    public interface OnUpdateListener {
        /**
         * Called when an update is made to the animation.
         * @param currentRect The rect of the window.
         * @param progress [0, 1] The progress of the rect scale animation.
         */
        void onUpdate(RectF currentRect, float progress);

        default void onCancel() { }
    }

    private abstract static class SpringConfig {
        protected RectF startRect;
        protected RectF targetRect;
        protected @Tracking int tracking;
        protected float stiffnessX;
        protected float stiffnessY;
        protected float dampingX;
        protected float dampingY;
        protected float rectStiffness;
        protected float rectDamping = -1f;
        protected boolean cos;
        protected float minVisChange;
        protected int maxVelocityPxPerS;

        private SpringConfig(Context context, RectF start, RectF target) {
            startRect = start;
            targetRect = target;

            ResourceProvider rp = DynamicResource.provider(context);
            minVisChange = rp.getDimension(R.dimen.swipe_up_fling_min_visible_change);
            maxVelocityPxPerS = (int) rp.getDimension(R.dimen.swipe_up_max_velocity);
        }
    }

    /**
     * Standard spring configuration parameters.
     */
    public static class DefaultSpringConfig extends SpringConfig {

        /** ColorOS lets the landing icon dip to about 95% of its size. */
        private static final float COS_CLOSE_BOUNCE_SIZE_FRACTION = 0.05f;

        public DefaultSpringConfig(Context context, DeviceProfile deviceProfile,
                RectF startRect, RectF targetRect) {
            super(context, startRect, targetRect);

            ResourceProvider rp = DynamicResource.provider(context);
            tracking = getDefaultTracking(deviceProfile);
            stiffnessX = rp.getFloat(R.dimen.swipe_up_rect_x_stiffness);
            stiffnessY = rp.getFloat(R.dimen.swipe_up_rect_y_stiffness);
            dampingX = rp.getFloat(R.dimen.swipe_up_rect_x_damping_ratio);
            dampingY = rp.getFloat(R.dimen.swipe_up_rect_y_damping_ratio);
            rectStiffness = rp.getFloat(R.dimen.swipe_up_rect_scale_stiffness_v2);
            if (QuickstepTransitionManager.cosExperience(context)) {
                // ColorOS 17 flies a closing window home at about 18 rad/s for size and x, y a
                // little softer, measured from its SurfaceFlinger layers. It lands on the icon
                // with a bounce of a fixed size: on the OP13 the icon passes about 6dp beyond its
                // spot and dips to about 95% of its size (damping 0.79 and 0.82 for the travel on
                // that phone). Derive the damping from the travel so that a longer flight on a
                // larger screen bounces by the same amount.
                stiffnessX = 330f;
                dampingX = 1f;
                stiffnessY = 250f;
                dampingY = cosBounceDamping(
                        rp.getDimension(R.dimen.cos_close_bounce_distance),
                        Math.abs(targetRect.centerY() - startRect.centerY()));
                rectStiffness = 330f;
                rectDamping = cosBounceDamping(
                        COS_CLOSE_BOUNCE_SIZE_FRACTION * targetRect.width(),
                        startRect.width() - targetRect.width());
                cos = true;
            }

            BlazeHomeUpController.GesturePhysics hp =
                    BlazeHomeUpController.resolvePhysics(context);
            stiffnessX = hp.stiffnessX;
            stiffnessY = hp.stiffnessY;
            dampingX = hp.dampingX;
            dampingY = hp.dampingY;
            rectStiffness = hp.scaleStiffness;
            rectDamping = hp.scaleDamping;
            tracking = hp.tracking;

            this.startRect = startRect;
            this.targetRect = targetRect;
        }

        /**
         * Damping ratio for which a spring released from rest overshoots its target by
         * {@code overshoot} after travelling {@code distance}.
         */
        private static float cosBounceDamping(float overshoot, float distance) {
            if (overshoot <= 0f || distance <= overshoot) return 1f;
            double l = Math.log(overshoot / distance);
            return Utilities.boundToRange(
                    (float) (-l / Math.sqrt(Math.PI * Math.PI + l * l)), 0.5f, 1f);
        }

        private @Tracking int getDefaultTracking(@Nullable DeviceProfile deviceProfile) {
            @Tracking int tracking;
            if (deviceProfile == null) {
                tracking = startRect.bottom < targetRect.bottom
                        ? TRACKING_BOTTOM
                        : TRACKING_TOP;
            } else {
                int heightPx = deviceProfile.getDeviceProperties().getHeightPx();
                Rect padding = deviceProfile.getWorkspaceProfile().getWorkspacePadding();

                final float topThreshold = heightPx / 3f;
                final float bottomThreshold = deviceProfile.getDeviceProperties().getHeightPx() - padding.bottom;

                if (targetRect.bottom > bottomThreshold) {
                    tracking = TRACKING_CENTER;
                } else if (targetRect.top < topThreshold) {
                    tracking = TRACKING_TOP;
                } else {
                    tracking = TRACKING_CENTER;
                }
            }
            return tracking;
        }
    }

    /**
     * Spring configuration parameters for widgets.
     */
    public static class WidgetSpringConfig extends DefaultSpringConfig {
        public WidgetSpringConfig(Context context, DeviceProfile deviceProfile,
                RectF startRect, RectF targetRect) {
            super(context, deviceProfile, startRect, targetRect);
            ResourceProvider rp = DynamicResource.provider(context);
            stiffnessX = rp.getFloat(R.dimen.widget_x_stiffness);
            stiffnessY = rp.getFloat(R.dimen.widget_y_stiffness);
            rectStiffness = rp.getFloat(R.dimen.widget_rect_scale_stiffness);
        }
    }

    /**
     * Spring configuration parameters for Taskbar/Hotseat items on devices that have a taskbar.
     */
    public static class TaskbarHotseatSpringConfig extends DefaultSpringConfig {
        public TaskbarHotseatSpringConfig(Context context, DeviceProfile deviceProfile,
                RectF start, RectF target) {
            super(context, deviceProfile, start, target);
            tracking = TRACKING_CENTER;
        }
    }

    /**
     * Reads and resolves Good Lock / Home Up (v17) gesture physics, app open curves,
     * folder popup, workspace grid/icon scale, and Task Changer parameters from Settings.Secure.
     *
     * All default presets and simple tuning formulas match Samsung Home Up 17.0.00.54
     * (TuningDataKt.java, SimpleTuningLogic.java, GestureAdvancedTuningActivity.java).
     */
    public static final class BlazeHomeUpController {

        public static final String KEY_GESTURE_MODE = "blaze_homeup_gesture_mode";
        public static final String KEY_SIMPLE_PROGRESS = "blaze_homeup_simple_progress";

        public static final String KEY_DAMPING_X = "blaze_homeup_damping_x";
        public static final String KEY_DAMPING_Y = "blaze_homeup_damping_y";
        public static final String KEY_STIFFNESS_X = "blaze_homeup_stiffness_x";
        public static final String KEY_STIFFNESS_Y = "blaze_homeup_stiffness_y";
        public static final String KEY_SCALE_DAMPING = "blaze_homeup_scale_damping";
        public static final String KEY_SCALE_STIFFNESS = "blaze_homeup_scale_stiffness";
        public static final String KEY_TRACKING_MODE = "blaze_homeup_tracking_mode";

        public static final String KEY_APP_OPEN_DURATION = "blaze_homeup_app_open_duration";
        public static final String KEY_HOME_DURATION = "blaze_homeup_home_duration";
        public static final String KEY_HOME_SCALE = "blaze_homeup_home_scale";
        public static final String KEY_WALLPAPER_DURATION = "blaze_homeup_wallpaper_duration";
        public static final String KEY_WALLPAPER_BLUR = "blaze_homeup_wallpaper_blur";

        public static final String KEY_POPUP_FOLDER = "blaze_homeup_popup_folder";
        public static final String KEY_FOLDER_GRID = "blaze_homeup_folder_grid";
        public static final String KEY_ICON_SCALE_PCT = "blaze_homeup_icon_scale_pct";
        public static final String KEY_GRID_NAME = "blaze_homeup_grid_name";

        public static final String KEY_TASK_CHANGER_STYLE = "blaze_homeup_task_changer_style";
        public static final String KEY_RECENTS_FOCUS_PREV = "blaze_homeup_recents_focus_prev";

        public static final int MODE_DYNAMIC = 0;
        public static final int MODE_CLASSIC = 1;
        public static final int MODE_PLAYFUL = 2;
        public static final int MODE_SWEET = 3;
        public static final int MODE_SIMPLE = 4;
        public static final int MODE_ADVANCED = 5;

        public static final int TASK_STYLE_DEFAULT = 0;
        public static final int TASK_STYLE_STACK = 1;
        public static final int TASK_STYLE_SLIM = 2;

        private BlazeHomeUpController() {}

        public static final class GesturePhysics {
            public float dampingX;
            public float dampingY;
            public float stiffnessX;
            public float stiffnessY;
            public float scaleDamping;
            public float scaleStiffness;
            public int tracking;
            public long appOpenDurationMs;
            public long homeDurationMs;
            public float homeScale;
            public long wallpaperDurationMs;
            public boolean wallpaperBlur;
        }

        public static GesturePhysics resolvePhysics(Context context) {
            GesturePhysics p = new GesturePhysics();
            if (context == null) {
                applyPreset0Dynamic(p);
                return p;
            }
            ContentResolver r = context.getContentResolver();
            int mode = Settings.Secure.getInt(r, KEY_GESTURE_MODE, MODE_DYNAMIC);
            p.wallpaperBlur = Settings.Secure.getInt(r, KEY_WALLPAPER_BLUR, 1) == 1;

            switch (mode) {
                case MODE_CLASSIC:
                    applyPreset1Classic(p);
                    break;
                case MODE_PLAYFUL:
                    applyPreset2Playful(p);
                    break;
                case MODE_SWEET:
                    applyPreset3Sweet(p);
                    break;
                case MODE_SIMPLE: {
                    int progress = Utilities.boundToRange(
                            Settings.Secure.getInt(r, KEY_SIMPLE_PROGRESS, 50), 0, 100);
                    applySimpleSliderFormula(p, progress);
                    break;
                }
                case MODE_ADVANCED: {
                    p.dampingX = Utilities.boundToRange(
                            Settings.Secure.getFloat(r, KEY_DAMPING_X, 0.82f), 0.50f, 1.00f);
                    p.dampingY = Utilities.boundToRange(
                            Settings.Secure.getFloat(r, KEY_DAMPING_Y, 0.82f), 0.50f, 1.00f);
                    p.stiffnessX = Utilities.boundToRange(
                            Settings.Secure.getFloat(r, KEY_STIFFNESS_X, 240f), 50f, 900f);
                    p.stiffnessY = Utilities.boundToRange(
                            Settings.Secure.getFloat(r, KEY_STIFFNESS_Y, 240f), 50f, 900f);
                    p.scaleDamping = Utilities.boundToRange(
                            Settings.Secure.getFloat(r, KEY_SCALE_DAMPING, 0.86f), 0.50f, 1.00f);
                    p.scaleStiffness = Utilities.boundToRange(
                            Settings.Secure.getFloat(r, KEY_SCALE_STIFFNESS, 300f), 50f, 900f);
                    p.tracking = Utilities.boundToRange(
                            Settings.Secure.getInt(r, KEY_TRACKING_MODE, TRACKING_CENTER),
                            TRACKING_TOP, TRACKING_BOTTOM);
                    p.appOpenDurationMs = Utilities.boundToRange(
                            Settings.Secure.getInt(r, KEY_APP_OPEN_DURATION, 450), 180, 1200);
                    p.homeDurationMs = Utilities.boundToRange(
                            Settings.Secure.getInt(r, KEY_HOME_DURATION, 480), 180, 1800);
                    p.homeScale = Utilities.boundToRange(
                            Settings.Secure.getFloat(r, KEY_HOME_SCALE, 0.88f), 0.65f, 0.98f);
                    p.wallpaperDurationMs = Utilities.boundToRange(
                            Settings.Secure.getInt(r, KEY_WALLPAPER_DURATION, 850), 200, 2000);
                    break;
                }
                case MODE_DYNAMIC:
                default:
                    applyPreset0Dynamic(p);
                    break;
            }
            return p;
        }

        private static void applyPreset0Dynamic(GesturePhysics p) {
            p.dampingX = 0.80f;
            p.dampingY = 0.80f;
            p.stiffnessX = 300.0f;
            p.stiffnessY = 300.0f;
            p.scaleDamping = 0.75f;
            p.scaleStiffness = 350.0f;
            p.tracking = TRACKING_BOTTOM;
            p.appOpenDurationMs = 380L;
            p.homeDurationMs = 350L;
            p.homeScale = 0.90f;
            p.wallpaperDurationMs = 450L;
        }

        private static void applyPreset1Classic(GesturePhysics p) {
            p.dampingX = 1.00f;
            p.dampingY = 1.00f;
            p.stiffnessX = 150.0f;
            p.stiffnessY = 150.0f;
            p.scaleDamping = 1.00f;
            p.scaleStiffness = 150.0f;
            p.tracking = TRACKING_TOP;
            p.appOpenDurationMs = 500L;
            p.homeDurationMs = 700L;
            p.homeScale = 0.82f;
            p.wallpaperDurationMs = 1100L;
        }

        private static void applyPreset2Playful(GesturePhysics p) {
            p.dampingX = 0.66f;
            p.dampingY = 0.66f;
            p.stiffnessX = 330.0f;
            p.stiffnessY = 330.0f;
            p.scaleDamping = 0.68f;
            p.scaleStiffness = 650.0f;
            p.tracking = TRACKING_CENTER;
            p.appOpenDurationMs = 420L;
            p.homeDurationMs = 320L;
            p.homeScale = 0.76f;
            p.wallpaperDurationMs = 620L;
        }

        private static void applyPreset3Sweet(GesturePhysics p) {
            p.dampingX = 0.96f;
            p.dampingY = 0.96f;
            p.stiffnessX = 65.0f;
            p.stiffnessY = 65.0f;
            p.scaleDamping = 0.98f;
            p.scaleStiffness = 110.0f;
            p.tracking = TRACKING_CENTER;
            p.appOpenDurationMs = 680L;
            p.homeDurationMs = 1050L;
            p.homeScale = 0.85f;
            p.wallpaperDurationMs = 1500L;
        }

        public static void applySimpleSliderFormula(GesturePhysics p, int progress) {
            int clamped = Utilities.boundToRange(progress, 0, 100);
            int inv = 100 - clamped;
            p.dampingX = (inv * 0.0021f) + 0.78f;
            p.dampingY = (inv * 0.0021f) + 0.78f;
            p.stiffnessX = (clamped * 5.5f) + 50.0f;
            p.stiffnessY = (clamped * 5.5f) + 50.0f;
            p.scaleDamping = (inv * 0.0019f) + 0.80f;
            p.scaleStiffness = (clamped * 6.0f) + 100.0f;
            p.tracking = TRACKING_CENTER;
            p.wallpaperDurationMs = 1300L - (clamped * 10L);
            p.homeDurationMs = 700L - (clamped * 4L);
            p.homeScale = 0.84f + (clamped * 0.001f);
            p.appOpenDurationMs = 650L - (clamped * 3L);
        }

        public static boolean isPopupFolderEnabled(Context context) {
            if (context == null) return false;
            return Settings.Secure.getInt(context.getContentResolver(), KEY_POPUP_FOLDER, 0) == 1;
        }

        public static int getFolderGridMode(Context context) {
            if (context == null) return 0;
            return Utilities.boundToRange(
                    Settings.Secure.getInt(context.getContentResolver(), KEY_FOLDER_GRID, 0), 0, 3);
        }

        public static float getIconScaleMultiplier(Context context) {
            if (context == null) return 1.0f;
            int pct = Utilities.boundToRange(
                    Settings.Secure.getInt(context.getContentResolver(), KEY_ICON_SCALE_PCT, 100),
                    80, 120);
            return pct / 100.0f;
        }

        public static int getTaskChangerStyle(Context context) {
            if (context == null) return TASK_STYLE_DEFAULT;
            return Utilities.boundToRange(
                    Settings.Secure.getInt(context.getContentResolver(), KEY_TASK_CHANGER_STYLE,
                            TASK_STYLE_DEFAULT),
                    TASK_STYLE_DEFAULT, TASK_STYLE_SLIM);
        }

        public static boolean shouldFocusPreviousTaskInRecents(Context context) {
            if (context == null) return false;
            return Settings.Secure.getInt(context.getContentResolver(), KEY_RECENTS_FOCUS_PREV, 0) == 1;
        }
    }

}

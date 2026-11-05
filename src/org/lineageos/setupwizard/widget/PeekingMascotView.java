/*
 * SPDX-FileCopyrightText: 2026 The BlazeAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import java.util.Random;

/**
 * Dynamic Island Capsule + Cute Mascot (BlazeBuddy) peeking down from the top island.
 * Supports spontaneous blinking, reactive gaze tracking, and iOS 27 / ColorOS 17 island rim.
 */
public class PeekingMascotView extends View {

    private final Paint mIslandBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIslandBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mShinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mIslandRect = new RectF();
    private final RectF mLeftEyeRect = new RectF();
    private final RectF mRightEyeRect = new RectF();

    private float mBlinkScaleY = 1.0f;
    private float mGazeOffsetX = 0f;
    private float mGazeOffsetY = 0f;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Random mRandom = new Random();
    private boolean mIsActive = false;

    private final Runnable mBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsActive) return;
            triggerBlink();
            long nextDelay = 2400 + mRandom.nextInt(3200);
            mHandler.postDelayed(this, nextDelay);
        }
    };

    public PeekingMascotView(Context context) {
        this(context, null);
    }

    public PeekingMascotView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PeekingMascotView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mIslandBgPaint.setColor(Color.parseColor("#D8090B14"));
        mIslandBgPaint.setStyle(Paint.Style.FILL);

        mIslandBorderPaint.setColor(Color.parseColor("#45FFFFFF"));
        mIslandBorderPaint.setStyle(Paint.Style.STROKE);
        mIslandBorderPaint.setStrokeWidth(dpToPx(1.1f));

        mEyePaint.setColor(Color.parseColor("#70E1FF"));
        mEyePaint.setStyle(Paint.Style.FILL);

        mShinePaint.setColor(Color.WHITE);
        mShinePaint.setStyle(Paint.Style.FILL);
    }

    public void startMascot() {
        mIsActive = true;
        mHandler.postDelayed(mBlinkRunnable, 1100);
    }

    public void stopMascot() {
        mIsActive = false;
        mHandler.removeCallbacks(mBlinkRunnable);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startMascot();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopMascot();
    }

    private void triggerBlink() {
        ValueAnimator closeAnim = ValueAnimator.ofFloat(1.0f, 0.08f);
        closeAnim.setDuration(85);
        closeAnim.addUpdateListener(anim -> {
            mBlinkScaleY = (float) anim.getAnimatedValue();
            invalidate();
        });
        closeAnim.start();

        mHandler.postDelayed(() -> {
            ValueAnimator openAnim = ValueAnimator.ofFloat(0.08f, 1.0f);
            openAnim.setDuration(155);
            openAnim.setInterpolator(new OvershootInterpolator(1.8f));
            openAnim.addUpdateListener(anim -> {
                mBlinkScaleY = (float) anim.getAnimatedValue();
                invalidate();
            });
            openAnim.start();
        }, 100);
    }

    public void setGaze(float touchX, float touchY) {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        float selfCenterX = loc[0] + getWidth() / 2f;
        float selfCenterY = loc[1] + getHeight() / 2f;

        float deltaX = touchX - selfCenterX;
        float deltaY = touchY - selfCenterY;
        float dist = (float) Math.hypot(deltaX, deltaY);

        if (dist > 0) {
            float maxOffset = dpToPx(3.5f);
            mGazeOffsetX = Math.max(-maxOffset, Math.min(maxOffset, (deltaX / dist) * maxOffset));
            mGazeOffsetY = Math.max(-maxOffset, Math.min(maxOffset, (deltaY / dist) * maxOffset));
            invalidate();
        }
    }

    public void resetGaze() {
        mGazeOffsetX = 0f;
        mGazeOffsetY = 0f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float width = getWidth();
        final float height = getHeight();
        if (width <= 0 || height <= 0) return;

        final float centerY = height / 2f;
        final float pillW = dpToPx(92f);
        final float pillH = dpToPx(28f);
        mIslandRect.set(
                width / 2f - pillW / 2f,
                centerY - pillH / 2f,
                width / 2f + pillW / 2f,
                centerY + pillH / 2f
        );
        canvas.drawRoundRect(mIslandRect, pillH / 2f, pillH / 2f, mIslandBgPaint);
        canvas.drawRoundRect(mIslandRect, pillH / 2f, pillH / 2f, mIslandBorderPaint);

        final float eyeWidth = dpToPx(8.5f);
        final float eyeHeight = dpToPx(13f) * mBlinkScaleY;
        final float eyeSpacing = dpToPx(18f);
        final float cornerRadius = eyeWidth / 2f;

        final float leftEyeCenterX = width / 2f - eyeSpacing / 2f;
        final float rightEyeCenterX = width / 2f + eyeSpacing / 2f;

        mLeftEyeRect.set(
                leftEyeCenterX - eyeWidth / 2f + mGazeOffsetX,
                centerY - eyeHeight / 2f + mGazeOffsetY,
                leftEyeCenterX + eyeWidth / 2f + mGazeOffsetX,
                centerY + eyeHeight / 2f + mGazeOffsetY
        );
        canvas.drawRoundRect(mLeftEyeRect, cornerRadius, cornerRadius, mEyePaint);

        mRightEyeRect.set(
                rightEyeCenterX - eyeWidth / 2f + mGazeOffsetX,
                centerY - eyeHeight / 2f + mGazeOffsetY,
                rightEyeCenterX + eyeWidth / 2f + mGazeOffsetX,
                centerY + eyeHeight / 2f + mGazeOffsetY
        );
        canvas.drawRoundRect(mRightEyeRect, cornerRadius, cornerRadius, mEyePaint);

        if (mBlinkScaleY > 0.5f) {
            float shineRadius = dpToPx(2.1f);
            canvas.drawCircle(
                    leftEyeCenterX - dpToPx(1.2f) + mGazeOffsetX,
                    centerY - dpToPx(2.3f) + mGazeOffsetY,
                    shineRadius,
                    mShinePaint
            );
            canvas.drawCircle(
                    rightEyeCenterX - dpToPx(1.2f) + mGazeOffsetX,
                    centerY - dpToPx(2.3f) + mGazeOffsetY,
                    shineRadius,
                    mShinePaint
            );
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}

/*
 * SPDX-FileCopyrightText: 2026 The BlazeAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * ColorOS 17 "Living Flow" + iOS 27 Liquid Glass OOBE atmospheric backdrop:
 * Stage 1: Expands a multi-spectral luminous core from deep cosmos night.
 * Stage 2: Continuously animates two intersecting 3D translucent glass ribbons
 *          (Warm Amber-Gold petal + Iridescent Violet-Magenta petal) with Fresnel specular rims.
 */
public class LuminousBloomView extends View {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPetalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mAmberPath = new Path();
    private final Path mAmberRimPath = new Path();
    private final Path mVioletPath = new Path();
    private final Path mVioletRimPath = new Path();

    private float mProgress = 0f;
    private float mWavePhase = 0f;
    private long mLastFrameMs = 0L;
    private boolean mDarkCosmosMode = true;
    private ValueAnimator mAnimator;
    private OnBloomCompleteListener mListener;

    public interface OnBloomCompleteListener {
        void onBloomComplete();
    }

    public LuminousBloomView(Context context) {
        this(context, null);
    }

    public LuminousBloomView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LuminousBloomView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        float d = getResources().getDisplayMetrics().density;
        mRimPaint.setStyle(Paint.Style.STROKE);
        mRimPaint.setStrokeWidth(2.0f * d);
        mRimPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setDarkCosmosMode(boolean darkCosmosMode) {
        mDarkCosmosMode = darkCosmosMode;
        invalidate();
    }

    public void startBloomAnimation(long duration, OnBloomCompleteListener listener) {
        mListener = listener;
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(duration);
        mAnimator.setInterpolator(new DecelerateInterpolator(1.6f));
        mAnimator.addUpdateListener(animation -> {
            mProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mListener != null) {
                    mListener.onBloomComplete();
                }
            }
        });
        mAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth();
        final float h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.uptimeMillis();
        if (mLastFrameMs == 0L) mLastFrameMs = now;
        float dt = Math.min((now - mLastFrameMs) / 1000f, 0.05f);
        mLastFrameMs = now;
        mWavePhase += dt * 0.9f;

        float wave1 = (float) Math.sin(mWavePhase);
        float wave2 = (float) Math.cos(mWavePhase * 0.78f);
        float wave3 = (float) Math.sin(mWavePhase * 0.55f + 1.1f);

        // 1. Base Deep Cosmos Indigo / Pastel Sky
        if (mDarkCosmosMode) {
            mPaint.setShader(new LinearGradient(
                    0f, 0f, 0f, h,
                    new int[]{0xFF060919, 0xFF141A46, 0xFF101434, 0xFF060812},
                    new float[]{0f, 0.36f, 0.74f, 1f},
                    Shader.TileMode.CLAMP));
        } else {
            mPaint.setShader(new LinearGradient(
                    0f, 0f, 0f, h,
                    new int[]{0xFFFFF0F3, 0xFFFFF4F0, 0xFFFAF7F5},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP));
        }
        mPaint.setAlpha(255);
        canvas.drawRect(0f, 0f, w, h, mPaint);

        float reveal = mAnimator != null ? Math.min(1f, mProgress * 1.25f) : 1f;

        // 2. Ambient Upper Indigo-Violet Halo
        float auraCx = w * 0.50f + wave2 * (w * 0.04f);
        float auraCy = h * 0.28f + wave1 * (h * 0.02f);
        float auraRadius = Math.max(w, h) * (0.18f + 0.30f * reveal);
        mPaint.setShader(new RadialGradient(
                auraCx, auraCy, auraRadius,
                new int[]{0x585E6BFF, 0x2C3B44B0, 0x00101432},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(auraCx, auraCy, auraRadius, mPaint);

        // 3. Upper-Right Warm Amber-Gold-Coral 3D Glass Petal (ColorOS 17 Signature Ribbon)
        float petalSlide = (1f - reveal) * h * 0.22f;
        float ax0 = w * 1.12f;
        float ay0 = h * (0.34f + 0.018f * wave1) + petalSlide;
        float ac1x = w * (0.64f + 0.035f * wave2);
        float ac1y = h * (0.41f + 0.022f * wave3) + petalSlide;
        float ac2x = w * (0.14f + 0.025f * wave1);
        float ac2y = h * (0.60f + 0.020f * wave2) + petalSlide;
        float axMid = w * (0.10f + 0.020f * wave3);
        float ayMid = h * (0.69f + 0.015f * wave1) + petalSlide;

        mAmberPath.reset();
        mAmberPath.moveTo(ax0, ay0);
        mAmberPath.cubicTo(ac1x, ac1y, ac2x, ac2y, axMid, ayMid);
        mAmberPath.cubicTo(
                w * 0.32f, h * (0.78f + 0.018f * wave2) + petalSlide,
                w * 0.78f, h * (0.72f + 0.015f * wave1) + petalSlide,
                w * 1.15f, h * 0.60f + petalSlide);
        mAmberPath.close();

        mPetalPaint.setShader(new LinearGradient(
                w * 0.95f, h * 0.36f,
                w * 0.20f, h * 0.74f,
                new int[]{0xEEFF6A1A, 0xE6FF9A3C, 0xC8FFD26E, 0x344B2468},
                new float[]{0f, 0.38f, 0.74f, 1f},
                Shader.TileMode.CLAMP));
        mPetalPaint.setAlpha(Math.round(255 * reveal));
        canvas.drawPath(mAmberPath, mPetalPaint);

        mAmberRimPath.reset();
        mAmberRimPath.moveTo(ax0, ay0);
        mAmberRimPath.cubicTo(ac1x, ac1y, ac2x, ac2y, axMid, ayMid);
        mRimPaint.setShader(new LinearGradient(
                w * 0.95f, ay0, axMid, ayMid,
                new int[]{0xD8FFF3D1, 0xA8FFD080, 0x00FFFFFF},
                new float[]{0f, 0.65f, 1f},
                Shader.TileMode.CLAMP));
        mRimPaint.setAlpha(Math.round(255 * reveal));
        canvas.drawPath(mAmberRimPath, mRimPaint);

        // 4. Lower Foreground Iridescent Violet-Magenta-Peach Glass Petal
        float vx0 = -w * 0.14f;
        float vy0 = h * (0.77f + 0.016f * wave2) + petalSlide * 0.8f;
        float vc1x = w * (0.08f + 0.025f * wave1);
        float vc1y = h * (0.56f + 0.022f * wave3) + petalSlide * 0.8f;
        float vc2x = w * (0.56f + 0.030f * wave2);
        float vc2y = h * (0.58f - 0.018f * wave1) + petalSlide * 0.8f;
        float vxEnd = w * 0.96f;
        float vyEnd = h * (0.94f + 0.012f * wave3) + petalSlide * 0.8f;

        mVioletPath.reset();
        mVioletPath.moveTo(vx0, vy0);
        mVioletPath.cubicTo(vc1x, vc1y, vc2x, vc2y, vxEnd, vyEnd);
        mVioletPath.lineTo(w * 1.05f, h * 1.08f);
        mVioletPath.lineTo(-w * 0.14f, h * 1.08f);
        mVioletPath.close();

        mPetalPaint.setShader(new LinearGradient(
                w * 0.28f, h * 0.56f,
                w * 0.68f, h * 0.98f,
                new int[]{0xE6FFC49B, 0xD8D462FF, 0xC86B2CF5, 0x90181240},
                new float[]{0f, 0.28f, 0.68f, 1f},
                Shader.TileMode.CLAMP));
        mPetalPaint.setAlpha(Math.round(255 * reveal));
        canvas.drawPath(mVioletPath, mPetalPaint);

        mVioletRimPath.reset();
        mVioletRimPath.moveTo(vx0, vy0);
        mVioletRimPath.cubicTo(vc1x, vc1y, vc2x, vc2y, vxEnd, vyEnd);
        mRimPaint.setShader(new LinearGradient(
                0f, h * 0.60f, w * 0.90f, h * 0.88f,
                new int[]{0x50FFFFFF, 0xEEFFF0FF, 0xAA9C72FF, 0x00FFFFFF},
                new float[]{0f, 0.35f, 0.75f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawPath(mVioletRimPath, mRimPaint);

        if (isAttachedToWindow()) {
            postInvalidateOnAnimation();
        }
    }
}

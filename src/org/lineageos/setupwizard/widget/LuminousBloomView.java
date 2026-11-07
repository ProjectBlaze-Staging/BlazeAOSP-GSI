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
 * Refined architectural ambient canvas for OOBE.
 * Renders a calm, deeply layered obsidian-slate surface (or warm porcelain in light mode)
 * with subtle diffused light fields and a delicate frosted glass horizon arc.
 */
public class LuminousBloomView extends View {

    private final Paint mBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mAmbientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArcFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArcStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mArcPath = new Path();
    private final Path mRimPath = new Path();

    private float mProgress = 1f;
    private float mPhase = 0f;
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
        setWillNotDraw(false);
        float d = getResources().getDisplayMetrics().density;
        mArcStrokePaint.setStyle(Paint.Style.STROKE);
        mArcStrokePaint.setStrokeWidth(1.2f * d);
        mArcStrokePaint.setStrokeCap(Paint.Cap.ROUND);
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
        mAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
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
        mPhase += dt * 0.45f;

        float driftX = (float) Math.sin(mPhase) * (w * 0.025f);
        float driftY = (float) Math.cos(mPhase * 0.8f) * (h * 0.015f);
        float reveal = Math.min(1f, 0.35f + mProgress * 0.65f);

        if (mDarkCosmosMode) {
            // 1. Calm Obsidian-Graphite Architectural Base
            mBasePaint.setShader(new LinearGradient(
                    0f, 0f, 0f, h,
                    new int[]{0xFF0A0D14, 0xFF101521, 0xFF0C1019, 0xFF080A10},
                    new float[]{0f, 0.38f, 0.75f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0f, 0f, w, h, mBasePaint);

            // 2. Upper Soft Slate-Blue Ambient Light Diffusion
            float upperCx = w * 0.72f + driftX;
            float upperCy = h * 0.20f + driftY;
            float upperR = Math.max(w, h) * 0.52f * reveal;
            mAmbientPaint.setShader(new RadialGradient(
                    upperCx, upperCy, Math.max(10f, upperR),
                    new int[]{0x2E3B82F6, 0x141E3A8A, 0x000A0D14},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(upperCx, upperCy, upperR, mAmbientPaint);

            // 3. Lower Warm Champagne-Indigo Subtle Ambient Reflection
            float lowerCx = w * 0.26f - driftX * 0.7f;
            float lowerCy = h * 0.68f - driftY * 0.6f;
            float lowerR = Math.max(w, h) * 0.48f * reveal;
            mAmbientPaint.setShader(new RadialGradient(
                    lowerCx, lowerCy, Math.max(10f, lowerR),
                    new int[]{0x226366F1, 0x10312E81, 0x00080A10},
                    new float[]{0f, 0.6f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(lowerCx, lowerCy, lowerR, mAmbientPaint);

            // 4. Subtle Architectural Frosted Glass Horizon Curve
            float curveTop = h * 0.56f + driftY * 0.5f;
            mArcPath.reset();
            mArcPath.moveTo(0f, curveTop + h * 0.06f);
            mArcPath.cubicTo(
                    w * 0.34f, curveTop - h * 0.04f,
                    w * 0.68f, curveTop + h * 0.08f,
                    w, curveTop - h * 0.01f);
            mArcPath.lineTo(w, h);
            mArcPath.lineTo(0f, h);
            mArcPath.close();

            mArcFillPaint.setShader(new LinearGradient(
                    0f, curveTop, 0f, h,
                    new int[]{0x14FFFFFF, 0x0894A3B8, 0x00080A10},
                    new float[]{0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawPath(mArcPath, mArcFillPaint);

            mRimPath.reset();
            mRimPath.moveTo(0f, curveTop + h * 0.06f);
            mRimPath.cubicTo(
                    w * 0.34f, curveTop - h * 0.04f,
                    w * 0.68f, curveTop + h * 0.08f,
                    w, curveTop - h * 0.01f);
            mArcStrokePaint.setShader(new LinearGradient(
                    0f, curveTop, w, curveTop,
                    new int[]{0x05FFFFFF, 0x26E2E8F0, 0x05FFFFFF},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawPath(mRimPath, mArcStrokePaint);
        } else {
            // Light Mode: Serene Alabaster & Pearl Glass Surface
            mBasePaint.setShader(new LinearGradient(
                    0f, 0f, 0f, h,
                    new int[]{0xFFF8FAFC, 0xFFF1F5F9, 0xFFEDF2F7},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0f, 0f, w, h, mBasePaint);

            float upperCx = w * 0.70f + driftX;
            float upperCy = h * 0.22f + driftY;
            float upperR = Math.max(w, h) * 0.50f;
            mAmbientPaint.setShader(new RadialGradient(
                    upperCx, upperCy, Math.max(10f, upperR),
                    new int[]{0x243B82F6, 0x0E60A5FA, 0x00F8FAFC},
                    new float[]{0f, 0.6f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(upperCx, upperCy, upperR, mAmbientPaint);
        }

        if (isAttachedToWindow()) {
            postInvalidateOnAnimation();
        }
    }
}

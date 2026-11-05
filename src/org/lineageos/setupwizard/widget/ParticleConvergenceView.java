/*
 * SPDX-FileCopyrightText: 2026 The BlazeAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ColorOS 17 / iOS 27 Flagship OOBE Centerpiece:
 * Luminous constellation particle convergence + Floating Liquid Glass "17" Emblem Core
 * with 120Hz continuous orbital breathing.
 */
public class ParticleConvergenceView extends View {

    private static class Node {
        final float startAngle;
        final float startDist;
        final float targetAngle;
        final float targetDist;
        final float radius;
        final int color;

        Node(float sAngle, float sDist, float tAngle, float tDist, float rad, int color) {
            this.startAngle = sAngle;
            this.startDist = sDist;
            this.targetAngle = tAngle;
            this.targetDist = tDist;
            this.radius = rad;
            this.color = color;
        }
    }

    private final Paint mNodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCardBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mCoreRect = new RectF();

    private final List<Node> mNodes = new ArrayList<>();
    private float mProgress = 0.25f;
    private float mOrbitPhase = 0f;
    private ValueAnimator mAnimator;
    private ValueAnimator mOrbitAnimator;

    public ParticleConvergenceView(Context context) {
        this(context, null);
    }

    public ParticleConvergenceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ParticleConvergenceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mNodePaint.setStyle(Paint.Style.FILL);

        mLinePaint.setColor(Color.parseColor("#88FFE0B2"));
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(dpToPx(1.1f));

        mCardBgPaint.setColor(Color.parseColor("#2812162A"));
        mCardBgPaint.setStyle(Paint.Style.FILL);

        mCardBorderPaint.setColor(Color.parseColor("#55FFFFFF"));
        mCardBorderPaint.setStyle(Paint.Style.STROKE);
        mCardBorderPaint.setStrokeWidth(dpToPx(1.3f));

        mNumberPaint.setColor(Color.WHITE);
        mNumberPaint.setTextSize(spToPx(64f));
        mNumberPaint.setTextAlign(Paint.Align.CENTER);
        mNumberPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.BOLD));
        mNumberPaint.setShadowLayer(dpToPx(18f), 0f, dpToPx(4f), Color.parseColor("#77FF8C3B"));

        mLabelPaint.setColor(Color.parseColor("#E6F0FF"));
        mLabelPaint.setTextSize(spToPx(13f));
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
        mLabelPaint.setLetterSpacing(0.14f);
        mLabelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        int[] palette = new int[]{
                Color.parseColor("#FFB347"),
                Color.parseColor("#FF7A59"),
                Color.parseColor("#D8B4FE"),
                Color.parseColor("#70E1FF"),
                Color.parseColor("#FFF3E0")
        };

        Random rnd = new Random(1337);
        for (int i = 0; i < 20; i++) {
            float sAngle = (float) (i * (2 * Math.PI / 20.0)) + (rnd.nextFloat() - 0.5f) * 0.25f;
            float sDist = dpToPx(135 + rnd.nextFloat() * 75);

            float tAngle = sAngle + 0.45f;
            float tDist = dpToPx(76 + (i % 3) * 18);

            float radius = dpToPx(2.8f + (i % 4) * 1.1f);
            mNodes.add(new Node(sAngle, sDist, tAngle, tDist, radius, palette[i % palette.length]));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startOrbitLoop();
        // Start gentle convergence automatically so even before typewriter finishes it looks alive
        startConvergence(2200);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAnimator != null) mAnimator.cancel();
        if (mOrbitAnimator != null) mOrbitAnimator.cancel();
    }

    private void startOrbitLoop() {
        if (mOrbitAnimator != null && mOrbitAnimator.isRunning()) return;
        mOrbitAnimator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2.0));
        mOrbitAnimator.setDuration(10000);
        mOrbitAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mOrbitAnimator.setInterpolator(null);
        mOrbitAnimator.addUpdateListener(anim -> {
            mOrbitPhase = (float) anim.getAnimatedValue();
            invalidate();
        });
        mOrbitAnimator.start();
    }

    public void startConvergence(long duration) {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofFloat(0.15f, 1f);
        mAnimator.setDuration(duration);
        mAnimator.setInterpolator(new OvershootInterpolator(0.95f));
        mAnimator.addUpdateListener(animation -> {
            mProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        mAnimator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            LinearGradient numGrad = new LinearGradient(
                    0f, h * 0.3f, 0f, h * 0.65f,
                    new int[]{Color.WHITE, Color.parseColor("#FFE4B5"), Color.parseColor("#D8B4FE")},
                    new float[]{0f, 0.58f, 1f},
                    Shader.TileMode.CLAMP
            );
            mNumberPaint.setShader(numGrad);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float width = getWidth();
        final float height = getHeight();
        if (width <= 0 || height <= 0) return;

        final float centerX = width / 2f;
        final float centerY = height / 2f + (float) Math.sin(mOrbitPhase) * dpToPx(5f);
        final float t = mProgress;

        float[] currentX = new float[mNodes.size()];
        float[] currentY = new float[mNodes.size()];

        for (int i = 0; i < mNodes.size(); i++) {
            Node n = mNodes.get(i);
            float angle = n.startAngle + (n.targetAngle - n.startAngle) * t + mOrbitPhase * 0.35f;
            float dist = n.startDist + (n.targetDist - n.startDist) * t;
            currentX[i] = centerX + (float) Math.cos(angle) * dist;
            currentY[i] = centerY + (float) Math.sin(angle) * dist;
        }

        // Draw luminous constellation filaments
        float maxLineDist = dpToPx(82f);
        for (int i = 0; i < mNodes.size(); i++) {
            for (int j = i + 1; j < mNodes.size(); j++) {
                float dx = currentX[i] - currentX[j];
                float dy = currentY[i] - currentY[j];
                float dist = (float) Math.hypot(dx, dy);
                if (dist < maxLineDist) {
                    int alpha = (int) (255 * (1f - dist / maxLineDist) * 0.34f * Math.min(1f, t));
                    mLinePaint.setAlpha(Math.max(0, Math.min(255, alpha)));
                    canvas.drawLine(currentX[i], currentY[i], currentX[j], currentY[j], mLinePaint);
                }
            }
        }

        // Draw orbital nodes
        for (int i = 0; i < mNodes.size(); i++) {
            Node n = mNodes.get(i);
            mNodePaint.setColor(n.color);
            mNodePaint.setAlpha((int) (220 * Math.min(1f, 0.4f + 0.6f * t)));
            canvas.drawCircle(currentX[i], currentY[i], n.radius * (0.7f + 0.3f * t), mNodePaint);
        }

        // Draw central Liquid Glass "17 / LIVING FLOW" emblem card
        float cardSize = dpToPx(148f) * (0.85f + 0.15f * t);
        float radius = dpToPx(38f);
        mCoreRect.set(
                centerX - cardSize / 2f,
                centerY - cardSize / 2f,
                centerX + cardSize / 2f,
                centerY + cardSize / 2f
        );
        canvas.drawRoundRect(mCoreRect, radius, radius, mCardBgPaint);
        canvas.drawRoundRect(mCoreRect, radius, radius, mCardBorderPaint);

        canvas.drawText("17", centerX, centerY + dpToPx(12f), mNumberPaint);
        canvas.drawText("LIVING FLOW", centerX, centerY + dpToPx(38f), mLabelPaint);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}

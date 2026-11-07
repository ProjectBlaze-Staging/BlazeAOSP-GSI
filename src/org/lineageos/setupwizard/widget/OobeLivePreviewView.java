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
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Interactive real-time miniature phone preview for OOBE Theme & Glass Architecture studio.
 * Previews Dark vs Light mode, Glass style (Dark Smoked Crystal, Liquid Glass, Soft Frosted),
 * and Dynamic Island state without using any third-party brand or trademark names.
 */
public class OobeLivePreviewView extends View {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Path mClipPath = new Path();

    private boolean mDarkMode = true;
    private int mGlassStyle = 3; // 3 = Dark Smoked Crystal, 0 = Liquid Glass, 1 = Soft Frosted
    private boolean mDynamicIslandEnabled = true;
    private float mTransitionProgress = 1f;

    public OobeLivePreviewView(Context context) {
        this(context, null);
    }

    public OobeLivePreviewView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OobeLivePreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        mTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void updateState(boolean darkMode, int glassStyle, boolean dynamicIslandEnabled) {
        mDarkMode = darkMode;
        mGlassStyle = glassStyle;
        mDynamicIslandEnabled = dynamicIslandEnabled;
        ValueAnimator anim = ValueAnimator.ofFloat(0.75f, 1f);
        anim.setDuration(260);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            mTransitionProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float d = getResources().getDisplayMetrics().density;

        float cardPad = 6f * d;
        mRect.set(cardPad, cardPad, w - cardPad, h - cardPad);
        float cornerRadius = 24f * d;

        int save = canvas.save();
        mClipPath.reset();
        mClipPath.addRoundRect(mRect, cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.clipPath(mClipPath);

        // 1. Wallpaper / Ambient Surface Preview
        int[] bgColors;
        if (mDarkMode) {
            if (mGlassStyle == 3) {
                bgColors = new int[]{0xFF0B0E17, 0xFF141B2D, 0xFF0F1420};
            } else if (mGlassStyle == 0) {
                bgColors = new int[]{0xFF0E1528, 0xFF1C2848, 0xFF131D36};
            } else {
                bgColors = new int[]{0xFF11151E, 0xFF1A202E, 0xFF131822};
            }
        } else {
            bgColors = new int[]{0xFFE8F0FE, 0xFFF1F5F9, 0xFFE2E8F0};
        }
        mPaint.setShader(new LinearGradient(
                0f, mRect.top, 0f, mRect.bottom,
                bgColors, new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(mRect, mPaint);
        mPaint.setShader(null);

        // 2. Top Dynamic Island / Status Bar Capsule
        float islandW = (mDynamicIslandEnabled ? 132f : 56f) * d * mTransitionProgress;
        float islandH = (mDynamicIslandEnabled ? 24f : 14f) * d;
        float islandTop = mRect.top + 10f * d;
        RectF islandRect = new RectF((w - islandW) * 0.5f, islandTop, (w + islandW) * 0.5f, islandTop + islandH);
        mPaint.setColor(0xEE080B11);
        canvas.drawRoundRect(islandRect, islandH * 0.5f, islandH * 0.5f, mPaint);

        if (mDynamicIslandEnabled) {
            mPaint.setColor(0xFF3B82F6);
            canvas.drawCircle(islandRect.left + 12f * d, islandRect.centerY(), 4f * d, mPaint);
            mTextPaint.setColor(Color.WHITE);
            mTextPaint.setTextSize(9.5f * d);
            canvas.drawText("Canlı Etkinlik • 02:45", islandRect.centerX() + 6f * d, islandRect.centerY() + 3.3f * d, mTextPaint);
        }

        // 3. Glass Quick Settings & Media Card Preview
        float glassLeft = mRect.left + 16f * d;
        float glassRight = mRect.right - 16f * d;
        float glassTop = islandRect.bottom + 12f * d;
        float glassBottom = mRect.bottom - 48f * d;
        RectF glassRect = new RectF(glassLeft, glassTop, glassRight, glassBottom);

        int glassFill;
        int glassRim;
        String styleBadge;
        if (mGlassStyle == 3) {
            glassFill = mDarkMode ? 0xBD101522 : 0xD8FFFFFF;
            glassRim = mDarkMode ? 0x5093C5FD : 0x603B82F6;
            styleBadge = "Koyu Kristal Cam";
        } else if (mGlassStyle == 0) {
            glassFill = mDarkMode ? 0xA8182238 : 0xE0FFFFFF;
            glassRim = mDarkMode ? 0x66E2E8F0 : 0x8094A3B8;
            styleBadge = "Akıcı Liquid Glass";
        } else {
            glassFill = mDarkMode ? 0xC81A202C : 0xEEFFFFFF;
            glassRim = mDarkMode ? 0x33FFFFFF : 0x4064748B;
            styleBadge = "Yumuşak Buzlu Cam";
        }

        mPaint.setColor(glassFill);
        canvas.drawRoundRect(glassRect, 18f * d, 18f * d, mPaint);
        mStrokePaint.setStrokeWidth(1.2f * d);
        mStrokePaint.setColor(glassRim);
        canvas.drawRoundRect(glassRect, 18f * d, 18f * d, mStrokePaint);

        // Mini QS Pills inside Glass Card
        float pillTop = glassRect.top + 10f * d;
        float pillH = 24f * d;
        float pillGap = 8f * d;
        float pillW = (glassRect.width() - 24f * d - pillGap) * 0.5f;

        RectF p1 = new RectF(glassRect.left + 12f * d, pillTop, glassRect.left + 12f * d + pillW, pillTop + pillH);
        mPaint.setColor(0xFF2563EB);
        canvas.drawRoundRect(p1, pillH * 0.5f, pillH * 0.5f, mPaint);
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(9.5f * d);
        canvas.drawText("Wi-Fi • 5G", p1.centerX(), p1.centerY() + 3.2f * d, mTextPaint);

        RectF p2 = new RectF(p1.right + pillGap, pillTop, p1.right + pillGap + pillW, pillTop + pillH);
        mPaint.setColor(mDarkMode ? 0x38FFFFFF : 0x180F172A);
        canvas.drawRoundRect(p2, pillH * 0.5f, pillH * 0.5f, mPaint);
        mTextPaint.setColor(mDarkMode ? 0xFFE2E8F0 : 0xFF1E293B);
        canvas.drawText(styleBadge, p2.centerX(), p2.centerY() + 3.2f * d, mTextPaint);

        // 4. Bottom Floating Glass Tab Bar
        float dockW = glassRect.width();
        float dockH = 28f * d;
        float dockTop = mRect.bottom - 10f * d - dockH;
        RectF dockRect = new RectF(glassLeft, dockTop, glassRight, dockTop + dockH);
        mPaint.setColor(glassFill);
        canvas.drawRoundRect(dockRect, dockH * 0.5f, dockH * 0.5f, mPaint);
        mStrokePaint.setColor(glassRim);
        canvas.drawRoundRect(dockRect, dockH * 0.5f, dockH * 0.5f, mStrokePaint);

        RectF activeLens = new RectF(dockRect.left + 6f * d, dockRect.top + 3.5f * d,
                dockRect.left + dockW * 0.36f, dockRect.bottom - 3.5f * d);
        mPaint.setColor(mDarkMode ? 0x333B82F6 : 0x262563EB);
        canvas.drawRoundRect(activeLens, activeLens.height() * 0.5f, activeLens.height() * 0.5f, mPaint);

        mTextPaint.setColor(mDarkMode ? Color.WHITE : 0xFF1E3A8A);
        mTextPaint.setTextSize(9f * d);
        canvas.drawText(mDarkMode ? "Karanlık Tema" : "Aydınlık Tema", activeLens.centerX(), activeLens.centerY() + 3f * d, mTextPaint);

        mTextPaint.setColor(mDarkMode ? 0x99CBD5E1 : 0x99475569);
        canvas.drawText("Yüzey & Cam Önizlemesi", dockRect.left + dockW * 0.68f, dockRect.centerY() + 3f * d, mTextPaint);

        canvas.restoreToCount(save);

        mStrokePaint.setStrokeWidth(1.4f * d);
        mStrokePaint.setColor(mDarkMode ? 0x38FFFFFF : 0x3364748B);
        canvas.drawRoundRect(mRect, cornerRadius, cornerRadius, mStrokePaint);
    }
}

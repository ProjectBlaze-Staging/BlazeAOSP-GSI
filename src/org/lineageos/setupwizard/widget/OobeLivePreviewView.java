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
import android.view.MotionEvent;
import android.view.View;

import org.lineageos.setupwizard.util.OobeSpringHelper;

/**
 * Interactive 120Hz Live Preview Stage for the Flagship OOBE Visual Studio.
 * Renders a miniature ColorOS 17 + iOS 27 + One UI 9 device canvas showing:
 * - Living Flow 3D glass ribbon wallpaper (Dark Cosmos or Light Pearl)
 * - Morphing Dynamic Island (Music Waveform / 04:59 Timer / Incoming Message)
 * - One UI 9 Spatial Bento Cards
 * - iOS 27 Liquid Glass Bottom Tab Bar with Active Lens Pill
 */
public class OobeLivePreviewView extends View {

    private final Paint mOuterBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOuterBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOrbWarmPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOrbVioletPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIslandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mIslandStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCardStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTabBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTabBarStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLensPillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mAccentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF mStageRect = new RectF();
    private final RectF mIslandRect = new RectF();
    private final RectF mCardLeftRect = new RectF();
    private final RectF mCardRightRect = new RectF();
    private final RectF mTabBarRect = new RectF();
    private final RectF mLensRect = new RectF();

    private boolean mIsDarkMode = true;
    private int mGlassStyle = 3; // 3 = iOS 27 Smoked Crystal, 0 = Hybrid, 1 = Frosted, 2 = M3
    private int mIslandMode = 0; // 0 = Mesaj, 1 = Zamanlayıcı, 2 = Müzik
    private boolean mFloatingTabBar = true;

    private float mPhase = 0f;
    private float mIslandMorph = 1f;
    private ValueAnimator mLoopAnimator;
    private ValueAnimator mMorphAnimator;

    public OobeLivePreviewView(Context context) {
        this(context, null);
    }

    public OobeLivePreviewView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OobeLivePreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mOuterBorderPaint.setStyle(Paint.Style.STROKE);
        mOuterBorderPaint.setStrokeWidth(dpToPx(1.4f));

        mIslandPaint.setColor(Color.parseColor("#EE060810"));
        mIslandPaint.setStyle(Paint.Style.FILL);

        mIslandStrokePaint.setColor(Color.parseColor("#55FFFFFF"));
        mIslandStrokePaint.setStyle(Paint.Style.STROKE);
        mIslandStrokePaint.setStrokeWidth(dpToPx(1f));

        mCardPaint.setStyle(Paint.Style.FILL);
        mCardStrokePaint.setStyle(Paint.Style.STROKE);
        mCardStrokePaint.setStrokeWidth(dpToPx(1f));

        mTabBarPaint.setStyle(Paint.Style.FILL);
        mTabBarStrokePaint.setStyle(Paint.Style.STROKE);
        mTabBarStrokePaint.setStrokeWidth(dpToPx(1.1f));

        mLensPillPaint.setStyle(Paint.Style.FILL);
        mAccentPaint.setStyle(Paint.Style.FILL);

        mTextTitlePaint.setColor(Color.WHITE);
        mTextTitlePaint.setTextSize(spToPx(12f));
        mTextTitlePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        mTextSubPaint.setColor(Color.parseColor("#CCE6F0FF"));
        mTextSubPaint.setTextSize(spToPx(10.5f));
        mTextSubPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    public void setPreviewConfig(boolean isDarkMode, int glassStyle, boolean floatingTabBar) {
        mIsDarkMode = isDarkMode;
        mGlassStyle = glassStyle;
        mFloatingTabBar = floatingTabBar;
        updateShaders(getWidth(), getHeight());
        invalidate();
    }

    public void cycleIslandMode() {
        mIslandMode = (mIslandMode + 1) % 3;
        if (mMorphAnimator != null) mMorphAnimator.cancel();
        mMorphAnimator = ValueAnimator.ofFloat(0.78f, 1f);
        mMorphAnimator.setDuration(420);
        mMorphAnimator.setInterpolator(OobeSpringHelper.LAMINAR_SPRING_INTERPOLATOR);
        mMorphAnimator.addUpdateListener(anim -> {
            mIslandMorph = (float) anim.getAnimatedValue();
            invalidate();
        });
        mMorphAnimator.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            cycleIslandMode();
            performClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mLoopAnimator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2.0));
        mLoopAnimator.setDuration(7500);
        mLoopAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mLoopAnimator.setInterpolator(null);
        mLoopAnimator.addUpdateListener(anim -> {
            mPhase = (float) anim.getAnimatedValue();
            invalidate();
        });
        mLoopAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mLoopAnimator != null) mLoopAnimator.cancel();
        if (mMorphAnimator != null) mMorphAnimator.cancel();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShaders(w, h);
    }

    private void updateShaders(int w, int h) {
        if (w <= 0 || h <= 0) return;
        int[] bgColors = mIsDarkMode
                ? new int[]{Color.parseColor("#0D1024"), Color.parseColor("#1D1942"), Color.parseColor("#121029")}
                : new int[]{Color.parseColor("#1E2852"), Color.parseColor("#352E6E"), Color.parseColor("#24204A")};
        mOuterBgPaint.setShader(new LinearGradient(
                0f, 0f, w, h, bgColors, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        mOuterBorderPaint.setColor(Color.parseColor(mIsDarkMode ? "#40FFFFFF" : "#66FFFFFF"));

        mOrbWarmPaint.setShader(new LinearGradient(
                0f, h * 0.2f, w * 0.8f, h,
                new int[]{Color.parseColor("#CCFF9E44"), Color.parseColor("#88FF5722"), Color.TRANSPARENT},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP
        ));
        mOrbVioletPaint.setShader(new LinearGradient(
                w * 0.2f, h * 0.35f, w, h,
                new int[]{Color.parseColor("#BBBA68C8"), Color.parseColor("#775E35B1"), Color.TRANSPARENT},
                new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP
        ));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth();
        final float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float pad = dpToPx(2f);
        float stageRadius = dpToPx(26f);
        mStageRect.set(pad, pad, w - pad, h - pad);
        canvas.drawRoundRect(mStageRect, stageRadius, stageRadius, mOuterBgPaint);

        // Living Flow Ambient Ribbons inside stage
        float driftX = (float) Math.sin(mPhase) * dpToPx(12f);
        float driftY = (float) Math.cos(mPhase) * dpToPx(8f);
        canvas.drawCircle(w * 0.72f + driftX, h * 0.42f + driftY, dpToPx(68f), mOrbWarmPaint);
        canvas.drawCircle(w * 0.32f - driftX, h * 0.68f - driftY, dpToPx(64f), mOrbVioletPaint);

        // 1. Interactive Dynamic Island at Top
        float islandW = dpToPx(mIslandMode == 0 ? 226f : (mIslandMode == 1 ? 186f : 198f)) * mIslandMorph;
        float islandH = dpToPx(34f) * (0.9f + 0.1f * mIslandMorph);
        float islandTop = dpToPx(12f);
        mIslandRect.set(
                w / 2f - islandW / 2f,
                islandTop,
                w / 2f + islandW / 2f,
                islandTop + islandH
        );
        canvas.drawRoundRect(mIslandRect, islandH / 2f, islandH / 2f, mIslandPaint);
        canvas.drawRoundRect(mIslandRect, islandH / 2f, islandH / 2f, mIslandStrokePaint);

        float dotCx = mIslandRect.left + dpToPx(17f);
        float dotCy = mIslandRect.centerY();
        if (mIslandMode == 0) {
            mAccentPaint.setColor(Color.parseColor("#25D366"));
            canvas.drawCircle(dotCx, dotCy, dpToPx(6f), mAccentPaint);
            canvas.drawText("Mesaj • Dokun & Değiştir", mIslandRect.left + dpToPx(30f),
                    dotCy + spToPx(4f), mTextTitlePaint);
        } else if (mIslandMode == 1) {
            mAccentPaint.setColor(Color.parseColor("#FF9E44"));
            canvas.drawCircle(dotCx, dotCy, dpToPx(6f), mAccentPaint);
            canvas.drawText("Zamanlayıcı • 04:59", mIslandRect.left + dpToPx(30f),
                    dotCy + spToPx(4f), mTextTitlePaint);
        } else {
            mAccentPaint.setColor(Color.parseColor("#D8B4FE"));
            canvas.drawCircle(dotCx, dotCy, dpToPx(6f), mAccentPaint);
            canvas.drawText("Living Flow • 120Hz", mIslandRect.left + dpToPx(30f),
                    dotCy + spToPx(4f), mTextTitlePaint);
        }

        // 2. Middle Spatial Bento Cards (One UI 9 + ColorOS 17)
        mCardPaint.setColor(Color.parseColor(mIsDarkMode ? "#26FFFFFF" : "#38FFFFFF"));
        mCardStrokePaint.setColor(Color.parseColor("#42FFFFFF"));
        float cardTop = islandTop + islandH + dpToPx(12f);
        float cardBottom = h - dpToPx(58f);
        float midGap = dpToPx(10f);
        float sideMargin = dpToPx(16f);
        float cardW = (w - sideMargin * 2f - midGap) / 2f;

        mCardLeftRect.set(sideMargin, cardTop, sideMargin + cardW, cardBottom);
        mCardRightRect.set(sideMargin + cardW + midGap, cardTop, w - sideMargin, cardBottom);
        canvas.drawRoundRect(mCardLeftRect, dpToPx(18f), dpToPx(18f), mCardPaint);
        canvas.drawRoundRect(mCardLeftRect, dpToPx(18f), dpToPx(18f), mCardStrokePaint);
        canvas.drawRoundRect(mCardRightRect, dpToPx(18f), dpToPx(18f), mCardPaint);
        canvas.drawRoundRect(mCardRightRect, dpToPx(18f), dpToPx(18f), mCardStrokePaint);

        canvas.drawText("Laminar Flow", mCardLeftRect.left + dpToPx(12f),
                mCardLeftRect.top + dpToPx(22f), mTextTitlePaint);
        canvas.drawText("120Hz Yay Fiziği", mCardLeftRect.left + dpToPx(12f),
                mCardLeftRect.top + dpToPx(38f), mTextSubPaint);

        canvas.drawText("Liquid Glass", mCardRightRect.left + dpToPx(12f),
                mCardRightRect.top + dpToPx(22f), mTextTitlePaint);
        String styleLabel = mGlassStyle == 3 ? "iOS 27 Koyu Kristal"
                : (mGlassStyle == 0 ? "ColorOS + iOS Hibrit"
                : (mGlassStyle == 1 ? "ColorOS Buzlu Cam" : "Stok Material 3"));
        canvas.drawText(styleLabel, mCardRightRect.left + dpToPx(12f),
                mCardRightRect.top + dpToPx(38f), mTextSubPaint);

        // 3. Bottom iOS 27 Liquid Glass Tab Bar with Active Lens Pill
        float barH = dpToPx(36f);
        float barBottom = h - dpToPx(11f);
        float barSide = mFloatingTabBar ? dpToPx(28f) : dpToPx(12f);
        mTabBarRect.set(barSide, barBottom - barH, w - barSide, barBottom);

        if (mGlassStyle == 3) {
            mTabBarPaint.setColor(Color.parseColor("#CC0A0D18"));
            mTabBarStrokePaint.setColor(Color.parseColor("#66FFFFFF"));
            mLensPillPaint.setColor(Color.parseColor("#445E9EFF"));
        } else if (mGlassStyle == 0) {
            mTabBarPaint.setColor(Color.parseColor("#4218223C"));
            mTabBarStrokePaint.setColor(Color.parseColor("#75FFFFFF"));
            mLensPillPaint.setColor(Color.parseColor("#48FF9E44"));
        } else if (mGlassStyle == 1) {
            mTabBarPaint.setColor(Color.parseColor("#551E2436"));
            mTabBarStrokePaint.setColor(Color.parseColor("#40FFFFFF"));
            mLensPillPaint.setColor(Color.parseColor("#38FFFFFF"));
        } else {
            mTabBarPaint.setColor(Color.parseColor("#DD1F222A"));
            mTabBarStrokePaint.setColor(Color.parseColor("#22FFFFFF"));
            mLensPillPaint.setColor(Color.parseColor("#33A8C7FA"));
        }

        float barCorner = mFloatingTabBar ? barH / 2f : dpToPx(12f);
        canvas.drawRoundRect(mTabBarRect, barCorner, barCorner, mTabBarPaint);
        canvas.drawRoundRect(mTabBarRect, barCorner, barCorner, mTabBarStrokePaint);

        // Animated sliding active lens capsule inside the Liquid Glass Tab Bar
        float lensW = dpToPx(88f);
        float lensPad = dpToPx(4f);
        float slideFactor = 0.5f + 0.38f * (float) Math.sin(mPhase * 0.7f);
        float minLeft = mTabBarRect.left + lensPad;
        float maxLeft = mTabBarRect.right - lensPad - lensW;
        float lensLeft = minLeft + (maxLeft - minLeft) * slideFactor;
        mLensRect.set(lensLeft, mTabBarRect.top + lensPad, lensLeft + lensW, mTabBarRect.bottom - lensPad);
        canvas.drawRoundRect(mLensRect, (barH - lensPad * 2f) / 2f, (barH - lensPad * 2f) / 2f, mLensPillPaint);

        canvas.drawText("Ana Ekran", mTabBarRect.left + dpToPx(18f),
                mTabBarRect.centerY() + spToPx(3.8f), mTextSubPaint);
        canvas.drawText("BlazeHouse", mTabBarRect.centerX() - dpToPx(30f),
                mTabBarRect.centerY() + spToPx(3.8f), mTextTitlePaint);
        canvas.drawText("Ayarlar", mTabBarRect.right - dpToPx(56f),
                mTabBarRect.centerY() + spToPx(3.8f), mTextSubPaint);

        // Outer border
        canvas.drawRoundRect(mStageRect, stageRadius, stageRadius, mOuterBorderPaint);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}

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
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

/**
 * ColorOS 17 / iOS 27 Flagship OOBE Typewriter greeting text with glowing cursor,
 * Living Flow gradient subtitle, and Liquid Glass OS badge pill.
 */
public class TypewriterTextView extends View {

    private final Paint mBadgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBadgeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBadgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBadgeDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint mGreetingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSubtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBadgeRect = new RectF();

    private final String[] mGreetings = new String[]{
            "Merhaba.", "Hello.", "你好.", "BlazeAOSP 17."
    };
    private final String mSubtitle = "İpeksi Akıcılık. Living Flow ile Tanışın.";
    private final String mBadgeText = "ANDROID 17  •  BLAZE V5.0 OFFICIAL";

    private int mCurrentIndex = 0;
    private int mCharCount = 0;
    private boolean mShowCursor = true;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private ValueAnimator mCursorAnimator;
    private OnTypingCompleteListener mListener;

    public interface OnTypingCompleteListener {
        void onTypingComplete();
    }

    public TypewriterTextView(Context context) {
        this(context, null);
    }

    public TypewriterTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TypewriterTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mBadgeBgPaint.setColor(Color.parseColor("#24FFFFFF"));
        mBadgeBgPaint.setStyle(Paint.Style.FILL);

        mBadgeStrokePaint.setColor(Color.parseColor("#48FFFFFF"));
        mBadgeStrokePaint.setStyle(Paint.Style.STROKE);
        mBadgeStrokePaint.setStrokeWidth(dpToPx(1f));

        mBadgeDotPaint.setColor(Color.parseColor("#FF9E44"));
        mBadgeDotPaint.setStyle(Paint.Style.FILL);

        mBadgeTextPaint.setColor(Color.parseColor("#E6F0FF"));
        mBadgeTextPaint.setTextSize(spToPx(11f));
        mBadgeTextPaint.setLetterSpacing(0.08f);
        mBadgeTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        mGreetingPaint.setColor(Color.WHITE);
        mGreetingPaint.setTextSize(spToPx(40f));
        mGreetingPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        mGreetingPaint.setShadowLayer(dpToPx(12f), 0f, dpToPx(4f), Color.parseColor("#55000000"));

        mCursorPaint.setColor(Color.parseColor("#FF9E44"));
        mCursorPaint.setTextSize(spToPx(40f));
        mCursorPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        mSubtitlePaint.setTextSize(spToPx(17.5f));
        mSubtitlePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        mCursorAnimator = ValueAnimator.ofInt(0, 1);
        mCursorAnimator.setDuration(450);
        mCursorAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mCursorAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mCursorAnimator.addUpdateListener(anim -> {
            boolean show = anim.getCurrentPlayTime() % 900 < 450;
            if (show != mShowCursor) {
                mShowCursor = show;
                invalidate();
            }
        });
        mCursorAnimator.start();
    }

    public void startTyping(OnTypingCompleteListener listener) {
        mListener = listener;
        mCurrentIndex = 0;
        mCharCount = 0;
        mHandler.removeCallbacksAndMessages(null);
        typeNextChar();
    }

    private void typeNextChar() {
        String currentStr = mGreetings[mCurrentIndex];
        if (mCharCount < currentStr.length()) {
            mCharCount++;
            invalidate();
            mHandler.postDelayed(this::typeNextChar, 85);
        } else {
            mHandler.postDelayed(() -> {
                if (mCurrentIndex < mGreetings.length - 1) {
                    mCurrentIndex++;
                    mCharCount = 0;
                    invalidate();
                    mHandler.postDelayed(this::typeNextChar, 95);
                } else {
                    if (mListener != null) {
                        mListener.onTypingComplete();
                    }
                }
            }, 900);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desiredHeight = (int) dpToPx(128f);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0) {
            LinearGradient gradient = new LinearGradient(
                    0, 0, w * 0.85f, 0,
                    new int[]{
                            Color.parseColor("#FFE4B5"),
                            Color.parseColor("#FF9E6D"),
                            Color.parseColor("#D8B4FE")
                    },
                    null, Shader.TileMode.CLAMP
            );
            mSubtitlePaint.setShader(gradient);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacksAndMessages(null);
        if (mCursorAnimator != null) {
            mCursorAnimator.cancel();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 1. Liquid Glass OS Pill Badge
        float badgeHeight = dpToPx(26f);
        float textW = mBadgeTextPaint.measureText(mBadgeText);
        float badgeWidth = textW + dpToPx(34f);
        mBadgeRect.set(0f, dpToPx(2f), badgeWidth, dpToPx(2f) + badgeHeight);
        canvas.drawRoundRect(mBadgeRect, badgeHeight / 2f, badgeHeight / 2f, mBadgeBgPaint);
        canvas.drawRoundRect(mBadgeRect, badgeHeight / 2f, badgeHeight / 2f, mBadgeStrokePaint);

        float dotCx = dpToPx(13f);
        float dotCy = mBadgeRect.centerY();
        canvas.drawCircle(dotCx, dotCy, dpToPx(3.5f), mBadgeDotPaint);
        canvas.drawText(mBadgeText, dpToPx(23f), dotCy + spToPx(3.8f), mBadgeTextPaint);

        // 2. Typewriter Greeting
        String currentStr = mGreetings[mCurrentIndex];
        String visibleStr = currentStr.substring(0, Math.min(mCharCount, currentStr.length()));

        float greetingY = dpToPx(74f);
        canvas.drawText(visibleStr, 0f, greetingY, mGreetingPaint);

        if (mShowCursor) {
            float textWidth = mGreetingPaint.measureText(visibleStr);
            canvas.drawText(" |", textWidth, greetingY, mCursorPaint);
        }

        // 3. Living Flow Subtitle
        float subtitleY = greetingY + dpToPx(32f);
        canvas.drawText(mSubtitle, 0f, subtitleY, mSubtitlePaint);
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}

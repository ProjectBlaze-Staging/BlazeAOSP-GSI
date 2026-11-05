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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Touch container that renders organic fluid ripples on tap
 * and drives PeekingMascotView gaze tracking.
 */
public class TouchRippleLayout extends FrameLayout {

    private static class Ripple {
        float x;
        float y;
        float radius;
        int alpha;

        Ripple(float x, float y) {
            this.x = x;
            this.y = y;
            this.radius = 0f;
            this.alpha = 180;
        }
    }

    private final Paint mRipplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Ripple> mRipples = new ArrayList<>();
    private PeekingMascotView mMascotView;

    public TouchRippleLayout(Context context) {
        this(context, null);
    }

    public TouchRippleLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TouchRippleLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        mRipplePaint.setStyle(Paint.Style.STROKE);
        mRipplePaint.setStrokeWidth(dpToPx(1.8f));
        mRipplePaint.setColor(Color.parseColor("#FF6B8B")); // Soft peach-pink
    }

    public void setMascotView(PeekingMascotView mascotView) {
        mMascotView = mascotView;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        handleTouch(ev);
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        handleTouch(event);
        return true;
    }

    private void handleTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                addRipple(event.getX(), event.getY());
                if (mMascotView != null) {
                    mMascotView.setGaze(event.getRawX(), event.getRawY());
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mMascotView != null) {
                    mMascotView.setGaze(event.getRawX(), event.getRawY());
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mMascotView != null) {
                    postDelayed(() -> {
                        if (mMascotView != null) mMascotView.resetGaze();
                    }, 500);
                }
                break;
        }
    }

    private void addRipple(float x, float y) {
        final Ripple ripple = new Ripple(x, y);
        mRipples.add(ripple);

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(600);
        anim.setInterpolator(new DecelerateInterpolator());
        final float maxRadius = dpToPx(75f);
        anim.addUpdateListener(animation -> {
            float frac = (float) animation.getAnimatedValue();
            ripple.radius = frac * maxRadius;
            ripple.alpha = (int) (180 * (1f - frac));
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Iterator<Ripple> it = mRipples.iterator();
        while (it.hasNext()) {
            Ripple r = it.next();
            if (r.alpha <= 0) {
                it.remove();
            } else {
                mRipplePaint.setAlpha(r.alpha);
                canvas.drawCircle(r.x, r.y, r.radius, mRipplePaint);
            }
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}

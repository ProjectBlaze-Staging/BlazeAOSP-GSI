/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-FileCopyrightText: 2026 The BlazeAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import static org.lineageos.setupwizard.SetupWizardApp.LOGV;

import android.animation.Animator;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.widget.Button;

import com.google.android.setupcompat.util.SystemBarHelper;

import org.lineageos.setupwizard.util.OobeSpringHelper;
import org.lineageos.setupwizard.util.SetupWizardUtils;
import org.lineageos.setupwizard.widget.LuminousBloomView;

public class FinishActivity extends BaseSetupWizardActivity {

    public static final String TAG = FinishActivity.class.getSimpleName();

    private LuminousBloomView mBloomView;
    private View mContentContainer;
    private SetupWizardApp mSetupWizardApp;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private volatile boolean mIsFinishing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (LOGV) {
            logActivityState("onCreate savedInstanceState=" + savedInstanceState);
        }
        mSetupWizardApp = (SetupWizardApp) getApplication();
        mBloomView = findViewById(R.id.finish_bloom_view);
        mContentContainer = findViewById(R.id.finish_content_container);

        View glifLayout = findViewById(R.id.setup_wizard_layout);
        if (glifLayout != null) {
            View footer = glifLayout.findViewWithTag("suc_layout_footer");
            if (footer != null) {
                footer.setVisibility(View.GONE);
            }
        }

        Button startBtn = findViewById(R.id.start);
        if (startBtn != null) {
            OobeSpringHelper.attachPressSpring(startBtn);
            startBtn.setOnClickListener(view -> {
                OobeSpringHelper.vibrateSuccess(this);
                onNextPressed();
            });
        }

        setNextText(R.string.start);

        if (mContentContainer != null) {
            float d = getResources().getDisplayMetrics().density;
            mContentContainer.setAlpha(0f);
            mContentContainer.setTranslationY(18f * d);
            mContentContainer.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(420)
                    .start();
        }
        if (mBloomView != null) {
            mBloomView.startBloomAnimation(700, null);
        }
    }

    private void disableNavigation() {
        hideNextButton();
        SystemBarHelper.hideSystemBars(getWindow());
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.finish_activity;
    }

    @Override
    protected void onNextPressed() {
        startFinishSequence();
    }

    @Override
    public void onNavigateNext() {
        startFinishSequence();
    }

    private void startFinishSequence() {
        if (mIsFinishing) {
            return;
        }
        mIsFinishing = true;

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        disableNavigation();

        final View rootView = findViewById(R.id.setup_wizard_layout);
        if (rootView != null && rootView.isAttachedToWindow()) {
            final int cx = rootView.getWidth() / 2;
            final int cy = rootView.getHeight() / 2;
            final float maxRadius = (float) Math.hypot(cx, cy);
            try {
                Animator anim = ViewAnimationUtils.createCircularReveal(
                        rootView, cx, cy, maxRadius, 0f);
                anim.setDuration(420);
                anim.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        mHandler.post(() -> {
                            if (LOGV) {
                                Log.v(TAG, "Animation started");
                            }
                        });
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mHandler.post(() -> {
                            rootView.setVisibility(View.INVISIBLE);
                            completeSetup();
                        });
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {}

                    @Override
                    public void onAnimationRepeat(Animator animation) {}
                });
                anim.start();
                return;
            } catch (Exception ignored) {}
        }
        completeSetup();
    }

    private void completeSetup() {
        handleWallpaperTheme(mSetupWizardApp);
        SetupWizardUtils.finishSetupWizard(FinishActivity.this);
    }

    private static void handleWallpaperTheme(Context context) {
        Bundle privacyData = SetupWizardApp.getSettingsBundle();
        if (privacyData != null && privacyData.containsKey("enable_dark_theme")) {
            Settings.Secure.putIntForUser(context.getContentResolver(),
                    "ui_night_mode",
                    privacyData.getBoolean("enable_dark_theme") ? 2 : 1,
                    UserHandle.USER_CURRENT);
        }
    }
}

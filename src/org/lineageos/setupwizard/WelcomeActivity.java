/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import static org.lineageos.setupwizard.SetupWizardApp.ACTION_EMERGENCY_DIAL;

import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.setupcompat.template.FooterButtonStyleUtils;
import com.google.android.setupcompat.util.SystemBarHelper;

import org.lineageos.setupwizard.util.SetupWizardUtils;

public class WelcomeActivity extends SubBaseActivity {

    private static final String ACTION_ACCESSIBILITY_SETTINGS =
            "android.settings.ACCESSIBILITY_SETTINGS_FOR_SUW";

    @Override
    protected void onStartSubactivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onSetupStart();
        SystemBarHelper.setBackButtonVisible(getWindow(), false);
        setNextText(R.string.start);
        Button startButton = findViewById(R.id.start);
        Button emergButton = findViewById(R.id.emerg_dialer);
        Button skipButton = findViewById(R.id.skip);
        startButton.setOnClickListener(view -> onNextPressed());
        findViewById(R.id.launch_accessibility)
                .setOnClickListener(
                        view -> startSubactivity(new Intent(ACTION_ACCESSIBILITY_SETTINGS)));

        if (SetupWizardUtils.hasTelephony(this)) {
            setSkipText(R.string.emergency_call);
            emergButton.setOnClickListener(
                    view -> startSubactivity(new Intent(ACTION_EMERGENCY_DIAL)));
        } else {
            emergButton.setVisibility(View.GONE);
        }

        TextView welcomeTitle = findViewById(R.id.welcome_title);
        if (SetupWizardUtils.isManagedProfile(this)) {
            welcomeTitle.setText(getString(R.string.setup_managed_profile_welcome_message));
        } else {
            welcomeTitle.setText(getString(R.string.setup_welcome_message,
                    getString(R.string.os_name)));
        }

        if (Build.TYPE.equals("eng")) {
            skipButton.setVisibility(View.VISIBLE);
            skipButton.setOnClickListener(v -> {
                SetupWizardUtils.finishSetupWizard(WelcomeActivity.this);
            });
        }

        // Initialize and choreograph ColorOS 17 opening animation suite
        org.lineageos.setupwizard.widget.LuminousBloomView bloomView = findViewById(R.id.bloom_background);
        org.lineageos.setupwizard.widget.PeekingMascotView mascotView = findViewById(R.id.peeking_mascot);
        org.lineageos.setupwizard.widget.TouchRippleLayout touchLayout = findViewById(R.id.touch_ripple_layout);
        org.lineageos.setupwizard.widget.TypewriterTextView typewriter = findViewById(R.id.typewriter_text);
        org.lineageos.setupwizard.widget.ParticleConvergenceView particles = findViewById(R.id.particle_convergence);
        View accessibilityButton = findViewById(R.id.launch_accessibility);

        org.lineageos.setupwizard.util.OobeSpringHelper.attachTouchSpring(startButton, 0.94f);
        org.lineageos.setupwizard.util.OobeSpringHelper.attachTouchSpring(emergButton, 0.95f);
        org.lineageos.setupwizard.util.OobeSpringHelper.attachTouchSpring(accessibilityButton, 0.95f);
        org.lineageos.setupwizard.util.OobeSpringHelper.attachTouchSpring(skipButton, 0.95f);
        org.lineageos.setupwizard.util.OobeSpringHelper.playEntranceCascade(
                typewriter, particles, startButton, emergButton, accessibilityButton);

        if (touchLayout != null && mascotView != null) {
            touchLayout.setMascotView(mascotView);
        }

        if (bloomView != null) {
            bloomView.startBloomAnimation(2600, () -> {
                if (typewriter != null) {
                    typewriter.startTyping(() -> {
                        if (particles != null) {
                            particles.startConvergence(1800);
                        }
                    });
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
    }

    @Override
    protected com.google.android.setupdesign.GlifLayout getGlifLayout() {
        return null;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.welcome_activity;
    }

    @Override
    protected int getTitleResId() {
        return -1;
    }
}

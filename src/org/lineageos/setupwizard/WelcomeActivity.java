/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-FileCopyrightText: 2026 The BlazeAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.internal.app.LocalePicker;
import com.android.internal.util.EmergencyAffordanceManager;

import org.lineageos.setupwizard.util.OobeSpringHelper;
import org.lineageos.setupwizard.util.SetupWizardUtils;
import org.lineageos.setupwizard.widget.LuminousBloomView;

import java.util.List;
import java.util.Locale;

public class WelcomeActivity extends SubBaseActivity {

    private LuminousBloomView mBloomView;
    private View mContentContainer;
    private TextView mCurrentLocaleText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onSetupStart();
        setNextAllowed(true);
    }

    @Override
    protected void onStartSubactivity() {
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.welcome_activity;
    }

    @Override
    protected int getTitleResId() {
        return -1;
    }

    @Override
    protected int getIconResId() {
        return -1;
    }

    @Override
    protected com.google.android.setupdesign.GlifLayout getGlifLayout() {
        return null;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        View glifLayout = findViewById(R.id.setup_wizard_layout);
        if (glifLayout != null) {
            View footer = glifLayout.findViewWithTag("suc_layout_footer");
            if (footer != null) {
                footer.setVisibility(View.GONE);
            }
        }

        mBloomView = findViewById(R.id.luminous_bloom_view);
        mContentContainer = findViewById(R.id.welcome_content_container);
        mCurrentLocaleText = findViewById(R.id.current_locale_text);

        Button startButton = findViewById(R.id.start);
        Button emergButton = findViewById(R.id.emerg_button);
        Button accessibilityButton = findViewById(R.id.accessibility_button);
        View langCard = findViewById(R.id.language_selector_card);

        updateDisplayedLocale();

        if (startButton != null) {
            OobeSpringHelper.attachPressSpring(startButton);
            startButton.setOnClickListener(view -> {
                OobeSpringHelper.vibrateSuccess(this);
                onNextPressed();
            });
        }

        if (langCard != null) {
            OobeSpringHelper.attachPressSpring(langCard);
            langCard.setOnClickListener(v -> {
                OobeSpringHelper.vibrateTick(this);
                showQuickLocaleDialog();
            });
        }

        if (accessibilityButton != null) {
            OobeSpringHelper.attachPressSpring(accessibilityButton);
            accessibilityButton.setOnClickListener(view -> {
                OobeSpringHelper.vibrateTick(this);
                try {
                    Intent intent = new Intent("com.android.settings.ACCESSIBILITY_COLOR_SPACE_SETTINGS");
                    if (intent.resolveActivity(getPackageManager()) == null) {
                        intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    }
                    startSubactivity(intent);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception ignored) {}
                }
            });
        }

        if (emergButton != null) {
            if (SetupWizardUtils.hasTelephony(this)) {
                emergButton.setVisibility(View.VISIBLE);
                OobeSpringHelper.attachPressSpring(emergButton);
                emergButton.setOnClickListener(view -> {
                    OobeSpringHelper.vibrateTick(this);
                    startSubactivity(new Intent(SetupWizardApp.ACTION_EMERGENCY_DIAL));
                });

                if (new EmergencyAffordanceManager(this).needsEmergencyAffordance()) {
                    emergButton.setOnLongClickListener(view -> {
                        new EmergencyAffordanceManager(WelcomeActivity.this).performEmergencyCall();
                        return true;
                    });
                }
            } else {
                emergButton.setVisibility(View.GONE);
            }
        }

        runEntranceChoreography();
    }

    private void updateDisplayedLocale() {
        if (mCurrentLocaleText == null) return;
        Locale current = Locale.getDefault();
        String display = current.getDisplayName(current);
        if (display != null && !display.isEmpty()) {
            display = display.substring(0, 1).toUpperCase(current) + display.substring(1);
            mCurrentLocaleText.setText(display);
        }
    }

    private void showQuickLocaleDialog() {
        try {
            final List<LocalePicker.LocaleInfo> locales = LocalePicker.getAllAssetLocales(this, false);
            if (locales == null || locales.isEmpty()) return;

            final CharSequence[] labels = new CharSequence[locales.size()];
            Locale current = Locale.getDefault();
            int checkedIndex = 0;

            for (int i = 0; i < locales.size(); i++) {
                LocalePicker.LocaleInfo info = locales.get(i);
                labels[i] = info.getLabel();
                if (info.getLocale() != null && info.getLocale().getLanguage().equals(current.getLanguage())) {
                    checkedIndex = i;
                }
            }

            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(R.string.setup_locale)
                    .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                        LocalePicker.LocaleInfo chosen = locales.get(which);
                        if (chosen != null && chosen.getLocale() != null) {
                            OobeSpringHelper.vibrateSuccess(this);
                            LocalePicker.updateLocale(chosen.getLocale());
                            updateDisplayedLocale();
                        }
                        dialog.dismiss();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Throwable ignored) {}
    }

    private void runEntranceChoreography() {
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

    @Override
    public void onBackPressed() {
    }
}

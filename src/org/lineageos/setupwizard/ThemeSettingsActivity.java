/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-FileCopyrightText: 2026 The BlazeAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.app.UiModeManager;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.lineageos.setupwizard.util.OobeSpringHelper;
import org.lineageos.setupwizard.widget.OobeLivePreviewView;

public class ThemeSettingsActivity extends BaseSetupWizardActivity {

    private View mRootLayout;
    private TextView mPageTitle;
    private TextView mPageSubtitle;
    private TextView mSectionTheme;
    private TextView mSectionGlass;

    private OobeLivePreviewView mLivePreview;
    private View mCardLight;
    private View mCardDark;
    private RadioButton mRadioLight;
    private RadioButton mRadioDark;

    private View mCardGlassCrystal;
    private View mCardGlassLiquid;
    private View mCardGlassFrosted;
    private RadioButton mRadioGlassCrystal;
    private RadioButton mRadioGlassLiquid;
    private RadioButton mRadioGlassFrosted;

    private View mCardDynamicIsland;
    private Switch mSwitchDynamicIsland;

    private TextView[] mPrimaryTexts;
    private TextView[] mSecondaryTexts;

    private boolean mIsDarkMode = true;
    private int mSelectedGlassStyle = 3; // 3 = Dark Smoked Crystal, 0 = Liquid Glass, 1 = Soft Frosted
    private boolean mDynamicIslandEnabled = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setNextText(R.string.next);

        mRootLayout = findViewById(R.id.setup_wizard_layout);
        mPageTitle = findViewById(R.id.theme_page_title);
        mPageSubtitle = findViewById(R.id.theme_page_subtitle);
        mSectionTheme = findViewById(R.id.section_label_theme);
        mSectionGlass = findViewById(R.id.section_label_glass);

        mLivePreview = findViewById(R.id.oobe_live_preview);
        mCardLight = findViewById(R.id.card_theme_light);
        mCardDark = findViewById(R.id.card_theme_dark);
        mRadioLight = findViewById(R.id.radio_light);
        mRadioDark = findViewById(R.id.radio_dark);

        mCardGlassCrystal = findViewById(R.id.card_glass_dark_crystal);
        mCardGlassLiquid = findViewById(R.id.card_glass_liquid);
        mCardGlassFrosted = findViewById(R.id.card_glass_frosted);
        mRadioGlassCrystal = findViewById(R.id.radio_glass_crystal);
        mRadioGlassLiquid = findViewById(R.id.radio_glass_liquid);
        mRadioGlassFrosted = findViewById(R.id.radio_glass_frosted);

        mCardDynamicIsland = findViewById(R.id.card_dynamic_island);
        mSwitchDynamicIsland = findViewById(R.id.switch_dynamic_island);
        Button nextBtn = findViewById(R.id.theme_next_button);

        mPrimaryTexts = new TextView[]{
                findViewById(R.id.title_theme_light),
                findViewById(R.id.title_theme_dark),
                findViewById(R.id.title_glass_crystal),
                findViewById(R.id.title_glass_liquid),
                findViewById(R.id.title_glass_frosted),
                findViewById(R.id.title_dynamic_island)
        };
        mSecondaryTexts = new TextView[]{
                findViewById(R.id.sub_theme_light),
                findViewById(R.id.sub_theme_dark),
                findViewById(R.id.sub_glass_crystal),
                findViewById(R.id.sub_glass_liquid),
                findViewById(R.id.sub_glass_frosted),
                findViewById(R.id.sub_dynamic_island)
        };

        int nightSetting = Settings.Secure.getIntForUser(
                getContentResolver(), "ui_night_mode", -1, UserHandle.USER_CURRENT);
        if (nightSetting == 1) {
            mIsDarkMode = false;
        } else if (nightSetting == 2) {
            mIsDarkMode = true;
        } else {
            mIsDarkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
        }

        mSelectedGlassStyle = Settings.Secure.getIntForUser(
                getContentResolver(), "blaze_ui_glass_style", 3, UserHandle.USER_CURRENT);
        mDynamicIslandEnabled = Settings.Secure.getIntForUser(
                getContentResolver(), "dynamic_bar_enabled", 1, UserHandle.USER_CURRENT) != 0;

        if (mCardLight != null) {
            OobeSpringHelper.attachPressSpring(mCardLight);
            mCardLight.setOnClickListener(v -> selectDarkMode(false));
        }
        if (mCardDark != null) {
            OobeSpringHelper.attachPressSpring(mCardDark);
            mCardDark.setOnClickListener(v -> selectDarkMode(true));
        }

        if (mCardGlassCrystal != null) {
            OobeSpringHelper.attachPressSpring(mCardGlassCrystal);
            mCardGlassCrystal.setOnClickListener(v -> selectGlassStyle(3));
        }
        if (mCardGlassLiquid != null) {
            OobeSpringHelper.attachPressSpring(mCardGlassLiquid);
            mCardGlassLiquid.setOnClickListener(v -> selectGlassStyle(0));
        }
        if (mCardGlassFrosted != null) {
            OobeSpringHelper.attachPressSpring(mCardGlassFrosted);
            mCardGlassFrosted.setOnClickListener(v -> selectGlassStyle(1));
        }

        if (mCardDynamicIsland != null && mSwitchDynamicIsland != null) {
            mSwitchDynamicIsland.setChecked(mDynamicIslandEnabled);
            OobeSpringHelper.attachPressSpring(mCardDynamicIsland);
            mCardDynamicIsland.setOnClickListener(v -> {
                mDynamicIslandEnabled = !mDynamicIslandEnabled;
                mSwitchDynamicIsland.setChecked(mDynamicIslandEnabled);
                applyDynamicIslandSetting(mDynamicIslandEnabled);
                OobeSpringHelper.vibrateTick(this);
                refreshSelectionUi();
            });
            mSwitchDynamicIsland.setOnCheckedChangeListener((btn, isChecked) -> {
                if (mDynamicIslandEnabled != isChecked) {
                    mDynamicIslandEnabled = isChecked;
                    applyDynamicIslandSetting(isChecked);
                    OobeSpringHelper.vibrateTick(this);
                    refreshSelectionUi();
                }
            });
        }

        if (nextBtn != null) {
            OobeSpringHelper.attachPressSpring(nextBtn);
            nextBtn.setOnClickListener(v -> {
                OobeSpringHelper.vibrateSuccess(this);
                onNextPressed();
            });
        }

        refreshSelectionUi();
    }

    private void selectDarkMode(boolean dark) {
        mIsDarkMode = dark;
        OobeSpringHelper.vibrateTick(this);

        // Persist night mode in Secure Settings + UiModeManager so it applies both during SUW and after SUW
        try {
            SetupWizardApp.getSettingsBundle().putBoolean("enable_dark_theme", dark);
            Settings.Secure.putIntForUser(
                    getContentResolver(), "ui_night_mode", dark ? 2 : 1, UserHandle.USER_CURRENT);
            Settings.Secure.putIntForUser(
                    getContentResolver(), "ui_night_mode_custom_type", -1, UserHandle.USER_CURRENT);
        } catch (Throwable ignored) {}

        try {
            UiModeManager uiModeManager = getSystemService(UiModeManager.class);
            if (uiModeManager != null) {
                uiModeManager.setNightMode(dark ? UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_NO);
                uiModeManager.setNightModeActivated(dark);
            }
        } catch (Throwable ignored) {}

        refreshSelectionUi();
    }

    private void selectGlassStyle(int style) {
        mSelectedGlassStyle = style;
        OobeSpringHelper.vibrateTick(this);
        try {
            Settings.Secure.putIntForUser(
                    getContentResolver(), "blaze_ui_glass_style", style, UserHandle.USER_CURRENT);
            SystemProperties.set("persist.sys.blaze.glass_style", String.valueOf(style));
            Settings.System.putIntForUser(
                    getContentResolver(),
                    "blaze_ui_glass_style_version",
                    (int) (System.currentTimeMillis() & 0x7FFFFFFF),
                    UserHandle.USER_CURRENT);
        } catch (Throwable ignored) {}
        refreshSelectionUi();
    }

    private void applyDynamicIslandSetting(boolean enabled) {
        try {
            Settings.Secure.putIntForUser(
                    getContentResolver(), "dynamic_bar_enabled", enabled ? 1 : 0, UserHandle.USER_CURRENT);
            Settings.Secure.putIntForUser(
                    getContentResolver(), "dynamic_bar_keyguard_enable", enabled ? 1 : 0, UserHandle.USER_CURRENT);
        } catch (Throwable ignored) {}
    }

    private void refreshSelectionUi() {
        // 1. Live Activity Surface & Text Theme Update (Dark vs Light)
        int bgColor = mIsDarkMode ? 0xFF0C0F16 : 0xFFF7F9FC;
        int titleColor = mIsDarkMode ? 0xFFF8FAFC : 0xFF0F172A;
        int subtitleColor = mIsDarkMode ? 0xFF94A3B8 : 0xFF475569;
        int sectionColor = mIsDarkMode ? 0xFF60A5FA : 0xFF2563EB;

        if (mRootLayout != null) mRootLayout.setBackgroundColor(bgColor);
        if (mPageTitle != null) mPageTitle.setTextColor(titleColor);
        if (mPageSubtitle != null) mPageSubtitle.setTextColor(subtitleColor);
        if (mSectionTheme != null) mSectionTheme.setTextColor(sectionColor);
        if (mSectionGlass != null) mSectionGlass.setTextColor(sectionColor);

        if (mPrimaryTexts != null) {
            for (TextView tv : mPrimaryTexts) {
                if (tv != null) tv.setTextColor(titleColor);
            }
        }
        if (mSecondaryTexts != null) {
            for (TextView tv : mSecondaryTexts) {
                if (tv != null) tv.setTextColor(subtitleColor);
            }
        }

        try {
            getWindow().setStatusBarColor(bgColor);
            getWindow().setNavigationBarColor(bgColor);
            WindowInsetsController wic = getWindow().getInsetsController();
            if (wic != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                wic.setSystemBarsAppearance(mIsDarkMode ? 0 : mask, mask);
            }
        } catch (Throwable ignored) {}

        // 2. Radio & Card Borders
        if (mRadioLight != null) mRadioLight.setChecked(!mIsDarkMode);
        if (mRadioDark != null) mRadioDark.setChecked(mIsDarkMode);
        styleOptionCard(mCardLight, !mIsDarkMode);
        styleOptionCard(mCardDark, mIsDarkMode);

        if (mRadioGlassCrystal != null) mRadioGlassCrystal.setChecked(mSelectedGlassStyle == 3);
        if (mRadioGlassLiquid != null) mRadioGlassLiquid.setChecked(mSelectedGlassStyle == 0);
        if (mRadioGlassFrosted != null) mRadioGlassFrosted.setChecked(mSelectedGlassStyle == 1);
        styleOptionCard(mCardGlassCrystal, mSelectedGlassStyle == 3);
        styleOptionCard(mCardGlassLiquid, mSelectedGlassStyle == 0);
        styleOptionCard(mCardGlassFrosted, mSelectedGlassStyle == 1);

        styleOptionCard(mCardDynamicIsland, mDynamicIslandEnabled);

        if (mLivePreview != null) {
            mLivePreview.updateState(mIsDarkMode, mSelectedGlassStyle, mDynamicIslandEnabled);
        }
    }

    private void styleOptionCard(View card, boolean selected) {
        if (card == null) return;
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(18f * d);
        if (mIsDarkMode) {
            if (selected) {
                gd.setColor(0xFF17233B);
                gd.setStroke(Math.round(1.6f * d), 0xFF3B82F6);
            } else {
                gd.setColor(0xFF141923);
                gd.setStroke(Math.round(1.0f * d), 0x22FFFFFF);
            }
        } else {
            if (selected) {
                gd.setColor(0xFFEFF6FF);
                gd.setStroke(Math.round(1.6f * d), 0xFF2563EB);
            } else {
                gd.setColor(0xFFFFFFFF);
                gd.setStroke(Math.round(1.0f * d), 0xFFE2E8F0);
            }
        }
        card.setBackground(gd);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.setup_theme;
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
}

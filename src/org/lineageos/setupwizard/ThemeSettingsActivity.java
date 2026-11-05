/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2026 The BlazeAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.app.UiModeManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.settingslib.Utils;

import org.lineageos.setupwizard.util.OobeSpringHelper;
import org.lineageos.setupwizard.widget.OobeLivePreviewView;

/**
 * Flagship OOBE Visual Design, Liquid Glass & Dynamic Island Customization Studio.
 */
public class ThemeSettingsActivity extends BaseSetupWizardActivity {

    private static final String KEY_UI_GLASS_STYLE = "blaze_ui_glass_style";
    private static final String KEY_GLASS_TAB_BARS = "blaze_glass_tab_bars";
    private static final String KEY_ANIMATION_ENGINE = "blaze_animation_engine";
    private static final String KEY_DYNAMIC_BAR_NOTIF_MODE = "dynamic_bar_notification_mode";

    private OobeLivePreviewView mLivePreview;
    private View mCardDark;
    private View mCardLight;
    private View mCardGlassIos27;
    private View mCardGlassHybrid;
    private View mCardGlassFrosted;
    private View mCardIslandMessages;
    private View mCardFloatingTabs;
    private TextView mSummaryIslandMessages;
    private TextView mSummaryFloatingTabs;

    private boolean mIsDarkMode;
    private int mGlassStyle = 3;
    private boolean mIslandMessagesEnabled = true;
    private boolean mFloatingTabsEnabled = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getGlifLayout().setDescriptionText(
                "Living Flow 120Hz, iOS 27 Liquid Glass ve Dinamik Ada deneyimini özelleştirin.");

        UiModeManager uiModeManager = getSystemService(UiModeManager.class);
        mIsDarkMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        mGlassStyle = Settings.Secure.getInt(
                getContentResolver(), KEY_UI_GLASS_STYLE, 3);
        mIslandMessagesEnabled = Settings.Secure.getInt(
                getContentResolver(), KEY_DYNAMIC_BAR_NOTIF_MODE, 0) != 2;
        mFloatingTabsEnabled = Settings.Secure.getInt(
                getContentResolver(), KEY_GLASS_TAB_BARS, 1) == 1;

        mLivePreview = findViewById(R.id.oobe_live_preview);
        mCardDark = findViewById(R.id.card_theme_dark);
        mCardLight = findViewById(R.id.card_theme_light);
        mCardGlassIos27 = findViewById(R.id.card_glass_ios27);
        mCardGlassHybrid = findViewById(R.id.card_glass_hybrid);
        mCardGlassFrosted = findViewById(R.id.card_glass_frosted);
        mCardIslandMessages = findViewById(R.id.card_toggle_island_messages);
        mCardFloatingTabs = findViewById(R.id.card_toggle_floating_tabs);
        mSummaryIslandMessages = findViewById(R.id.summary_island_messages);
        mSummaryFloatingTabs = findViewById(R.id.summary_floating_tabs);

        final RadioGroup radioGroup = findViewById(R.id.theme_radio_group);
        if (radioGroup != null) {
            radioGroup.check(mIsDarkMode ? R.id.radio_dark : R.id.radio_light);
            radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.radio_dark) {
                    uiModeManager.setNightModeActivated(true);
                } else if (checkedId == R.id.radio_light) {
                    uiModeManager.setNightModeActivated(false);
                }
            });
        }

        if (mCardDark != null) {
            OobeSpringHelper.attachTouchSpring(mCardDark, 0.95f);
            mCardDark.setOnClickListener(v -> {
                mIsDarkMode = true;
                refreshSelectionVisuals();
                if (uiModeManager != null) {
                    uiModeManager.setNightModeActivated(true);
                }
            });
        }

        if (mCardLight != null) {
            OobeSpringHelper.attachTouchSpring(mCardLight, 0.95f);
            mCardLight.setOnClickListener(v -> {
                mIsDarkMode = false;
                refreshSelectionVisuals();
                if (uiModeManager != null) {
                    uiModeManager.setNightModeActivated(false);
                }
            });
        }

        if (mCardGlassIos27 != null) {
            OobeSpringHelper.attachTouchSpring(mCardGlassIos27, 0.96f);
            mCardGlassIos27.setOnClickListener(v -> selectGlassStyle(3));
        }
        if (mCardGlassHybrid != null) {
            OobeSpringHelper.attachTouchSpring(mCardGlassHybrid, 0.96f);
            mCardGlassHybrid.setOnClickListener(v -> selectGlassStyle(0));
        }
        if (mCardGlassFrosted != null) {
            OobeSpringHelper.attachTouchSpring(mCardGlassFrosted, 0.96f);
            mCardGlassFrosted.setOnClickListener(v -> selectGlassStyle(1));
        }

        if (mCardIslandMessages != null) {
            OobeSpringHelper.attachTouchSpring(mCardIslandMessages, 0.96f);
            mCardIslandMessages.setOnClickListener(v -> {
                mIslandMessagesEnabled = !mIslandMessagesEnabled;
                Settings.Secure.putInt(getContentResolver(), KEY_DYNAMIC_BAR_NOTIF_MODE,
                        mIslandMessagesEnabled ? 0 : 2);
                if (mLivePreview != null) {
                    mLivePreview.cycleIslandMode();
                }
                refreshSelectionVisuals();
            });
        }

        if (mCardFloatingTabs != null) {
            OobeSpringHelper.attachTouchSpring(mCardFloatingTabs, 0.96f);
            mCardFloatingTabs.setOnClickListener(v -> {
                mFloatingTabsEnabled = !mFloatingTabsEnabled;
                Settings.Secure.putInt(getContentResolver(), KEY_GLASS_TAB_BARS,
                        mFloatingTabsEnabled ? 1 : 0);
                Settings.Secure.putInt(getContentResolver(), KEY_ANIMATION_ENGINE, 0);
                refreshSelectionVisuals();
            });
        }

        refreshSelectionVisuals();
        OobeSpringHelper.playEntranceCascade(
                mLivePreview, mCardDark, mCardLight,
                mCardGlassIos27, mCardGlassHybrid, mCardGlassFrosted,
                mCardIslandMessages, mCardFloatingTabs);
    }

    private void selectGlassStyle(int style) {
        mGlassStyle = style;
        Settings.Secure.putInt(getContentResolver(), KEY_UI_GLASS_STYLE, style);
        refreshSelectionVisuals();
    }

    private void refreshSelectionVisuals() {
        int accent = Utils.getColorAccentDefaultColor(this);
        applyCardBackground(mCardDark, mIsDarkMode, accent);
        applyCardBackground(mCardLight, !mIsDarkMode, accent);

        applyCardBackground(mCardGlassIos27, mGlassStyle == 3, accent);
        applyCardBackground(mCardGlassHybrid, mGlassStyle == 0, accent);
        applyCardBackground(mCardGlassFrosted, mGlassStyle == 1, accent);

        applyCardBackground(mCardIslandMessages, mIslandMessagesEnabled, accent);
        applyCardBackground(mCardFloatingTabs, mFloatingTabsEnabled, accent);

        if (mSummaryIslandMessages != null) {
            mSummaryIslandMessages.setText(mIslandMessagesEnabled
                    ? "Aktif • Gelen mesajlar ve sayaçlar Dinamik Ada'da açılır"
                    : "Standart • Klasik Android bildirim balonları kullanılır");
        }
        if (mSummaryFloatingTabs != null) {
            mSummaryFloatingTabs.setText(mFloatingTabsEnabled
                    ? "Aktif • Yüzen Liquid Glass bar ve 120Hz Laminar Flow yay fiziği"
                    : "Standart • Klasik alt sekme görünümü");
        }
        if (mLivePreview != null) {
            mLivePreview.setPreviewConfig(mIsDarkMode, mGlassStyle, mFloatingTabsEnabled);
        }
    }

    private void applyCardBackground(View view, boolean selected, int accentColor) {
        if (view == null) return;
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(18f * density);
        if (selected) {
            int fill = Color.argb(38, Color.red(accentColor),
                    Color.green(accentColor), Color.blue(accentColor));
            gd.setColor(fill);
            gd.setStroke((int) (2.0f * density), accentColor);
        } else {
            int surfaceFill = mIsDarkMode
                    ? Color.parseColor("#18FFFFFF")
                    : Color.parseColor("#12000000");
            int stroke = mIsDarkMode
                    ? Color.parseColor("#2AFFFFFF")
                    : Color.parseColor("#22000000");
            gd.setColor(surfaceFill);
            gd.setStroke((int) (1.0f * density), stroke);
        }
        view.setBackground(gd);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.setup_theme;
    }

    @Override
    protected int getTitleResId() {
        return R.string.setup_theme;
    }

    @Override
    protected int getIconResId() {
        return R.drawable.ic_theme;
    }
}

package com.wexresprise.margytube;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

/**
 * Every preference lives here. Appearance setters bump a version so open screens
 * recreate themselves exactly once, keeping the Material theme honest instead of
 * faking colours after the fact.
 */
public final class Prefs {
    private static final String FILE = "margytube";

    public static final String ACCENT_DYNAMIC = "dynamic";
    public static final String ACCENT_GREEN = "green";
    public static final String ACCENT_BLUE = "blue";
    public static final String ACCENT_PURPLE = "purple";
    public static final String ACCENT_PINK = "pink";
    public static final String ACCENT_ORANGE = "orange";
    public static final String ACCENT_RED = "red";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final int CARD_ELEVATED = 0;
    public static final int CARD_FILLED = 1;
    public static final int CARD_OUTLINED = 2;

    private static final String KEY_ACCENT = "accent";
    private static final String KEY_THEME = "theme";
    private static final String KEY_CARD = "card_style";
    private static final String KEY_RADIUS = "radius";
    private static final String KEY_FONT = "font_scale";
    private static final String KEY_API = "api_key";
    private static final String KEY_TOKEN = "oauth_token";
    private static final String KEY_REGION = "region";
    private static final String KEY_STYLE_VERSION = "style_version";

    private Prefs() {
    }

    public static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String accent(Context c) {
        return sp(c).getString(KEY_ACCENT, ACCENT_DYNAMIC);
    }

    public static void setAccent(Context c, String value) {
        sp(c).edit().putString(KEY_ACCENT, value).apply();
        bumpStyle(c);
    }

    public static int themeMode(Context c) {
        return sp(c).getInt(KEY_THEME, THEME_SYSTEM);
    }

    public static void setThemeMode(Context c, int mode) {
        sp(c).edit().putInt(KEY_THEME, mode).apply();
        applyThemeMode(c);
    }

    public static int cardStyle(Context c) {
        return sp(c).getInt(KEY_CARD, CARD_FILLED);
    }

    public static void setCardStyle(Context c, int style) {
        sp(c).edit().putInt(KEY_CARD, style).apply();
        bumpStyle(c);
    }

    public static int radius(Context c) {
        return sp(c).getInt(KEY_RADIUS, 16);
    }

    public static void setRadius(Context c, int dp) {
        sp(c).edit().putInt(KEY_RADIUS, dp).apply();
        bumpStyle(c);
    }

    public static int fontScale(Context c) {
        return sp(c).getInt(KEY_FONT, 100);
    }

    public static void setFontScale(Context c, int percent) {
        sp(c).edit().putInt(KEY_FONT, percent).apply();
        bumpStyle(c);
    }

    public static String apiKey(Context c) {
        return sp(c).getString(KEY_API, "");
    }

    public static void setApiKey(Context c, String key) {
        sp(c).edit().putString(KEY_API, key == null ? "" : key.trim()).apply();
    }

    public static String token(Context c) {
        return sp(c).getString(KEY_TOKEN, "");
    }

    public static void setToken(Context c, String token) {
        sp(c).edit().putString(KEY_TOKEN, token == null ? "" : token.trim()).apply();
    }

    public static String region(Context c) {
        return sp(c).getString(KEY_REGION, "RU");
    }

    public static void setRegion(Context c, String code) {
        sp(c).edit().putString(KEY_REGION, code).apply();
    }

    public static int styleVersion(Context c) {
        return sp(c).getInt(KEY_STYLE_VERSION, 0);
    }

    public static void bumpStyle(Context c) {
        int v = styleVersion(c) + 1;
        sp(c).edit().putInt(KEY_STYLE_VERSION, v).apply();
    }

    public static void applyThemeMode(Context c) {
        int mode = themeMode(c);
        int night = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (mode == THEME_LIGHT) {
            night = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (mode == THEME_DARK) {
            night = AppCompatDelegate.MODE_NIGHT_YES;
        }
        if (AppCompatDelegate.getDefaultNightMode() != night) {
            AppCompatDelegate.setDefaultNightMode(night);
        }
    }

    /**
     * Scales text relative to the system setting, never replacing it.
     * A new configuration context is what makes the font slider affect every view,
     * including alert dialogs and the bottom sheet, not just labels set by hand.
     */
    public static Context wrap(Context base) {
        float factor = fontScale(base) / 100f;
        if (Math.abs(factor - 1f) < 0.001f) {
            return base;
        }
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.fontScale = configuration.fontScale * factor;
        return base.createConfigurationContext(configuration);
    }

    public static boolean dynamicColorAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static void applyAccent(AppCompatActivity activity) {
        String accent = accent(activity);
        if (ACCENT_DYNAMIC.equals(accent) && dynamicColorAvailable()) {
            DynamicColors.applyToActivityIfAvailable(activity);
        } else {
            activity.getTheme().applyStyle(overlayFor(accent), true);
        }
    }

    private static int overlayFor(String accent) {
        switch (accent) {
            case ACCENT_BLUE:
                return R.style.ThemeOverlay_MargyTube_Blue;
            case ACCENT_PURPLE:
                return R.style.ThemeOverlay_MargyTube_Purple;
            case ACCENT_PINK:
                return R.style.ThemeOverlay_MargyTube_Pink;
            case ACCENT_ORANGE:
                return R.style.ThemeOverlay_MargyTube_Orange;
            case ACCENT_RED:
                return R.style.ThemeOverlay_MargyTube_Red;
            case ACCENT_GREEN:
            case ACCENT_DYNAMIC:
            default:
                return R.style.ThemeOverlay_MargyTube_Green;
        }
    }
}

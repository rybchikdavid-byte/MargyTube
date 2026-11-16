package com.wexresprise.margytube;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

/**
 * Every preference lives here. Appearance setters only bump a version when the
 * value really changed, so a plugin that applies its own style cannot make the
 * screens recreate themselves in a loop.
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

    /**
     * Google's public OAuth client for YouTube on TV, the same one Android TV
     * builds use. It lets the device flow work with no setup from the person.
     */
    public static final String DEFAULT_OAUTH_CLIENT =
            "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com";

    private static final String KEY_ACCENT = "accent";
    private static final String KEY_THEME = "theme";
    private static final String KEY_CARD = "card_style";
    private static final String KEY_RADIUS = "radius";
    private static final String KEY_FONT = "font_scale";
    private static final String KEY_TOKEN = "oauth_token";
    private static final String KEY_REFRESH = "oauth_refresh";
    private static final String KEY_EXPIRY = "oauth_expiry";
    private static final String KEY_ACCOUNT = "account_name";
    private static final String KEY_CHANNEL = "account_channel";
    private static final String KEY_AVATAR = "account_avatar";
    private static final String KEY_CLIENT = "oauth_client";
    private static final String KEY_REGION = "region";
    private static final String KEY_STYLE_VERSION = "style_version";
    private static final String KEY_FEED_INDEX = "feed_index";

    private Prefs() {
    }

    public static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String accent(Context c) {
        return sp(c).getString(KEY_ACCENT, ACCENT_DYNAMIC);
    }

    public static void setAccent(Context c, String value) {
        if (value == null || value.equals(accent(c))) {
            return;
        }
        sp(c).edit().putString(KEY_ACCENT, value).apply();
        bumpStyle(c);
    }

    public static int themeMode(Context c) {
        return sp(c).getInt(KEY_THEME, THEME_SYSTEM);
    }

    public static void setThemeMode(Context c, int mode) {
        if (mode == themeMode(c)) {
            return;
        }
        sp(c).edit().putInt(KEY_THEME, mode).apply();
        applyThemeMode(c);
        bumpStyle(c);
    }

    public static int cardStyle(Context c) {
        return sp(c).getInt(KEY_CARD, CARD_FILLED);
    }

    public static void setCardStyle(Context c, int style) {
        if (style == cardStyle(c)) {
            return;
        }
        sp(c).edit().putInt(KEY_CARD, style).apply();
        bumpStyle(c);
    }

    public static int radius(Context c) {
        return sp(c).getInt(KEY_RADIUS, 16);
    }

    public static void setRadius(Context c, int dp) {
        int value = Math.max(0, Math.min(28, dp));
        if (value == radius(c)) {
            return;
        }
        sp(c).edit().putInt(KEY_RADIUS, value).apply();
        bumpStyle(c);
    }

    public static int fontScale(Context c) {
        return sp(c).getInt(KEY_FONT, 100);
    }

    public static void setFontScale(Context c, int percent) {
        int value = Math.max(85, Math.min(125, percent));
        if (value == fontScale(c)) {
            return;
        }
        sp(c).edit().putInt(KEY_FONT, value).apply();
        bumpStyle(c);
    }

    public static String token(Context c) {
        return sp(c).getString(KEY_TOKEN, "");
    }

    public static String refreshToken(Context c) {
        return sp(c).getString(KEY_REFRESH, "");
    }

    public static long tokenExpiry(Context c) {
        return sp(c).getLong(KEY_EXPIRY, 0);
    }

    public static boolean isSignedIn(Context c) {
        return !token(c).isEmpty() || !refreshToken(c).isEmpty();
    }

    public static void setSession(Context c, String access, String refresh, long expiresInSeconds) {
        SharedPreferences.Editor editor = sp(c).edit()
                .putString(KEY_TOKEN, access == null ? "" : access)
                .putLong(KEY_EXPIRY, System.currentTimeMillis() + Math.max(60, expiresInSeconds) * 1000L);
        if (refresh != null && !refresh.isEmpty()) {
            editor.putString(KEY_REFRESH, refresh);
        }
        editor.apply();
    }

    public static void signOut(Context c) {
        sp(c).edit()
                .remove(KEY_TOKEN)
                .remove(KEY_REFRESH)
                .remove(KEY_EXPIRY)
                .remove(KEY_ACCOUNT)
                .remove(KEY_CHANNEL)
                .remove(KEY_AVATAR)
                .apply();
    }

    public static String accountName(Context c) {
        return sp(c).getString(KEY_ACCOUNT, "");
    }

    public static String accountAvatar(Context c) {
        return sp(c).getString(KEY_AVATAR, "");
    }

    public static String accountChannel(Context c) {
        return sp(c).getString(KEY_CHANNEL, "");
    }

    public static void setAccount(Context c, String name, String channelId, String avatar) {
        sp(c).edit()
                .putString(KEY_ACCOUNT, name == null ? "" : name)
                .putString(KEY_CHANNEL, channelId == null ? "" : channelId)
                .putString(KEY_AVATAR, avatar == null ? "" : avatar)
                .apply();
    }

    public static String oauthClient(Context c) {
        String stored = sp(c).getString(KEY_CLIENT, "");
        return stored == null || stored.trim().isEmpty() ? DEFAULT_OAUTH_CLIENT : stored.trim();
    }

    public static void setOauthClient(Context c, String clientId) {
        sp(c).edit().putString(KEY_CLIENT, clientId == null ? "" : clientId.trim()).apply();
    }

    public static String region(Context c) {
        return sp(c).getString(KEY_REGION, "RU");
    }

    public static void setRegion(Context c, String code) {
        sp(c).edit().putString(KEY_REGION, code).apply();
    }

    /** Rotation for the signed-out home feed, so it is not always the same topic. */
    public static int nextFeedIndex(Context c, int size) {
        if (size <= 0) {
            return 0;
        }
        int index = Math.floorMod(sp(c).getInt(KEY_FEED_INDEX, 0), size);
        sp(c).edit().putInt(KEY_FEED_INDEX, (index + 1) % size).apply();
        return index;
    }

    public static int styleVersion(Context c) {
        return sp(c).getInt(KEY_STYLE_VERSION, 0);
    }

    public static void bumpStyle(Context c) {
        int v = styleVersion(c) + 1;
        sp(c).edit().putInt(KEY_STYLE_VERSION, v).apply();
    }

    /** Read-only view of the settings for plugins (margytube.get). */
    public static String value(Context c, String key) {
        if (key == null) {
            return "";
        }
        switch (key) {
            case "accent":
                return accent(c);
            case "theme":
                switch (themeMode(c)) {
                    case THEME_LIGHT:
                        return "light";
                    case THEME_DARK:
                        return "dark";
                    default:
                        return "system";
                }
            case "cards":
                switch (cardStyle(c)) {
                    case CARD_ELEVATED:
                        return "elevated";
                    case CARD_OUTLINED:
                        return "outlined";
                    default:
                        return "filled";
                }
            case "radius":
                return String.valueOf(radius(c));
            case "font":
                return String.valueOf(fontScale(c));
            case "region":
                return region(c);
            case "account":
                return accountName(c);
            case "signed_in":
                return isSignedIn(c) ? "yes" : "no";
            default:
                return "";
        }
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

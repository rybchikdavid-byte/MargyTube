package com.wexresprise.margytube;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Shared shell: font scaling, real Material You colours, edge-to-edge insets,
 * and a single recreate when the appearance changed somewhere else.
 */
public abstract class BaseActivity extends AppCompatActivity {
    private int appliedStyleVersion;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Prefs.wrap(base));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Prefs.applyAccent(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        appliedStyleVersion = Prefs.styleVersion(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSystemBars();
        if (Prefs.styleVersion(this) != appliedStyleVersion) {
            appliedStyleVersion = Prefs.styleVersion(this);
            recreate();
        }
    }

    protected boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    protected void updateSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        boolean light = !isNight();
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
    }

    /**
     * Pads the top bar by the status bar and the bottom bar by the navigation bar.
     * The base padding is captured once, so repeated inset dispatches (the keyboard
     * sends a lot of them) never stack up.
     */
    protected void applyInsets(View root, @Nullable View top, @Nullable View bottom) {
        applyInsets(root, top, bottom, false);
    }

    protected void applyInsets(View root, @Nullable View top, @Nullable View bottom, boolean ime) {
        final int topBase = top == null ? 0 : top.getPaddingTop();
        final int bottomBase = bottom == null ? 0 : bottom.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (top != null) {
                top.setPadding(top.getPaddingLeft(), topBase + bars.top,
                        top.getPaddingRight(), top.getPaddingBottom());
            }
            if (bottom != null) {
                int extra = bars.bottom;
                if (ime) {
                    extra = Math.max(extra,
                            windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
                }
                bottom.setPadding(bottom.getPaddingLeft(), bottom.getPaddingTop(),
                        bottom.getPaddingRight(), bottomBase + extra);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    protected void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) {
            return;
        }
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
        focus.clearFocus();
    }
}

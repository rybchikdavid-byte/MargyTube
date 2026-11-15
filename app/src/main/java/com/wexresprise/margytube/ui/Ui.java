package com.wexresprise.margytube.ui;

import android.content.Context;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

public final class Ui {
    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static int attrColor(Context context, int attr) {
        TypedValue typed = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, typed, true)) {
            if (typed.resourceId != 0) {
                return ContextCompat.getColor(context, typed.resourceId);
            }
            return typed.data;
        }
        return 0;
    }
}

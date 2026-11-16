package com.wexresprise.margytube;

import android.app.Application;
import android.util.Log;

import com.wexresprise.margytube.plugin.PluginManager;
import com.wexresprise.margytube.plugin.ScriptRuntime;

public class App extends Application {
    private static final String TAG = "MargyTube";

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.applyThemeMode(this);
        ScriptRuntime.install();
        try {
            PluginManager.installBundled(this);
            PluginManager.applyStyles(this);
        } catch (Throwable t) {
            Log.w(TAG, "plugin startup failed", t);
        }
    }
}

package com.wexresprise.margytube;

import android.app.Application;

import com.wexresprise.margytube.plugin.ScriptRuntime;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.applyThemeMode(this);
        ScriptRuntime.install();
    }
}

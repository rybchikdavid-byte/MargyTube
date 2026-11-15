package com.wexresprise.margytube.plugin;

import android.content.Context;

import java.io.File;

import dalvik.system.DexClassLoader;

/** Native plugin: classes.dex inside the archive, entry class in manifest.json. */
public final class DexPlugin {
    private final MargyTubePlugin impl;

    public DexPlugin(Context context, PluginInfo info) throws Exception {
        File dex = new File(info.dirPath, "classes.dex");
        if (!dex.isFile()) {
            throw new Exception("classes.dex не найден");
        }
        DexClassLoader loader = new DexClassLoader(dex.getAbsolutePath(),
                context.getCodeCacheDir().getAbsolutePath(), null,
                DexPlugin.class.getClassLoader());
        String entry = info.entry == null || info.entry.isEmpty()
                ? "MargyTubePlugin" : info.entry;
        Class<?> cls = Class.forName(entry, true, loader);
        Object instance = cls.getDeclaredConstructor().newInstance();
        if (!(instance instanceof MargyTubePlugin)) {
            throw new Exception("entry не реализует MargyTubePlugin");
        }
        impl = (MargyTubePlugin) instance;
    }

    public String onFeed(String json) {
        return impl.onFeed(json);
    }

    public String onVideo(String videoId) {
        return impl.onVideo(videoId);
    }
}

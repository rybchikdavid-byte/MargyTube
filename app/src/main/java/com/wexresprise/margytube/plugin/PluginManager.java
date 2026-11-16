package com.wexresprise.margytube.plugin;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.wexresprise.margytube.Prefs;
import com.wexresprise.margytube.Video;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MargyT-style plugins without runtime method hooking: manifests plus small JS
 * hooks, or an optional DEX class implementing MargyTubePlugin. Everything is
 * stored inside the app, validated, and run with a deadline.
 */
public final class PluginManager {
    private static final String TAG = "MargyTubePlugin";
    private static final String PREFS = "plugins";
    private static final String FOLDER = "plugins";
    private static final long MAX_ENTRY = 12 * 1024 * 1024L;
    private static final long MAX_TOTAL = 24 * 1024 * 1024L;
    private static final long MAX_MANIFEST = 64 * 1024L;
    private static final long MAX_SCRIPT = 1024 * 1024L;
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
    private static final String BUNDLED_ID = "com.margytube.custom";
    private static final String BUNDLED_ASSET = "custom.mgtp";
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
            "manifest.json", "plugin.js", "classes.dex", "icon.png"));

    private static final Object CACHE_LOCK = new Object();
    private static final Map<String, ScriptPlugin> SCRIPTS = new HashMap<>();
    private static final Map<String, DexPlugin> DEXES = new HashMap<>();
    private static final ExecutorService FEED_EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mgtp-feed");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface FeedCallback {
        void done(List<Video> videos);
    }

    public interface NotesCallback {
        void done(List<String> notes);
    }

    private PluginManager() {
    }

    public static File root(Context c) {
        File root = new File(c.getApplicationContext().getFilesDir(), FOLDER);
        if (!root.isDirectory() && !root.mkdirs()) {
            Log.w(TAG, "cannot create plugin folder");
        }
        return root;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context c, String id) {
        return prefs(c).getBoolean("on_" + id, true);
    }

    public static void setEnabled(Context c, String id, boolean enabled) {
        prefs(c).edit().putBoolean("on_" + id, enabled).apply();
    }

    public static List<PluginInfo> list(Context c) {
        List<PluginInfo> out = new ArrayList<>();
        File root = root(c);
        File[] dirs = root.listFiles();
        if (dirs == null) {
            return out;
        }
        Arrays.sort(dirs, Comparator.comparing(File::getName));
        for (File dir : dirs) {
            if (!dir.isDirectory()) {
                continue;
            }
            try {
                PluginInfo info = readInfo(c, dir);
                if (info != null) {
                    out.add(info);
                }
            } catch (Exception e) {
                Log.w(TAG, "bad plugin folder " + dir.getName(), e);
            }
        }
        return out;
    }

    public static boolean hasEnabled(Context c) {
        for (PluginInfo info : list(c)) {
            if (info.enabled && (info.dex || new File(info.dirPath, "plugin.js").isFile())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads a .mgtp (ZIP) from SAF. Only the four known files are taken, names are
     * sanitised, and the archive may not escape the plugin folder.
     */
    public static PluginInfo install(Context c, InputStream input) throws Exception {
        Map<String, byte[]> entries = readZip(input);

        String manifestKey = null;
        if (entries.containsKey("manifest.json")) {
            manifestKey = "manifest.json";
        } else {
            for (String name : entries.keySet()) {
                if (name.endsWith("/manifest.json")) {
                    manifestKey = name;
                    break;
                }
            }
        }
        if (manifestKey == null) {
            throw new IOException("В архиве нет manifest.json");
        }
        String prefix = manifestKey.substring(0, manifestKey.length() - "manifest.json".length());

        Map<String, byte[]> files = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            String relative = entry.getKey().substring(prefix.length());
            if (ALLOWED.contains(relative) && !files.containsKey(relative)) {
                files.put(relative, entry.getValue());
            }
        }

        JSONObject manifest = new JSONObject(new String(files.get("manifest.json"),
                StandardCharsets.UTF_8));
        String id = manifest.optString("id", "").trim();
        if (!ID.matcher(id).matches()) {
            throw new IOException("manifest.json: id может содержать только буквы, цифры, . _ -");
        }
        if (!files.containsKey("plugin.js") && !files.containsKey("classes.dex")) {
            throw new IOException("Нужен plugin.js или classes.dex");
        }

        File root = root(c);
        File dir = new File(root, id);
        String rootPath = root.getCanonicalPath();
        if (!rootPath.equals(dir.getParentFile().getCanonicalPath())) {
            throw new IOException("Недопустимый путь плагина");
        }
        deleteRecursively(dir);
        if (!dir.mkdirs()) {
            throw new IOException("Не удалось создать папку плагина");
        }
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            write(new File(dir, entry.getKey()), entry.getValue());
        }

        closeCached(id);
        prefs(c).edit().putBoolean("on_" + id, true)
                .remove("err_" + id).apply();
        return readInfo(c, dir);
    }

    public static void delete(Context c, String id) {
        if (!ID.matcher(id).matches()) {
            return;
        }
        File root = root(c);
        try {
            File dir = new File(root, id);
            if (root.getCanonicalPath().equals(dir.getParentFile().getCanonicalPath())) {
                deleteRecursively(dir);
            }
        } catch (IOException ignored) {
            // Nothing else to do; the list refresh will show the result.
        }
        closeCached(id);
        prefs(c).edit().remove("on_" + id).remove("err_" + id).apply();
    }

    /** Writes an importable example into the plugin folder and leaves it enabled. */
    public static String createExample(Context c) throws Exception {
        String id = "com.margytube.example";
        File dir = new File(root(c), id);
        deleteRecursively(dir);
        if (!dir.mkdirs()) {
            throw new IOException("Не удалось создать папку плагина");
        }
        String manifest = "{\n"
                + "  \"id\": \"com.margytube.example\",\n"
                + "  \"name\": \"Пример плагина\",\n"
                + "  \"version\": \"1.0\",\n"
                + "  \"author\": \"MargyTube\",\n"
                + "  \"description\": \"Добавляет звезду к заголовкам в ленте и заметку на экране видео.\",\n"
                + "  \"hooks\": [\"onFeed\", \"onVideo\"]\n"
                + "}\n";
        String script = "// MargyTube plugin example.\n"
                + "// onFeed receives a JSON array of videos and returns JSON.\n"
                + "function onFeed(json) {\n"
                + "  var list = JSON.parse(json);\n"
                + "  for (var i = 0; i < list.length; i++) {\n"
                + "    if (list[i].title && list[i].title.indexOf('\\u2605 ') !== 0) {\n"
                + "      list[i].title = '\\u2605 ' + list[i].title;\n"
                + "    }\n"
                + "  }\n"
                + "  return JSON.stringify(list);\n"
                + "}\n\n"
                + "// onVideo receives the video id and returns a short note or null.\n"
                + "function onVideo(id) {\n"
                + "  margytube.log('opening ' + id);\n"
                + "  return 'Плагин-пример открыл видео ' + id;\n"
                + "}\n";
        write(new File(dir, "manifest.json"), manifest.getBytes(StandardCharsets.UTF_8));
        write(new File(dir, "plugin.js"), script.getBytes(StandardCharsets.UTF_8));
        closeCached(id);
        prefs(c).edit().putBoolean("on_" + id, true).remove("err_" + id).apply();
        return id;
    }

    /**
     * Installs the customization plugin that ships inside the APK, once. It is a
     * normal .mgtp plugin: the person can switch it off, delete it, or import a
     * newer build over it.
     */
    public static void installBundled(Context c) {
        File dir = new File(root(c), BUNDLED_ID);
        if (new File(dir, "manifest.json").isFile()) {
            return;
        }
        try (InputStream in = c.getAssets().open(BUNDLED_ASSET)) {
            install(c, in);
            Log.i(TAG, "bundled customization plugin installed");
        } catch (Exception e) {
            Log.w(TAG, "cannot install bundled plugin", e);
        }
    }

    /** Runs the onApp hook of every enabled script plugin, which may set the style. */
    public static void applyStyles(Context c) {
        for (PluginInfo info : list(c)) {
            if (!info.enabled || info.dex) {
                continue;
            }
            if (!new File(info.dirPath, "plugin.js").isFile()) {
                continue;
            }
            if (!info.hooks.isEmpty() && !info.hooks.contains("onApp")) {
                continue;
            }
            try {
                scriptFor(c, info).call("onApp", "");
                clearError(c, info.id);
            } catch (Throwable t) {
                Log.w(TAG, "onApp failed in " + info.id, t);
                setError(c, info.id, describe(t));
            }
        }
    }

    /**
     * Applies a style object from a plugin:
     * {"accent":"blue","theme":"dark","cards":"outlined","radius":20,"font":100}
     */
    public static boolean applyStyle(Context c, String json) throws Exception {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        final Context app = c.getApplicationContext();
        JSONObject style = new JSONObject(json);
        boolean changed = false;

        String accent = style.optString("accent", "");
        if (!accent.isEmpty() && !accent.equals(Prefs.accent(app))) {
            Prefs.setAccent(app, accent);
            changed = true;
        }

        String theme = style.optString("theme", "");
        if (!theme.isEmpty()) {
            int mode = -1;
            if ("system".equals(theme)) {
                mode = Prefs.THEME_SYSTEM;
            } else if ("light".equals(theme)) {
                mode = Prefs.THEME_LIGHT;
            } else if ("dark".equals(theme)) {
                mode = Prefs.THEME_DARK;
            }
            if (mode >= 0 && mode != Prefs.themeMode(app)) {
                final int wanted = mode;
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    Prefs.setThemeMode(app, wanted);
                } else {
                    MAIN.post(() -> Prefs.setThemeMode(app, wanted));
                }
                changed = true;
            }
        }

        String cards = style.optString("cards", "");
        if (!cards.isEmpty()) {
            int styleValue = -1;
            if ("elevated".equals(cards)) {
                styleValue = Prefs.CARD_ELEVATED;
            } else if ("filled".equals(cards)) {
                styleValue = Prefs.CARD_FILLED;
            } else if ("outlined".equals(cards)) {
                styleValue = Prefs.CARD_OUTLINED;
            }
            if (styleValue >= 0 && styleValue != Prefs.cardStyle(app)) {
                Prefs.setCardStyle(app, styleValue);
                changed = true;
            }
        }

        int radius = style.optInt("radius", -1);
        if (radius >= 0 && radius != Prefs.radius(app)) {
            Prefs.setRadius(app, radius);
            changed = true;
        }

        int font = style.optInt("font", -1);
        if (font >= 0 && font != Prefs.fontScale(app)) {
            Prefs.setFontScale(app, font);
            changed = true;
        }

        String region = style.optString("region", "");
        if (region.length() == 2 && !region.equals(Prefs.region(app))) {
            Prefs.setRegion(app, region.toUpperCase());
            changed = true;
        }
        return changed;
    }

    public static void applyFeedAsync(Context c, List<Video> input, FeedCallback callback) {
        final List<Video> source = input == null ? new ArrayList<>() : input;
        if (!hasEnabled(c)) {
            callback.done(source);
            return;
        }
        final Context app = c.getApplicationContext();
        FEED_EXEC.execute(() -> {
            List<Video> result = transformFeed(app, source);
            MAIN.post(() -> callback.done(result));
        });
    }

    public static void notesAsync(Context c, String videoId, NotesCallback callback) {
        if (!hasEnabled(c)) {
            callback.done(Collections.emptyList());
            return;
        }
        final Context app = c.getApplicationContext();
        FEED_EXEC.execute(() -> {
            List<String> notes = notes(app, videoId);
            MAIN.post(() -> callback.done(notes));
        });
    }

    public static List<Video> transformFeed(Context c, List<Video> input) {
        if (input == null) {
            return new ArrayList<>();
        }
        Map<String, Boolean> demoFlags = new HashMap<>();
        String json;
        try {
            JSONArray array = new JSONArray();
            for (Video video : input) {
                demoFlags.put(video.id, video.demo);
                array.put(video.toJson());
            }
            json = array.toString();
        } catch (JSONException e) {
            return input;
        }

        for (PluginInfo info : list(c)) {
            if (!info.enabled) {
                continue;
            }
            try {
                String output;
                if (info.dex) {
                    output = dexFor(c, info).onFeed(json);
                } else if (new File(info.dirPath, "plugin.js").isFile()) {
                    output = scriptFor(c, info).call("onFeed", json);
                } else {
                    continue;
                }
                if (output != null && !output.trim().isEmpty()) {
                    JSONArray parsed = new JSONArray(output);
                    json = parsed.toString();
                }
                clearError(c, info.id);
            } catch (Throwable t) {
                Log.w(TAG, "onFeed failed in " + info.id, t);
                setError(c, info.id, describe(t));
            }
        }

        try {
            JSONArray array = new JSONArray(json);
            List<Video> out = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                Video video = Video.fromJson(object);
                if (video.id == null || video.id.isEmpty()) {
                    continue;
                }
                video.demo = demoFlags.containsKey(video.id) && demoFlags.get(video.id);
                out.add(video);
            }
            return out;
        } catch (JSONException e) {
            return input;
        }
    }

    public static List<String> notes(Context c, String videoId) {
        List<String> notes = new ArrayList<>();
        for (PluginInfo info : list(c)) {
            if (!info.enabled) {
                continue;
            }
            try {
                String output;
                if (info.dex) {
                    output = dexFor(c, info).onVideo(videoId);
                } else if (new File(info.dirPath, "plugin.js").isFile()) {
                    output = scriptFor(c, info).call("onVideo", videoId);
                } else {
                    continue;
                }
                if (output != null && !output.trim().isEmpty()) {
                    notes.add(info.name + ": " + output.trim());
                }
                clearError(c, info.id);
            } catch (Throwable t) {
                Log.w(TAG, "onVideo failed in " + info.id, t);
                setError(c, info.id, describe(t));
            }
        }
        return notes;
    }

    private static PluginInfo readInfo(Context c, File dir) throws Exception {
        File manifestFile = new File(dir, "manifest.json");
        if (!manifestFile.isFile()) {
            return null;
        }
        String text = readText(manifestFile, MAX_MANIFEST);
        JSONObject manifest = new JSONObject(text);

        PluginInfo info = new PluginInfo();
        info.id = dir.getName();
        if (!ID.matcher(info.id).matches()) {
            return null;
        }
        info.name = manifest.optString("name", info.id);
        info.version = manifest.optString("version", "");
        info.author = manifest.optString("author", "");
        info.description = manifest.optString("description", "");
        info.entry = manifest.optString("entry", "");
        info.dirPath = dir.getAbsolutePath();
        info.dex = new File(dir, "classes.dex").isFile();
        info.enabled = isEnabled(c, info.id);
        info.error = prefs(c).getString("err_" + info.id, null);
        JSONArray hooks = manifest.optJSONArray("hooks");
        if (hooks != null) {
            for (int i = 0; i < hooks.length(); i++) {
                String hook = hooks.optString(i, "");
                if (!hook.isEmpty()) {
                    info.hooks.add(hook);
                }
            }
        }
        return info;
    }

    private static Map<String, byte[]> readZip(InputStream input) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        byte[] buffer = new byte[8192];
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.length() > 200) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                long size = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    size += read;
                    total += read;
                    if (size > MAX_ENTRY || total > MAX_TOTAL) {
                        throw new IOException("Архив плагина слишком большой");
                    }
                    out.write(buffer, 0, read);
                }
                entries.put(name, out.toByteArray());
            }
        }
        if (entries.isEmpty()) {
            throw new IOException("Пустой архив плагина");
        }
        return entries;
    }

    private static String readText(File file, long max) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(file),
                StandardCharsets.UTF_8)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
                if (builder.length() > max) {
                    throw new IOException("Файл плагина слишком большой");
                }
            }
            return builder.toString();
        }
    }

    private static void write(File file, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static ScriptPlugin scriptFor(Context c, PluginInfo info) {
        synchronized (CACHE_LOCK) {
            ScriptPlugin plugin = SCRIPTS.get(info.id);
            if (plugin == null) {
                plugin = new ScriptPlugin(info.id, new File(info.dirPath, "plugin.js"),
                        c.getApplicationContext());
                SCRIPTS.put(info.id, plugin);
            }
            return plugin;
        }
    }

    private static DexPlugin dexFor(Context c, PluginInfo info) throws Exception {
        synchronized (CACHE_LOCK) {
            DexPlugin plugin = DEXES.get(info.id);
            if (plugin == null) {
                plugin = new DexPlugin(c, info);
                DEXES.put(info.id, plugin);
            }
            return plugin;
        }
    }

    private static void closeCached(String id) {
        synchronized (CACHE_LOCK) {
            ScriptPlugin script = SCRIPTS.remove(id);
            if (script != null) {
                script.close();
            }
            DEXES.remove(id);
        }
    }

    private static void setError(Context c, String id, String message) {
        prefs(c).edit().putString("err_" + id, message).apply();
    }

    private static void clearError(Context c, String id) {
        if (prefs(c).contains("err_" + id)) {
            prefs(c).edit().remove("err_" + id).apply();
        }
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = t.getClass().getSimpleName();
        }
        message = message.trim();
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}

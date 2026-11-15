package com.wexresprise.margytube;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Talks only to the official YouTube Data API v3. No scraping, no stream
 * extraction: playback stays in YouTube's own IFrame player.
 */
public final class YouTubeApi {
    private static final String BASE = "https://www.googleapis.com/youtube/v3/";
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Cb<T> {
        void ok(T data);

        void error(String message);
    }

    private interface Call<T> {
        T run() throws Exception;
    }

    public static final class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }
    }

    private YouTubeApi() {
    }

    public static boolean hasKey(Context c) {
        return !Prefs.apiKey(c).trim().isEmpty();
    }

    public static boolean hasToken(Context c) {
        return !Prefs.token(c).trim().isEmpty();
    }

    public static void trending(Context c, String region, Cb<List<Video>> cb) {
        final String key = Prefs.apiKey(c).trim();
        if (key.isEmpty()) {
            cb.error(c.getString(R.string.err_no_key));
            return;
        }
        final String code = region == null || region.isEmpty() ? "RU" : region;
        async(c, cb, () -> {
            String url = BASE + "videos?part=snippet,contentDetails,statistics"
                    + "&chart=mostPopular&maxResults=25"
                    + "&regionCode=" + enc(code) + "&key=" + enc(key);
            return parseVideos(c, get(url, null));
        });
    }

    public static void search(Context c, String query, String region, Cb<List<Video>> cb) {
        final String key = Prefs.apiKey(c).trim();
        if (key.isEmpty()) {
            cb.error(c.getString(R.string.err_no_key));
            return;
        }
        final String code = region == null || region.isEmpty() ? "RU" : region;
        async(c, cb, () -> {
            String url = BASE + "search?part=snippet&type=video&maxResults=25"
                    + "&q=" + enc(query) + "&regionCode=" + enc(code)
                    + "&key=" + enc(key);
            JSONObject response = get(url, null);
            List<Video> found = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            JSONArray items = response.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    JSONObject idObject = item.optJSONObject("id");
                    String videoId = idObject == null ? "" : idObject.optString("videoId", "");
                    JSONObject snippet = item.optJSONObject("snippet");
                    if (videoId.isEmpty() || snippet == null) {
                        continue;
                    }
                    Video v = new Video();
                    v.id = videoId;
                    v.title = snippet.optString("title", "");
                    v.channel = snippet.optString("channelTitle", "");
                    v.channelId = snippet.optString("channelId", "");
                    v.description = snippet.optString("description", "");
                    v.publishedAt = snippet.optString("publishedAt", "");
                    v.thumbnail = thumbnail(snippet);
                    found.add(v);
                    ids.add(videoId);
                }
            }
            if (!ids.isEmpty()) {
                try {
                    String statsUrl = BASE + "videos?part=contentDetails,statistics&id="
                            + enc(TextUtils.join(",", ids)) + "&key=" + enc(key);
                    JSONObject stats = get(statsUrl, null);
                    Map<String, JSONObject> byId = new HashMap<>();
                    JSONArray statItems = stats.optJSONArray("items");
                    if (statItems != null) {
                        for (int i = 0; i < statItems.length(); i++) {
                            JSONObject item = statItems.optJSONObject(i);
                            if (item != null) {
                                byId.put(item.optString("id", ""), item);
                            }
                        }
                    }
                    for (Video v : found) {
                        JSONObject item = byId.get(v.id);
                        if (item != null) {
                            applyStats(v, item);
                        }
                    }
                } catch (Exception ignored) {
                    // Search results are still usable without per-video statistics.
                }
            }
            return found;
        });
    }

    public static void video(Context c, String videoId, Cb<Video> cb) {
        final String key = Prefs.apiKey(c).trim();
        if (key.isEmpty()) {
            cb.error(c.getString(R.string.err_no_key));
            return;
        }
        async(c, cb, () -> {
            String url = BASE + "videos?part=snippet,contentDetails,statistics&id="
                    + enc(videoId) + "&key=" + enc(key);
            List<Video> list = parseVideos(c, get(url, null));
            if (list.isEmpty()) {
                throw new ApiException("Видео не найдено");
            }
            return list.get(0);
        });
    }

    public static void comments(Context c, String videoId, Cb<List<Comment>> cb) {
        final String key = Prefs.apiKey(c).trim();
        if (key.isEmpty()) {
            cb.error(c.getString(R.string.err_no_key));
            return;
        }
        async(c, cb, () -> {
            String url = BASE + "commentThreads?part=snippet&videoId=" + enc(videoId)
                    + "&maxResults=50&order=relevance&textFormat=plainText&key=" + enc(key);
            JSONObject response = get(url, null);
            List<Comment> out = new ArrayList<>();
            JSONArray items = response.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    Comment parsed = parseThread(items.optJSONObject(i));
                    if (parsed != null) {
                        out.add(parsed);
                    }
                }
            }
            return out;
        });
    }

    public static void postComment(Context c, String videoId, String text, Cb<Comment> cb) {
        final String token = Prefs.token(c).trim();
        if (token.isEmpty()) {
            cb.error(c.getString(R.string.err_token));
            return;
        }
        async(c, cb, () -> {
            JSONObject inner = new JSONObject();
            inner.put("textOriginal", text);
            JSONObject top = new JSONObject();
            top.put("snippet", inner);
            JSONObject snippet = new JSONObject();
            snippet.put("videoId", videoId);
            snippet.put("topLevelComment", top);
            JSONObject body = new JSONObject();
            body.put("snippet", snippet);
            String url = BASE + "commentThreads?part=snippet" + keySuffix(c);
            JSONObject response = post(url, token, body);
            Comment comment = parseThread(response);
            if (comment == null) {
                throw new ApiException(c.getString(R.string.err_parse));
            }
            return comment;
        });
    }

    public static void rateLike(Context c, String videoId, Cb<Void> cb) {
        final String token = Prefs.token(c).trim();
        if (token.isEmpty()) {
            cb.error(c.getString(R.string.err_token));
            return;
        }
        async(c, cb, () -> {
            String url = BASE + "videos/rate?id=" + enc(videoId) + "&rating=like"
                    + keySuffix(c);
            post(url, token, null);
            return null;
        });
    }

    public static void testKey(Context c, String key, Cb<Integer> cb) {
        final String trimmed = key == null ? "" : key.trim();
        if (trimmed.isEmpty()) {
            cb.error(c.getString(R.string.err_key_invalid));
            return;
        }
        async(c, cb, () -> {
            String url = BASE + "videos?part=snippet&chart=mostPopular&maxResults=1"
                    + "&regionCode=US&key=" + enc(trimmed);
            JSONObject response = get(url, null);
            JSONArray items = response.optJSONArray("items");
            return items == null ? 0 : items.length();
        });
    }

    public static void testToken(Context c, String token, Cb<String> cb) {
        final String trimmed = token == null ? "" : token.trim();
        if (trimmed.isEmpty()) {
            cb.error(c.getString(R.string.err_token));
            return;
        }
        async(c, cb, () -> {
            String url = BASE + "channels?part=snippet&mine=true";
            JSONObject response = get(url, trimmed);
            JSONArray items = response.optJSONArray("items");
            if (items == null || items.length() == 0) {
                return "";
            }
            JSONObject snippet = items.optJSONObject(0) == null ? null
                    : items.optJSONObject(0).optJSONObject("snippet");
            return snippet == null ? "" : snippet.optString("title", "");
        });
    }

    private static <T> void async(Context c, Cb<T> cb, Call<T> work) {
        EXEC.execute(() -> {
            try {
                T result = work.run();
                MAIN.post(() -> cb.ok(result));
            } catch (Exception e) {
                String message;
                if (e instanceof ApiException) {
                    message = e.getMessage();
                } else if (e instanceof IOException) {
                    message = c.getString(R.string.err_network);
                } else {
                    message = c.getString(R.string.err_parse);
                }
                MAIN.post(() -> cb.error(message));
            }
        });
    }

    private static JSONObject get(String url, String token) throws IOException, ApiException {
        return request(url, token, "GET", null);
    }

    private static JSONObject post(String url, String token, JSONObject body)
            throws IOException, ApiException {
        return request(url, token, "POST", body);
    }

    private static JSONObject request(String url, String token, String method, JSONObject body)
            throws IOException, ApiException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type",
                        "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            } else if ("POST".equals(method)) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(0);
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String text = read(stream);
            if (code < 200 || code >= 300) {
                throw errorFor(code, text);
            }
            if (text.trim().isEmpty()) {
                return new JSONObject();
            }
            try {
                return new JSONObject(text);
            } catch (JSONException e) {
                throw new ApiException("Не удалось разобрать ответ YouTube");
            }        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
        }
        return builder.toString();
    }

    private static ApiException errorFor(int code, String body) {
        String message = "";
        String reason = "";
        try {
            JSONObject json = new JSONObject(body);
            JSONObject error = json.optJSONObject("error");
            if (error != null) {
                message = error.optString("message", "");
                JSONArray errors = error.optJSONArray("errors");
                if (errors != null && errors.length() > 0 && errors.optJSONObject(0) != null) {
                    reason = errors.optJSONObject(0).optString("reason", "");
                }
            }
        } catch (JSONException ignored) {
            // HTML error page, fall through.
        }

        if ("quotaExceeded".equals(reason)) {
            return new ApiException("Квота YouTube API на сегодня исчерпана");
        }
        if ("keyInvalid".equals(reason) || "API_KEY_INVALID".equals(reason)
                || "badRequest".equals(reason) && message.contains("API key")) {
            return new ApiException("Ключ API недействителен");
        }
        if ("commentsDisabled".equals(reason)) {
            return new ApiException("Комментарии отключены автором");
        }
        if ("insufficientPermissions".equals(reason)
                || "youtubeSignupRequired".equals(reason)
                || "forbidden".equals(reason)) {
            return new ApiException("YouTube запретил это действие для аккаунта");
        }
        if (code == 401) {
            return new ApiException("Токен недействителен или истёк");
        }
        if (!message.isEmpty()) {
            return new ApiException(message);
        }
        return new ApiException("HTTP " + code);
    }

    private static List<Video> parseVideos(Context c, JSONObject response)
            throws JSONException {
        List<Video> out = new ArrayList<>();
        JSONArray items = response.optJSONArray("items");
        if (items == null) {
            return out;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String id = item.optString("id", "");
            if (id.isEmpty()) {
                continue;
            }
            JSONObject snippet = item.optJSONObject("snippet");
            Video v = new Video();
            v.id = id;
            if (snippet != null) {
                v.title = snippet.optString("title", "");
                v.channel = snippet.optString("channelTitle", "");
                v.channelId = snippet.optString("channelId", "");
                v.description = snippet.optString("description", "");
                v.publishedAt = snippet.optString("publishedAt", "");
                v.thumbnail = thumbnail(snippet);
            }
            applyStats(v, item);
            out.add(v);
        }
        return out;
    }

    private static void applyStats(Video v, JSONObject item) {
        JSONObject details = item.optJSONObject("contentDetails");
        if (details != null) {
            v.duration = Formats.duration(details.optString("duration", ""));
        }
        JSONObject statistics = item.optJSONObject("statistics");
        if (statistics != null) {
            v.views = optLong(statistics, "viewCount");
            v.likes = optLong(statistics, "likeCount");
            v.comments = optLong(statistics, "commentCount");
        }
    }

    private static long optLong(JSONObject object, String name) {
        try {
            String value = object.optString(name, "");
            return value.isEmpty() ? -1 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String thumbnail(JSONObject snippet) {
        JSONObject thumbs = snippet.optJSONObject("thumbnails");
        if (thumbs == null) {
            return "";
        }
        for (String key : new String[]{"high", "medium", "standard", "default"}) {
            JSONObject thumb = thumbs.optJSONObject(key);
            if (thumb != null) {
                String url = thumb.optString("url", "");
                if (!url.isEmpty()) {
                    return url;
                }
            }
        }
        return "";
    }

    private static Comment parseThread(JSONObject item) {
        if (item == null) {
            return null;
        }
        JSONObject snippet = item.optJSONObject("snippet");
        if (snippet == null) {
            return null;
        }
        JSONObject top = snippet.optJSONObject("topLevelComment");
        if (top == null) {
            return null;
        }
        JSONObject inner = top.optJSONObject("snippet");
        if (inner == null) {
            return null;
        }
        Comment comment = new Comment();
        comment.author = inner.optString("authorDisplayName", "");
        comment.text = inner.optString("textDisplay", inner.optString("textOriginal", ""));
        comment.publishedAt = inner.optString("publishedAt", "");
        try {
            comment.likes = Long.parseLong(inner.optString("likeCount", "0"));
        } catch (NumberFormatException e) {
            comment.likes = 0;
        }
        return comment;
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    /** OAuth-only comment calls don't need (and must not send an empty) API key. */
    private static String keySuffix(Context c) {
        String key = Prefs.apiKey(c).trim();
        return key.isEmpty() ? "" : "&key=" + enc(key);
    }
}

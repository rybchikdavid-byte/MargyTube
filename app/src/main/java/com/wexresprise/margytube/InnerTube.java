package com.wexresprise.margytube;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to YouTube's own servers through their internal JSON endpoints (the ones
 * every browser and phone app already use). No API key of the user's own, no web
 * page scraping: search, the home feed, video details, comments, and the media
 * streams played by Google's Media3 player all come from youtube.com itself.
 *
 * Signing in uses Google's OAuth device flow. Commenting and liking then go
 * through the public YouTube Data API v3 with that OAuth token, which needs no
 * key either.
 *
 * This is an unofficial route: it can break whenever YouTube changes something.
 */
public final class InnerTube {
    private static final String TAG = "MargyTube";
    private static final String BASE = "https://www.youtube.com/youtubei/v1/";
    private static final String DATA_API = "https://www.googleapis.com/youtube/v3/";

    /** The web client key that youtube.com itself ships in every page. */
    private static final String WEB_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
    private static final String WEB_VERSION = "2.20261112.10.00";
    private static final String ANDROID_VERSION = "20.10.38";
    private static final String ANDROID_UA =
            "com.google.android.youtube/" + ANDROID_VERSION + " (Linux; U; Android 14) gzip";
    private static final String WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final String[] QUERIES_RU = {
            "музыка", "новинки музыки", "игры", "фильмы", "наука", "путешествия", "спорт"
    };
    private static final String[] QUERIES_EN = {
            "music", "gaming", "movies", "science", "travel", "sports", "technology"
    };

    public interface Cb<T> {
        void ok(T data);

        void error(String message);
    }

    private interface Call<T> {
        T run() throws Exception;
    }

    public static final class ApiException extends Exception {
        public final int code;

        public ApiException(String message) {
            this(message, 0);
        }

        public ApiException(String message, int code) {
            super(message);
            this.code = code;
        }
    }

    /** Everything the player needs: metadata plus the streams Google handed out. */
    public static final class Playback {
        public Video video = new Video();
        public String muxedUrl = "";
        public String muxedMime = "";
        public String videoUrl = "";
        public String videoMime = "";
        public String audioUrl = "";
        public String audioMime = "";

        public boolean canMerge() {
            return !videoUrl.isEmpty() && !audioUrl.isEmpty();
        }
    }

    private InnerTube() {
    }

    // ---------------------------------------------------------------- public API

    public static void search(Context c, String query, Cb<List<Video>> cb) {
        final String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            cb.ok(new ArrayList<>());
            return;
        }
        async(c, cb, () -> {
            JSONObject body = new JSONObject()
                    .put("context", webContext(c))
                    .put("query", q);
            JSONObject response = webPost(c, "search", body, tokenOrEmpty(c));
            return collectVideos(response);
        });
    }

    /**
     * Home: the signed-in recommendation feed when there is an account, otherwise
     * a real YouTube search for a rotating broad topic. Trending pages no longer
     * answer on the internal API, so this keeps the tab honest instead of fake.
     */
    public static void feed(Context c, Cb<List<Video>> cb) {
        if (!Prefs.isSignedIn(c)) {
            search(c, nextFeedQuery(c), cb);
            return;
        }
        GoogleAuth.token(c, new Cb<String>() {
            @Override
            public void ok(String token) {
                async(c, cb, () -> {
                    JSONObject body = new JSONObject()
                            .put("context", webContext(c))
                            .put("browseId", "FEwhat_to_watch");
                    try {
                        List<Video> list = collectVideos(webPost(c, "browse", body, token));
                        if (list.size() >= 4) {
                            return list;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "personal feed failed, using search", e);
                    }
                    return searchSync(c, nextFeedQuery(c), token);
                });
            }

            @Override
            public void error(String message) {
                search(c, nextFeedQuery(c), cb);
            }
        });
    }

    public static void playback(Context c, String videoId, Cb<Playback> cb) {
        async(c, cb, () -> {
            String token = tokenOrEmpty(c);
            JSONObject response;
            try {
                response = player(c, videoId, token);
            } catch (ApiException e) {
                if (e.code == 401 && !token.isEmpty()) {
                    response = player(c, videoId, "");
                } else {
                    throw e;
                }
            }
            Playback playback = new Playback();
            playback.video = videoFromPlayer(response, videoId);
            readStreams(response, playback);
            if (playback.muxedUrl.isEmpty() && !playback.canMerge()) {
                throw new ApiException(c.getString(R.string.player_no_stream));
            }
            return playback;
        });
    }

    public static void comments(Context c, String videoId, Cb<List<Comment>> cb) {
        async(c, cb, () -> {
            JSONObject body = new JSONObject()
                    .put("context", webContext(c))
                    .put("videoId", videoId);
            JSONObject page = webPost(c, "next", body, tokenOrEmpty(c));
            String token = commentToken(page);
            if (token == null || token.isEmpty()) {
                throw new ApiException(c.getString(R.string.comments_disabled));
            }
            JSONObject continuation = new JSONObject()
                    .put("context", webContext(c))
                    .put("continuation", token);
            JSONObject comments = webPost(c, "next", continuation, tokenOrEmpty(c));
            return commentsFrom(comments);
        });
    }

    public static void postComment(Context c, String videoId, String text, Cb<Comment> cb) {
        GoogleAuth.token(c, new Cb<String>() {
            @Override
            public void ok(String token) {
                async(c, cb, () -> {
                    JSONObject top = new JSONObject()
                            .put("snippet", new JSONObject().put("textOriginal", text));
                    JSONObject snippet = new JSONObject()
                            .put("videoId", videoId)
                            .put("topLevelComment", top);
                    JSONObject body = new JSONObject().put("snippet", snippet);
                    JSONObject response = dataPost("commentThreads?part=snippet", body, token);
                    Comment comment = parseThread(response);
                    if (comment == null) {
                        throw new ApiException(c.getString(R.string.err_parse));
                    }
                    return comment;
                });
            }

            @Override
            public void error(String message) {
                cb.error(message);
            }
        });
    }

    public static void rateLike(Context c, String videoId, Cb<Void> cb) {
        GoogleAuth.token(c, new Cb<String>() {
            @Override
            public void ok(String token) {
                async(c, cb, () -> {
                    dataPost("videos/rate?id=" + enc(videoId) + "&rating=like", null, token);
                    return null;
                });
            }

            @Override
            public void error(String message) {
                cb.error(message);
            }
        });
    }

    public static void fetchAccount(Context c, String token, Cb<String> cb) {
        async(c, cb, () -> {
            JSONObject response = dataGet("channels?part=snippet&mine=true", token);
            JSONArray items = response.optJSONArray("items");
            if (items == null || items.length() == 0) {
                throw new ApiException(c.getString(R.string.err_no_channel));
            }
            JSONObject item = items.optJSONObject(0);
            JSONObject snippet = item == null ? null : item.optJSONObject("snippet");
            if (snippet == null) {
                throw new ApiException(c.getString(R.string.err_no_channel));
            }
            String name = snippet.optString("title", "");
            String avatar = thumbnail(snippet);
            Prefs.setAccount(c, name, item.optString("id", ""), avatar);
            return name;
        });
    }

    // ------------------------------------------------------------------ requests

    private static JSONObject player(Context c, String videoId, String token)
            throws IOException, ApiException, JSONException {
        JSONObject client = new JSONObject()
                .put("clientName", "ANDROID")
                .put("clientVersion", ANDROID_VERSION)
                .put("hl", language(c))
                .put("gl", region(c))
                .put("androidSdkVersion", 34)
                .put("osName", "Android")
                .put("osVersion", "14");
        JSONObject body = new JSONObject()
                .put("context", new JSONObject().put("client", client))
                .put("videoId", videoId)
                .put("contentCheckOk", true)
                .put("racyCheckOk", true);
        JSONObject response = androidPost(c, "player", body, token);
        JSONObject status = response.optJSONObject("playabilityStatus");
        if (status != null) {
            String state = status.optString("status", "");
            if (!"OK".equals(state)) {
                String reason = status.optString("reason", "");
                throw new ApiException(playabilityMessage(state, reason));
            }
        }
        return response;
    }

    private static JSONObject webPost(Context c, String path, JSONObject body, String token)
            throws IOException, ApiException, JSONException {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", WEB_UA);
        headers.put("Origin", "https://www.youtube.com");
        headers.put("X-YouTube-Client-Name", "1");
        headers.put("X-YouTube-Client-Version", WEB_VERSION);
        return send(BASE + path + "?key=" + WEB_KEY + "&prettyPrint=false", body, headers, token);
    }

    private static JSONObject androidPost(Context c, String path, JSONObject body, String token)
            throws IOException, ApiException, JSONException {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", ANDROID_UA);
        headers.put("X-YouTube-Client-Name", "3");
        headers.put("X-YouTube-Client-Version", ANDROID_VERSION);
        return send(BASE + path + "?key=" + WEB_KEY + "&prettyPrint=false", body, headers, token);
    }

    private static JSONObject send(String url, JSONObject body, Map<String, String> headers,
                                   String token) throws IOException, ApiException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(25000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String text = read(stream);
            if (code < 200 || code >= 300) {
                throw errorFor(code, text);
            }
            try {
                return new JSONObject(text);
            } catch (JSONException e) {
                throw new ApiException("YouTube вернул неожиданный ответ", code);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static JSONObject dataGet(String path, String token)
            throws IOException, ApiException {
        return dataRequest(path, "GET", null, token);
    }

    private static JSONObject dataPost(String path, JSONObject body, String token)
            throws IOException, ApiException {
        return dataRequest(path, "POST", body, token);
    }

    private static JSONObject dataRequest(String path, String method, JSONObject body,
                                          String token) throws IOException, ApiException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(DATA_API + path).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);
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
            return new JSONObject(text);
        } catch (JSONException e) {
            throw new ApiException("Не удалось разобрать ответ YouTube");
        } finally {
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
            return new ApiException("Квота YouTube на сегодня исчерпана", code);
        }
        if ("commentsDisabled".equals(reason)) {
            return new ApiException("Комментарии отключены автором", code);
        }
        if ("insufficientPermissions".equals(reason) || "forbidden".equals(reason)
                || "youtubeSignupRequired".equals(reason)) {
            return new ApiException("YouTube запретил это действие для аккаунта", code);
        }
        if (code == 401) {
            return new ApiException("Нужно снова войти в Google", code);
        }
        if (code == 429) {
            return new ApiException("Слишком много запросов, попробуй позже", code);
        }
        if (!message.isEmpty()) {
            return new ApiException("YouTube: " + message, code);
        }
        return new ApiException("YouTube ответил HTTP " + code, code);
    }

    private static String playabilityMessage(String status, String reason) {
        String text = reason == null ? "" : reason;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("bot")) {
            return "YouTube просит подтвердить, что ты не бот. Войди в Google.";
        }
        if ("LOGIN_REQUIRED".equals(status)) {
            return "Это видео требует входа в Google.";
        }
        if ("AGE_VERIFICATION_REQUIRED".equals(status)) {
            return "Видео с возрастным ограничением, нужен вход.";
        }
        if ("LIVE_STREAM_OFFLINE".equals(status)) {
            return "Трансляция ещё не началась или уже закончилась.";
        }
        if (!text.isEmpty()) {
            return "Видео недоступно: " + text;
        }
        return "Видео недоступно";
    }

    // ------------------------------------------------------------------- parsers

    private static List<Video> collectVideos(JSONObject root) {
        List<Video> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collectVideos(root, out, seen);
        return out;
    }

    private static void collectVideos(Object node, List<Video> out, Set<String> seen) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            JSONObject renderer = object.optJSONObject("videoRenderer");
            if (renderer != null) {
                Video video = fromVideoRenderer(renderer);
                if (video != null && seen.add(video.id)) {
                    out.add(video);
                }
            }
            JSONObject lockup = object.optJSONObject("lockupViewModel");
            if (lockup != null && "LOCKUP_CONTENT_TYPE_VIDEO".equals(
                    lockup.optString("contentType", ""))) {
                Video video = fromLockup(lockup);
                if (video != null && seen.add(video.id)) {
                    out.add(video);
                }
            }
            for (Iterator<String> it = object.keys(); it.hasNext(); ) {
                collectVideos(object.opt(it.next()), out, seen);
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                collectVideos(array.opt(i), out, seen);
            }
        }
    }

    private static Video fromVideoRenderer(JSONObject renderer) {
        String id = renderer.optString("videoId", "");
        if (id.isEmpty()) {
            return null;
        }
        Video video = new Video();
        video.id = id;
        video.title = text(renderer.opt("title"));
        video.channel = text(renderer.opt("ownerText"));
        video.channelId = browseId(renderer.opt("ownerText"));
        video.publishedText = text(renderer.opt("publishedTimeText"));
        video.views = parseCount(text(renderer.opt("viewCountText")));
        video.duration = text(renderer.opt("lengthText"));
        video.thumbnail = thumbnail(renderer.optJSONObject("thumbnail"));
        JSONArray snippets = renderer.optJSONArray("detailedMetadataSnippets");
        if (snippets != null && snippets.length() > 0) {
            JSONObject snippet = snippets.optJSONObject(0);
            if (snippet != null) {
                video.description = text(snippet.opt("snippetText"));
            }
        }
        if (video.thumbnail.isEmpty()) {
            video.thumbnail = "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
        }
        return video;
    }

    private static Video fromLockup(JSONObject lockup) {
        String id = lockup.optString("contentId", "");
        if (id.isEmpty()) {
            return null;
        }
        Video video = new Video();
        video.id = id;
        JSONObject metadata = lockup.optJSONObject("metadata");
        JSONObject meta = metadata == null ? null
                : metadata.optJSONObject("lockupMetadataViewModel");
        if (meta != null) {
            video.title = text(meta.opt("title"));
            JSONObject content = meta.optJSONObject("metadata");
            JSONObject rows = content == null ? null
                    : content.optJSONObject("contentMetadataViewModel");
            JSONArray rowArray = rows == null ? null : rows.optJSONArray("metadataRows");
            if (rowArray != null) {
                for (int i = 0; i < rowArray.length(); i++) {
                    JSONObject row = rowArray.optJSONObject(i);
                    JSONArray parts = row == null ? null : row.optJSONArray("metadataParts");
                    if (parts == null) {
                        continue;
                    }
                    for (int p = 0; p < parts.length(); p++) {
                        JSONObject part = parts.optJSONObject(p);
                        if (part == null) {
                            continue;
                        }
                        String value = text(part.opt("text"));
                        if (value.isEmpty()) {
                            continue;
                        }
                        String lower = value.toLowerCase(Locale.ROOT);
                        boolean views = lower.contains("просмотр")
                                || lower.contains("view")
                                || lower.contains("watching")
                                || value.matches(
                                "(?i)^[\\d\\s.,]+(тыс\\.?|млн|млрд|k|m|b)?$");
                        if (video.channel.isEmpty()) {
                            video.channel = value;
                        } else if (video.views < 0 && views) {
                            long parsed = parseCount(value);
                            if (parsed >= 0) {
                                video.views = parsed;
                            }
                        } else if (video.publishedText.isEmpty()) {
                            video.publishedText = value;
                        }
                    }
                }
            }
        }
        video.thumbnail = thumbnail(lockup.optJSONObject("contentImage"));
        if (video.thumbnail.isEmpty()) {
            video.thumbnail = "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
        }
        return video;
    }

    private static Video videoFromPlayer(JSONObject response, String videoId) {
        Video video = new Video();
        video.id = videoId;
        JSONObject details = response.optJSONObject("videoDetails");
        if (details != null) {
            video.title = details.optString("title", "");
            video.channel = details.optString("author", "");
            video.channelId = details.optString("channelId", "");
            video.description = details.optString("shortDescription", "");
            video.views = parseCount(details.optString("viewCount", ""));
            String seconds = details.optString("lengthSeconds", "");
            try {
                video.duration = Formats.fromSeconds(Long.parseLong(seconds));
            } catch (NumberFormatException ignored) {
                video.duration = "";
            }
        }
        JSONObject micro = response.optJSONObject("microformat");
        JSONObject renderer = micro == null ? null
                : micro.optJSONObject("playerMicroformatRenderer");
        if (renderer != null) {
            String date = renderer.optString("publishDate", "");
            if (!date.isEmpty()) {
                video.publishedAt = date.length() <= 10 ? date + "T00:00:00Z" : date;
            }
            if (video.thumbnail.isEmpty()) {
                video.thumbnail = thumbnail(renderer.optJSONObject("thumbnail"));
            }
        }
        if (video.title.isEmpty()) {
            video.title = videoId;
        }
        return video;
    }

    private static void readStreams(JSONObject response, Playback playback) {
        JSONObject data = response.optJSONObject("streamingData");
        if (data == null) {
            return;
        }
        JSONArray formats = data.optJSONArray("formats");
        if (formats != null) {
            long bestBitrate = -1;
            for (int i = 0; i < formats.length(); i++) {
                JSONObject format = formats.optJSONObject(i);
                String url = format == null ? "" : format.optString("url", "");
                if (url.isEmpty()) {
                    continue;
                }
                long bitrate = format.optLong("bitrate", 0);
                if (bitrate > bestBitrate) {
                    bestBitrate = bitrate;
                    playback.muxedUrl = url;
                    playback.muxedMime = format.optString("mimeType", "");
                }
            }
        }
        JSONArray adaptive = data.optJSONArray("adaptiveFormats");
        if (adaptive == null) {
            return;
        }
        long bestVideo = -1;
        long bestAudio = -1;
        for (int i = 0; i < adaptive.length(); i++) {
            JSONObject format = adaptive.optJSONObject(i);
            if (format == null) {
                continue;
            }
            String url = format.optString("url", "");
            if (url.isEmpty()) {
                continue;
            }
            String mime = format.optString("mimeType", "");
            long bitrate = format.optLong("bitrate", 0);
            if (mime.startsWith("audio/")) {
                long score = bitrate + (mime.contains("mp4") ? 100000000L : 0);
                if (score > bestAudio) {
                    bestAudio = score;
                    playback.audioUrl = url;
                    playback.audioMime = mime;
                }
            } else if (mime.startsWith("video/")) {
                int height = format.optInt("height", 0);
                if (height > 1080) {
                    continue;
                }
                long score = height * 100000000L + bitrate
                        + (mime.contains("mp4") ? 50000000L : 0);
                if (score > bestVideo) {
                    bestVideo = score;
                    playback.videoUrl = url;
                    playback.videoMime = mime;
                }
            }
        }
    }

    private static String commentToken(JSONObject root) {
        JSONObject section = findCommentSection(root);
        if (section == null) {
            return null;
        }
        return firstContinuation(section);
    }

    private static JSONObject findCommentSection(Object node) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            if ("comment-item-section".equals(object.optString("sectionIdentifier", ""))) {
                return object;
            }
            for (Iterator<String> it = object.keys(); it.hasNext(); ) {
                JSONObject found = findCommentSection(object.opt(it.next()));
                if (found != null) {
                    return found;
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                JSONObject found = findCommentSection(array.opt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String firstContinuation(Object node) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            JSONObject endpoint = object.optJSONObject("continuationEndpoint");
            JSONObject command = endpoint == null ? null
                    : endpoint.optJSONObject("continuationCommand");
            if (command != null) {
                String token = command.optString("token", "");
                if (!token.isEmpty()) {
                    return token;
                }
            }
            for (Iterator<String> it = object.keys(); it.hasNext(); ) {
                String found = firstContinuation(object.opt(it.next()));
                if (found != null) {
                    return found;
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                String found = firstContinuation(array.opt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static List<Comment> commentsFrom(JSONObject root) {
        List<Comment> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collectEntityComments(root, out, seen);
        if (out.isEmpty()) {
            collectRenderedComments(root, out);
        }
        return out;
    }

    private static void collectEntityComments(Object node, List<Comment> out, Set<String> seen) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            JSONObject payload = object.optJSONObject("commentEntityPayload");
            if (payload != null) {
                JSONObject properties = payload.optJSONObject("properties");
                JSONObject content = properties == null ? null
                        : properties.optJSONObject("content");
                String body = content == null ? "" : content.optString("content", "");
                JSONObject author = payload.optJSONObject("author");
                String name = author == null ? "" : author.optString("displayName", "");
                String id = properties == null ? "" : properties.optString("commentId", "");
                if (!body.isEmpty() && !name.isEmpty() && seen.add(id.isEmpty() ? body : id)) {
                    Comment comment = new Comment();
                    comment.author = name;
                    comment.text = body;
                    comment.publishedAt = properties.optString("publishedTime", "");
                    JSONObject toolbar = payload.optJSONObject("toolbar");
                    if (toolbar != null) {
                        comment.likes = parseCount(toolbar.optString("likeCountNotliked", ""));
                    }
                    out.add(comment);
                }
            }
            for (Iterator<String> it = object.keys(); it.hasNext(); ) {
                collectEntityComments(object.opt(it.next()), out, seen);
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                collectEntityComments(array.opt(i), out, seen);
            }
        }
    }

    private static void collectRenderedComments(Object node, List<Comment> out) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            JSONObject renderer = object.optJSONObject("commentRenderer");
            if (renderer != null) {
                Comment comment = new Comment();
                comment.author = text(renderer.opt("authorText"));
                comment.text = text(renderer.opt("contentText"));
                comment.publishedAt = text(renderer.opt("publishedTimeText"));
                comment.likes = parseCount(text(renderer.opt("voteCount")));
                if (!comment.text.isEmpty()) {
                    out.add(comment);
                }
            }
            for (Iterator<String> it = object.keys(); it.hasNext(); ) {
                collectRenderedComments(object.opt(it.next()), out);
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                collectRenderedComments(array.opt(i), out);
            }
        }
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
        JSONObject inner = top == null ? null : top.optJSONObject("snippet");
        if (inner == null) {
            return null;
        }
        Comment comment = new Comment();
        comment.author = inner.optString("authorDisplayName", "");
        comment.text = inner.optString("textDisplay", inner.optString("textOriginal", ""));
        comment.publishedAt = inner.optString("publishedAt", "");
        try {
            comment.likes = Long.parseLong(inner.optString("likeCount", "0"));
        } catch (NumberFormatException ignored) {
            comment.likes = 0;
        }
        return comment;
    }

    // -------------------------------------------------------------------- helpers

    private static JSONObject webContext(Context c) throws JSONException {
        JSONObject client = new JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", WEB_VERSION)
                .put("hl", language(c))
                .put("gl", region(c));
        return new JSONObject().put("client", client);
    }

    private static String language(Context c) {
        String code = region(c);
        if ("RU".equals(code) || "UA".equals(code) || "KZ".equals(code) || "BY".equals(code)) {
            return "ru";
        }
        return "en";
    }

    private static String region(Context c) {
        String code = Prefs.region(c);
        return code == null || code.trim().isEmpty() ? "RU" : code;
    }

    private static String tokenOrEmpty(Context c) {
        if (Prefs.tokenExpiry(c) <= System.currentTimeMillis() + 60000) {
            return "";
        }
        return Prefs.token(c);
    }

    private static String nextFeedQuery(Context c) {
        String[] list = "ru".equals(language(c)) ? QUERIES_RU : QUERIES_EN;
        int index = Prefs.nextFeedIndex(c, list.length);
        return list[index];
    }

    private static List<Video> searchSync(Context c, String query, String token)
            throws IOException, ApiException, JSONException {
        JSONObject body = new JSONObject()
                .put("context", webContext(c))
                .put("query", query);
        return new ArrayList<>(collectVideos(webPost(c, "search", body, token)));
    }

    private static <T> void async(Context c, Cb<T> cb, Call<T> work) {
        final Context app = c.getApplicationContext();
        EXEC.execute(() -> {
            try {
                T result = work.run();
                MAIN.post(() -> cb.ok(result));
            } catch (Exception e) {
                String message;
                if (e instanceof ApiException) {
                    message = e.getMessage();
                } else if (e instanceof IOException) {
                    message = app.getString(R.string.err_network);
                } else {
                    message = app.getString(R.string.err_parse);
                }
                Log.w(TAG, "request failed: " + e);
                MAIN.post(() -> cb.error(message));
            }
        });
    }

    private static String text(Object node) {
        if (node == null || node == JSONObject.NULL) {
            return "";
        }
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            String simple = object.optString("simpleText", "");
            if (!simple.isEmpty()) {
                return simple;
            }
            JSONArray runs = object.optJSONArray("runs");
            if (runs != null) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < runs.length(); i++) {
                    JSONObject run = runs.optJSONObject(i);
                    if (run != null) {
                        builder.append(run.optString("text", ""));
                    }
                }
                return builder.toString().trim();
            }
            String content = object.optString("content", "");
            if (!content.isEmpty()) {
                return content;
            }
            return "";
        }
        return "";
    }

    private static String thumbnail(JSONObject holder) {
        if (holder == null) {
            return "";
        }
        JSONObject image = holder.optJSONObject("image");
        JSONArray sources = null;
        if (image != null) {
            sources = image.optJSONArray("sources");
        }
        if (sources == null) {
            sources = holder.optJSONArray("sources");
        }
        if (sources == null) {
            JSONObject thumbs = holder.optJSONObject("thumbnails");
            if (thumbs != null) {
                for (String key : new String[]{"high", "medium", "standard", "default"}) {
                    JSONObject thumb = thumbs.optJSONObject(key);
                    if (thumb != null) {
                        String url = thumb.optString("url", "");
                        if (!url.isEmpty()) {
                            return url;
                        }
                    }
                }
            }
            return "";
        }
        String best = "";
        int bestWidth = -1;
        for (int i = 0; i < sources.length(); i++) {
            JSONObject source = sources.optJSONObject(i);
            if (source == null) {
                continue;
            }
            int width = source.optInt("width", 0);
            String url = source.optString("url", "");
            if (!url.isEmpty() && width >= bestWidth) {
                bestWidth = width;
                best = url;
            }
        }
        return best;
    }

    private static String browseId(Object ownerText) {
        if (!(ownerText instanceof JSONObject)) {
            return "";
        }
        JSONObject object = (JSONObject) ownerText;
        JSONArray runs = object.optJSONArray("runs");
        if (runs == null || runs.length() == 0) {
            return "";
        }
        JSONObject run = runs.optJSONObject(0);
        JSONObject endpoint = run == null ? null : run.optJSONObject("navigationEndpoint");
        JSONObject browse = endpoint == null ? null : endpoint.optJSONObject("browseEndpoint");
        return browse == null ? "" : browse.optString("browseId", "");
    }

    /** "1,2 млн просмотров", "4.8M", "22.1K subscribers" -> 1200000, 4800000, 22100. */
    static long parseCount(String raw) {
        if (raw == null) {
            return -1;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT).replace('\u00a0', ' ');
        if (value.isEmpty()) {
            return -1;
        }
        Matcher matcher = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(тыс\\.?|млн|млрд|k|m|b)?")
                .matcher(value);
        if (!matcher.find()) {
            return -1;
        }
        String suffix = matcher.group(2);
        double multiplier = 1;
        if (suffix != null) {
            if (suffix.startsWith("тыс") || suffix.equals("k")) {
                multiplier = 1_000d;
            } else if (suffix.equals("млн") || suffix.equals("m")) {
                multiplier = 1_000_000d;
            } else if (suffix.equals("млрд") || suffix.equals("b")) {
                multiplier = 1_000_000_000d;
            }
        }
        try {
            if (multiplier > 1) {
                double number = Double.parseDouble(matcher.group(1).replace(',', '.'));
                return (long) (number * multiplier);
            }
            String digits = value.replaceAll("[^0-9]", "");
            if (digits.isEmpty() || digits.length() > 18) {
                return -1;
            }
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}

package com.wexresprise.margytube;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Google sign-in through the OAuth 2.0 device flow: Google shows a short code,
 * the person confirms it at google.com/device, and the app receives an access
 * token plus a refresh token. No password ever reaches MargyTube, and no API key
 * is involved.
 *
 * The default client is the public "YouTube on TV" OAuth client that Google's own
 * TV builds use, so signing in works without the person registering anything.
 * It is an unofficial route and can be revoked by Google at any time.
 */
public final class GoogleAuth {
    private static final String TAG = "MargyTube";
    private static final String SCOPE = "https://www.googleapis.com/auth/youtube";
    private static final String DEVICE_URL = "https://oauth2.googleapis.com/device/code";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile int pollGeneration;

    public static final class Device {
        public String deviceCode = "";
        public String userCode = "";
        public String verificationUrl = "https://www.google.com/device";
        public int interval = 5;
    }

    private static final class AuthError extends Exception {
        final String code;

        AuthError(String code, String message) {
            super(message == null || message.isEmpty() ? code : message);
            this.code = code;
        }
    }

    private GoogleAuth() {
    }

    /** Asks Google for a code the person can type on any other device. */
    public static void start(Context c, InnerTube.Cb<Device> cb) {
        final Context app = c.getApplicationContext();
        EXEC.execute(() -> {
            try {
                String form = "client_id=" + enc(Prefs.oauthClient(app))
                        + "&scope=" + enc(SCOPE);
                JSONObject response = form(DEVICE_URL, form);
                Device device = new Device();
                device.deviceCode = response.optString("device_code", "");
                device.userCode = response.optString("user_code", "");
                String url = response.optString("verification_url", "");
                if (!url.isEmpty()) {
                    device.verificationUrl = url;
                }
                device.interval = Math.max(5, response.optInt("interval", 5));
                if (device.deviceCode.isEmpty() || device.userCode.isEmpty()) {
                    throw new AuthError("", "Google не выдал код");
                }
                MAIN.post(() -> cb.ok(device));
            } catch (Exception e) {
                MAIN.post(() -> cb.error(message(e, app)));
            }
        });
    }

    /** Polls Google until the code is confirmed, the code expires, or the app stops. */
    public static void poll(Context c, Device device, InnerTube.Cb<String> cb) {
        final Context app = c.getApplicationContext();
        final int generation = ++pollGeneration;
        pollStep(app, device, generation, cb);
    }

    public static void cancel() {
        pollGeneration++;
    }

    private static void pollStep(Context app, Device device, int generation,
                                 InnerTube.Cb<String> cb) {
        EXEC.execute(() -> {
            if (generation != pollGeneration) {
                return;
            }
            try {
                String form = "client_id=" + enc(Prefs.oauthClient(app))
                        + "&device_code=" + enc(device.deviceCode)
                        + "&grant_type=urn:ietf:params:oauth:grant-type:device_code";
                JSONObject response = form(TOKEN_URL, form);
                String access = response.optString("access_token", "");
                if (access.isEmpty()) {
                    throw new AuthError("", "Google не выдал токен");
                }
                Prefs.setSession(app, access, response.optString("refresh_token", ""),
                        response.optLong("expires_in", 3600));
                MAIN.post(() -> {
                    if (generation != pollGeneration) {
                        return;
                    }
                    loadAccount(app, cb);
                });
            } catch (AuthError e) {
                if ("authorization_pending".equals(e.code)) {
                    schedule(app, device, generation, cb);
                } else if ("slow_down".equals(e.code)) {
                    device.interval += 5;
                    schedule(app, device, generation, cb);
                } else if ("access_denied".equals(e.code)) {
                    MAIN.post(() -> cb.error("Вход отменён на сайте Google"));
                } else if ("expired_token".equals(e.code)) {
                    MAIN.post(() -> cb.error("Код устарел, начни вход заново"));
                } else {
                    MAIN.post(() -> cb.error(message(e, app)));
                }
            } catch (Exception e) {
                MAIN.post(() -> cb.error(message(e, app)));
            }
        });
    }

    private static void schedule(Context app, Device device, int generation,
                                 InnerTube.Cb<String> cb) {
        MAIN.postDelayed(() -> {
            if (generation != pollGeneration) {
                return;
            }
            pollStep(app, device, generation, cb);
        }, Math.max(5, device.interval) * 1000L);
    }

    private static void loadAccount(Context app, InnerTube.Cb<String> cb) {
        InnerTube.fetchAccount(app, Prefs.token(app), new InnerTube.Cb<String>() {
            @Override
            public void ok(String name) {
                cb.ok(name);
            }

            @Override
            public void error(String message) {
                // The token is valid even if the channel list is empty or blocked.
                Log.w(TAG, "account name unavailable: " + message);
                cb.ok(Prefs.accountName(app));
            }
        });
    }

    /** Returns a usable access token, refreshing it when it is about to expire. */
    public static void token(Context c, InnerTube.Cb<String> cb) {
        final Context app = c.getApplicationContext();
        String access = Prefs.token(app);
        if (!access.isEmpty() && Prefs.tokenExpiry(app) > System.currentTimeMillis() + 60000) {
            cb.ok(access);
            return;
        }
        final String refresh = Prefs.refreshToken(app);
        if (refresh.isEmpty() && access.isEmpty()) {
            cb.error(c.getString(R.string.err_sign_in));
            return;
        }
        EXEC.execute(() -> {
            try {
                String form = "client_id=" + enc(Prefs.oauthClient(app))
                        + "&refresh_token=" + enc(refresh)
                        + "&grant_type=refresh_token";
                JSONObject response = form(TOKEN_URL, form);
                String token = response.optString("access_token", "");
                if (token.isEmpty()) {
                    throw new AuthError("", "Google не выдал токен");
                }
                Prefs.setSession(app, token, response.optString("refresh_token", refresh),
                        response.optLong("expires_in", 3600));
                MAIN.post(() -> cb.ok(token));
            } catch (AuthError e) {
                String code = e.code;
                MAIN.post(() -> {
                    if (!code.isEmpty() && !"authorization_pending".equals(code)) {
                        Prefs.signOut(app);
                    }
                    cb.error(c.getString(R.string.err_session_expired));
                });
            } catch (Exception e) {
                MAIN.post(() -> cb.error(message(e, app)));
            }
        });
    }

    private static JSONObject form(String url, String body) throws IOException, AuthError {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "MargyTube/1.1");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String text = read(stream);
            JSONObject json;
            try {
                json = new JSONObject(text);
            } catch (Exception e) {
                throw new AuthError("", "Google ответил HTTP " + code);
            }
            if (code < 200 || code >= 300) {
                String error = json.optString("error", "");
                throw new AuthError(error, json.optString("error_description", ""));
            }
            return json;
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
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, count);
            }
        }
        return builder.toString();
    }

    private static String message(Exception e, Context c) {
        if (e instanceof IOException) {
            return c.getString(R.string.err_network);
        }
        String text = e.getMessage();
        return text == null || text.isEmpty() ? c.getString(R.string.sign_in_failed) : text;
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}

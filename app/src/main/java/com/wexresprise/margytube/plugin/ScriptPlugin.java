package com.wexresprise.margytube.plugin;

import android.util.Log;

import com.wexresprise.margytube.Prefs;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A JS plugin from plugin.js. Safe standard objects only: no java.*, no Packages,
 * so a plugin cannot reach the file system or reflection by accident.
 *
 * Besides onFeed and onVideo, a plugin may define onApp and call
 * margytube.applyStyle({...}) to own the whole appearance.
 */
public final class ScriptPlugin {
    private static final String TAG = "MargyTubePlugin";
    private static final long TIMEOUT_MS = 2500;

    private final String id;
    private final File script;
    private final android.content.Context app;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mgtp-js");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean broken;
    private volatile String breakReason;

    public ScriptPlugin(String id, File script, android.content.Context context) {
        this.id = id;
        this.script = script;
        this.app = context.getApplicationContext();
    }

    public String call(String function, String argument) throws Exception {
        if (broken) {
            throw new Exception(breakReason == null ? "плагин остановлен" : breakReason);
        }
        Future<String> future = exec.submit(() -> evaluate(function, argument));
        try {
            return future.get(TIMEOUT_MS + 700, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            broken = true;
            breakReason = "timeout";
            future.cancel(true);
            throw new Exception("timeout");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new Exception(cause == null ? e.getMessage() : cause.getMessage());
        }
    }

    private String evaluate(String function, String argument) throws Exception {
        Context cx = Context.enter();
        try {
            cx.setOptimizationLevel(-1);
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setInstructionObserverThreshold(10000);
            ScriptRuntime.setDeadline(System.currentTimeMillis() + TIMEOUT_MS);

            Scriptable scope = cx.initSafeStandardObjects();
            Scriptable host = cx.newObject(scope);
            ScriptableObject.putProperty(host, "version", 2);
            ScriptableObject.putProperty(host, "log", new BaseFunction() {
                @Override
                public Object call(Context c, Scriptable s, Scriptable thisObj,
                                   Object[] args) {
                    StringBuilder builder = new StringBuilder();
                    for (Object arg : args) {
                        if (builder.length() > 0) {
                            builder.append(' ');
                        }
                        builder.append(Context.toString(arg));
                    }
                    Log.d(TAG, id + ": " + builder);
                    return Undefined.instance;
                }
            });
            ScriptableObject.putProperty(host, "get", new BaseFunction() {
                @Override
                public Object call(Context c, Scriptable s, Scriptable thisObj,
                                   Object[] args) {
                    if (args.length == 0) {
                        return "";
                    }
                    return Prefs.value(app, Context.toString(args[0]));
                }
            });
            ScriptableObject.putProperty(host, "applyStyle", new BaseFunction() {
                @Override
                public Object call(Context c, Scriptable s, Scriptable thisObj,
                                   Object[] args) {
                    if (args.length == 0) {
                        return Boolean.FALSE;
                    }
                    try {
                        String json;
                        Object arg = args[0];
                        if (arg instanceof CharSequence) {
                            json = arg.toString();
                        } else {
                            Object stringified = NativeJSON.stringify(c, s, arg, null, null);
                            if (stringified == null || stringified == Undefined.instance) {
                                return Boolean.FALSE;
                            }
                            json = Context.toString(stringified);
                        }
                        return PluginManager.applyStyle(app, json)
                                ? Boolean.TRUE : Boolean.FALSE;
                    } catch (Exception e) {
                        Log.w(TAG, id + ": bad style", e);
                        return Boolean.FALSE;
                    }
                }
            });
            ScriptableObject.putProperty(scope, "margytube", host);

            try (Reader reader = new InputStreamReader(new FileInputStream(script),
                    StandardCharsets.UTF_8)) {
                cx.evaluateReader(scope, reader, script.getName(), 1, null);
            }

            Object candidate = scope.get(function, scope);
            if (!(candidate instanceof Function)) {
                return null;
            }
            Object result = ((Function) candidate).call(cx, scope, scope,
                    new Object[]{argument});
            if (result == null || result instanceof Undefined) {
                return null;
            }
            return Context.toString(result);
        } finally {
            ScriptRuntime.clearDeadline();
            Context.exit();
        }
    }

    public void close() {
        exec.shutdownNow();
    }
}

package com.wexresprise.margytube.plugin;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;

/**
 * One global Rhino factory that can stop a script mid-loop. The deadline is
 * thread-local because contexts are per-thread; the interpreter checks it every
 * few thousand instructions, so a malicious plugin cannot freeze the UI.
 */
public final class ScriptRuntime extends ContextFactory {
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    private static boolean installed;

    public static synchronized void install() {
        if (installed) {
            return;
        }
        try {
            ContextFactory.initGlobal(new ScriptRuntime());
        } catch (IllegalStateException | SecurityException ignored) {
            // Another component already installed a factory; the deadline backstop
            // in ScriptPlugin still protects the app.
        }
        installed = true;
    }

    static void setDeadline(long millis) {
        DEADLINE.set(millis);
    }

    static void clearDeadline() {
        DEADLINE.remove();
    }

    @Override
    protected void observeInstructionCount(Context cx, int instructionCount) {
        if (Thread.currentThread().isInterrupted()) {
            throw new Error("interrupted");
        }
        Long deadline = DEADLINE.get();
        if (deadline != null && System.currentTimeMillis() > deadline) {
            throw new Error("plugin timeout");
        }
    }
}

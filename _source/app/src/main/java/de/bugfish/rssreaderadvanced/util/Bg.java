package de.bugfish.rssreaderadvanced.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal background-execute / post-to-main-thread helper. */
public final class Bg {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Bg() {
    }

    public interface Task<T> {
        T run();
    }

    public interface Done<T> {
        void onResult(T result);
    }

    public static <T> void run(final Task<T> task, final Done<T> done) {
        POOL.execute(() -> {
            T r = null;
            try {
                r = task.run();
            } catch (Throwable t) {
                // Never let a background failure strand the caller's callback
                // (e.g. a refresh spinner that would otherwise spin forever).
                Log.e("Bg", "Background task failed", t);
            }
            final T result = r;
            MAIN.post(() -> done.onResult(result));
        });
    }

    public static void post(Runnable r) {
        MAIN.post(r);
    }
}

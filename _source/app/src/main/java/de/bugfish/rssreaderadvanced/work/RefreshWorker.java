package de.bugfish.rssreaderadvanced.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import de.bugfish.rssreaderadvanced.net.RefreshEngine;
import de.bugfish.rssreaderadvanced.util.Notifications;
import de.bugfish.rssreaderadvanced.util.Prefs;

/** Periodic background fetch; posts a notification when new items arrive. */
public class RefreshWorker extends Worker {

    public RefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        Prefs prefs = new Prefs(ctx);
        if (!prefs.isAutoRefresh()) {
            return Result.success();
        }
        RefreshEngine.Result r = new RefreshEngine(ctx).refreshAll();
        if (r.newItems > 0 && prefs.isBackgroundPush()) {
            Notifications.notifyNewItems(ctx, r.newItems);
        }
        return Result.success();
    }
}

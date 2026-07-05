package de.bugfish.rssreaderadvanced.work;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import de.bugfish.rssreaderadvanced.util.Prefs;

/** Schedules / cancels the periodic background refresh. */
public final class RefreshScheduler {

    private static final String WORK_NAME = "periodic_refresh";

    private RefreshScheduler() {
    }

    /** Re-applies scheduling to match current preferences. */
    public static void apply(Context context) {
        Prefs prefs = new Prefs(context);
        if (prefs.isAutoRefresh()) {
            schedule(context, prefs.getRefreshMinutes());
        } else {
            cancel(context);
        }
    }

    public static void schedule(Context context, int minutes) {
        // WorkManager enforces a 15-minute minimum period.
        long period = Math.max(15, minutes);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                RefreshWorker.class, period, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
}

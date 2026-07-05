package de.bugfish.rssreaderadvanced.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Thin typed wrapper over the app's SharedPreferences. */
public final class Prefs {

    private static final String FILE = "rss_prefs";

    public static final String KEY_FIRST_RUN_DONE = "first_run_done";
    /** true = save all item content locally; false = read items online only. */
    public static final String KEY_STORE_LOCAL = "store_local";
    public static final String KEY_AUTO_REFRESH = "auto_refresh";
    public static final String KEY_BACKGROUND_PUSH = "background_push";
    public static final String KEY_REFRESH_MINUTES = "refresh_minutes";
    public static final String KEY_SORT_MODE = "sort_mode";
    public static final String KEY_FILTER_SOURCE = "filter_source";
    public static final String KEY_READER_FONT_SP = "reader_font_sp";
    public static final String KEY_SHOW_IMAGES = "show_images";

    public static final int READER_FONT_MIN = 12;
    public static final int READER_FONT_MAX = 30;
    public static final int READER_FONT_DEFAULT = 18;

    private final SharedPreferences sp;

    public Prefs(Context context) {
        this.sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean isFirstRunDone() {
        return sp.getBoolean(KEY_FIRST_RUN_DONE, false);
    }

    public void setFirstRunDone(boolean done) {
        sp.edit().putBoolean(KEY_FIRST_RUN_DONE, done).apply();
    }

    /** true = local storage of full content; false = online reading. */
    public boolean isStoreLocal() {
        return sp.getBoolean(KEY_STORE_LOCAL, true);
    }

    public void setStoreLocal(boolean local) {
        sp.edit().putBoolean(KEY_STORE_LOCAL, local).apply();
    }

    public boolean isAutoRefresh() {
        return sp.getBoolean(KEY_AUTO_REFRESH, false);
    }

    public void setAutoRefresh(boolean on) {
        sp.edit().putBoolean(KEY_AUTO_REFRESH, on).apply();
    }

    public boolean isBackgroundPush() {
        return sp.getBoolean(KEY_BACKGROUND_PUSH, false);
    }

    public void setBackgroundPush(boolean on) {
        sp.edit().putBoolean(KEY_BACKGROUND_PUSH, on).apply();
    }

    public int getRefreshMinutes() {
        return sp.getInt(KEY_REFRESH_MINUTES, 60);
    }

    public void setRefreshMinutes(int minutes) {
        sp.edit().putInt(KEY_REFRESH_MINUTES, minutes).apply();
    }

    public int getSortMode() {
        return sp.getInt(KEY_SORT_MODE, 0);
    }

    public void setSortMode(int mode) {
        sp.edit().putInt(KEY_SORT_MODE, mode).apply();
    }

    public long getFilterSource() {
        return sp.getLong(KEY_FILTER_SOURCE, 0L);
    }

    public void setFilterSource(long sourceId) {
        sp.edit().putLong(KEY_FILTER_SOURCE, sourceId).apply();
    }

    /** Off by default for privacy: loading remote images can leak your IP. */
    public boolean isShowImages() {
        return sp.getBoolean(KEY_SHOW_IMAGES, false);
    }

    public void setShowImages(boolean show) {
        sp.edit().putBoolean(KEY_SHOW_IMAGES, show).apply();
    }

    public int getReaderFontSp() {
        return sp.getInt(KEY_READER_FONT_SP, READER_FONT_DEFAULT);
    }

    /** Stores the reader font size, clamped to the supported range. */
    public void setReaderFontSp(int sp_) {
        int clamped = Math.max(READER_FONT_MIN, Math.min(READER_FONT_MAX, sp_));
        sp.edit().putInt(KEY_READER_FONT_SP, clamped).apply();
    }
}

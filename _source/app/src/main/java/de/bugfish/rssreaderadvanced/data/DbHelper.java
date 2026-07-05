package de.bugfish.rssreaderadvanced.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Owns the SQLite schema for sources, items and favorites. */
public class DbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "rssreader.db";
    public static final int DB_VERSION = 1;

    // sources
    public static final String T_SOURCES = "sources";
    public static final String C_S_ID = "_id";
    public static final String C_S_NAME = "name";
    public static final String C_S_URL = "url";
    public static final String C_S_CREATED = "created_at";

    // items
    public static final String T_ITEMS = "items";
    public static final String C_I_ID = "_id";
    public static final String C_I_SOURCE = "source_id";
    public static final String C_I_GUID = "guid";
    public static final String C_I_TITLE = "title";
    public static final String C_I_LINK = "link";
    public static final String C_I_DESC = "description";
    public static final String C_I_CONTENT = "content";
    public static final String C_I_AUTHOR = "author";
    public static final String C_I_PUBDATE = "pub_date";
    public static final String C_I_FETCHED = "fetched_at";
    public static final String C_I_READ = "read";
    public static final String C_I_FAV = "favorite";

    // favorites (self-contained snapshot)
    public static final String T_FAVS = "favorites";
    public static final String C_F_ID = "_id";
    public static final String C_F_GUID = "guid";
    public static final String C_F_TITLE = "title";
    public static final String C_F_LINK = "link";
    public static final String C_F_DESC = "description";
    public static final String C_F_CONTENT = "content";
    public static final String C_F_AUTHOR = "author";
    public static final String C_F_PUBDATE = "pub_date";
    public static final String C_F_SOURCE_NAME = "source_name";
    public static final String C_F_SAVED = "saved_at";

    public DbHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        // Keep all committed data in the single .db file (no -wal sidecar) so
        // Auto Backup of "rssreader.db" is always complete and consistent.
        setWriteAheadLoggingEnabled(false);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_SOURCES + " ("
                + C_S_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + C_S_NAME + " TEXT NOT NULL, "
                + C_S_URL + " TEXT NOT NULL UNIQUE, "
                + C_S_CREATED + " INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + T_ITEMS + " ("
                + C_I_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + C_I_SOURCE + " INTEGER NOT NULL, "
                + C_I_GUID + " TEXT NOT NULL, "
                + C_I_TITLE + " TEXT, "
                + C_I_LINK + " TEXT, "
                + C_I_DESC + " TEXT, "
                + C_I_CONTENT + " TEXT, "
                + C_I_AUTHOR + " TEXT, "
                + C_I_PUBDATE + " INTEGER NOT NULL DEFAULT 0, "
                + C_I_FETCHED + " INTEGER NOT NULL DEFAULT 0, "
                + C_I_READ + " INTEGER NOT NULL DEFAULT 0, "
                + C_I_FAV + " INTEGER NOT NULL DEFAULT 0, "
                + "FOREIGN KEY(" + C_I_SOURCE + ") REFERENCES " + T_SOURCES + "(" + C_S_ID + ") ON DELETE CASCADE)");

        // De-duplication guarantee: one row per (source, guid).
        db.execSQL("CREATE UNIQUE INDEX idx_items_unique ON " + T_ITEMS
                + " (" + C_I_SOURCE + ", " + C_I_GUID + ")");
        db.execSQL("CREATE INDEX idx_items_pubdate ON " + T_ITEMS + " (" + C_I_PUBDATE + ")");

        db.execSQL("CREATE TABLE " + T_FAVS + " ("
                + C_F_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + C_F_GUID + " TEXT, "
                + C_F_TITLE + " TEXT, "
                + C_F_LINK + " TEXT, "
                + C_F_DESC + " TEXT, "
                + C_F_CONTENT + " TEXT, "
                + C_F_AUTHOR + " TEXT, "
                + C_F_PUBDATE + " INTEGER NOT NULL DEFAULT 0, "
                + C_F_SOURCE_NAME + " TEXT, "
                + C_F_SAVED + " INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Fresh schema only for now.
    }
}

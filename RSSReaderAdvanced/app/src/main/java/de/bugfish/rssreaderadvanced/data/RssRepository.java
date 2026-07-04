package de.bugfish.rssreaderadvanced.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import de.bugfish.rssreaderadvanced.AppConfig;

/** Single entry point for all persistence (sources, items, favorites). */
public class RssRepository {

    // Sort modes for the items list.
    public static final int SORT_DATE_DESC = 0;
    public static final int SORT_DATE_ASC = 1;
    public static final int SORT_TITLE_ASC = 2;
    public static final int SORT_SOURCE_ASC = 3;

    /** addSource() result: refused because the free-build source cap is reached. */
    public static final long ADD_LIMIT_REACHED = -2;

    private final DbHelper helper;

    public RssRepository(Context context) {
        this.helper = new DbHelper(context);
    }

    // ----------------------------------------------------------------- sources

    /**
     * @return new source id; {@link #ADD_LIMIT_REACHED} if the free-build cap is
     * reached; or -1 if the URL already exists / failed.
     *
     * <p>The cap is enforced here, at the single insertion chokepoint, so it
     * cannot be bypassed by any caller (catalogue, custom feed, or OPML import).
     */
    public long addSource(String name, String url) {
        if (AppConfig.FREE_VERSION && getSourceCount() >= AppConfig.FREE_MAX_SOURCES) {
            return ADD_LIMIT_REACHED;
        }
        ContentValues v = new ContentValues();
        v.put(DbHelper.C_S_NAME, name);
        v.put(DbHelper.C_S_URL, url);
        v.put(DbHelper.C_S_CREATED, System.currentTimeMillis());
        try {
            return helper.getWritableDatabase().insertOrThrow(DbHelper.T_SOURCES, null, v);
        } catch (Exception e) {
            return -1;
        }
    }

    public void updateSource(long id, String name, String url) {
        ContentValues v = new ContentValues();
        v.put(DbHelper.C_S_NAME, name);
        v.put(DbHelper.C_S_URL, url);
        helper.getWritableDatabase().update(DbHelper.T_SOURCES, v,
                DbHelper.C_S_ID + "=?", new String[]{String.valueOf(id)});
    }

    public void deleteSource(long id) {
        helper.getWritableDatabase().delete(DbHelper.T_SOURCES,
                DbHelper.C_S_ID + "=?", new String[]{String.valueOf(id)});
    }

    public int getSourceCount() {
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + DbHelper.T_SOURCES, null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public boolean sourceUrlExists(String url) {
        Cursor c = helper.getReadableDatabase().query(DbHelper.T_SOURCES,
                new String[]{DbHelper.C_S_ID}, DbHelper.C_S_URL + "=?",
                new String[]{url}, null, null, null);
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    public List<Source> getSources() {
        List<Source> out = new ArrayList<>();
        String sql = "SELECT s." + DbHelper.C_S_ID + ", s." + DbHelper.C_S_NAME + ", s."
                + DbHelper.C_S_URL + ", s." + DbHelper.C_S_CREATED
                + ", (SELECT COUNT(*) FROM " + DbHelper.T_ITEMS + " i WHERE i."
                + DbHelper.C_I_SOURCE + " = s." + DbHelper.C_S_ID + ") AS cnt"
                + " FROM " + DbHelper.T_SOURCES + " s ORDER BY s." + DbHelper.C_S_NAME + " COLLATE NOCASE ASC";
        Cursor c = helper.getReadableDatabase().rawQuery(sql, null);
        while (c.moveToNext()) {
            Source s = new Source(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3));
            s.itemCount = c.getInt(4);
            out.add(s);
        }
        c.close();
        return out;
    }

    public Source getSource(long id) {
        Cursor c = helper.getReadableDatabase().query(DbHelper.T_SOURCES, null,
                DbHelper.C_S_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        Source s = null;
        if (c.moveToFirst()) {
            s = new Source(
                    c.getLong(c.getColumnIndexOrThrow(DbHelper.C_S_ID)),
                    c.getString(c.getColumnIndexOrThrow(DbHelper.C_S_NAME)),
                    c.getString(c.getColumnIndexOrThrow(DbHelper.C_S_URL)),
                    c.getLong(c.getColumnIndexOrThrow(DbHelper.C_S_CREATED)));
        }
        c.close();
        return s;
    }

    // ------------------------------------------------------------------- items

    /**
     * Inserts parsed items, ignoring duplicates (same source + guid).
     *
     * @param storeContent when false (online mode) the heavy body fields are
     *                     dropped to keep storage light; titles/links are kept
     *                     so the list still renders and de-dup still works.
     * @return number of genuinely new items inserted.
     */
    public int insertItems(long sourceId, List<FeedItem> items, boolean storeContent) {
        SQLiteDatabase db = helper.getWritableDatabase();
        int inserted = 0;
        db.beginTransaction();
        try {
            for (FeedItem it : items) {
                ContentValues v = new ContentValues();
                v.put(DbHelper.C_I_SOURCE, sourceId);
                v.put(DbHelper.C_I_GUID, guidFor(it));
                v.put(DbHelper.C_I_TITLE, it.title);
                v.put(DbHelper.C_I_LINK, it.link);
                if (storeContent) {
                    v.put(DbHelper.C_I_DESC, it.description);
                    v.put(DbHelper.C_I_CONTENT, it.content);
                }
                v.put(DbHelper.C_I_AUTHOR, it.author);
                v.put(DbHelper.C_I_PUBDATE, it.pubDate);
                v.put(DbHelper.C_I_FETCHED, System.currentTimeMillis());
                long id = db.insertWithOnConflict(DbHelper.T_ITEMS, null, v,
                        SQLiteDatabase.CONFLICT_IGNORE);
                if (id != -1) {
                    inserted++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return inserted;
    }

    private static String guidFor(FeedItem it) {
        if (!TextUtils.isEmpty(it.guid)) return it.guid;
        if (!TextUtils.isEmpty(it.link)) return it.link;
        return (it.title == null ? "" : it.title) + "|" + it.pubDate;
    }

    /**
     * @param sourceId 0 means "all sources".
     */
    public List<FeedItem> getItems(long sourceId, int sortMode) {
        return getItems(sourceId, sortMode, null);
    }

    /**
     * @param sourceId 0 means "all sources".
     * @param query    optional case-insensitive title/description filter.
     */
    public List<FeedItem> getItems(long sourceId, int sortMode, String query) {
        String order;
        switch (sortMode) {
            case SORT_DATE_ASC:
                order = "i." + DbHelper.C_I_PUBDATE + " ASC, i." + DbHelper.C_I_ID + " ASC";
                break;
            case SORT_TITLE_ASC:
                order = "i." + DbHelper.C_I_TITLE + " COLLATE NOCASE ASC";
                break;
            case SORT_SOURCE_ASC:
                order = "s." + DbHelper.C_S_NAME + " COLLATE NOCASE ASC, i." + DbHelper.C_I_PUBDATE + " DESC";
                break;
            case SORT_DATE_DESC:
            default:
                order = "i." + DbHelper.C_I_PUBDATE + " DESC, i." + DbHelper.C_I_ID + " DESC";
                break;
        }

        StringBuilder sql = new StringBuilder("SELECT i.*, s." + DbHelper.C_S_NAME
                + " AS s_name FROM " + DbHelper.T_ITEMS + " i JOIN " + DbHelper.T_SOURCES
                + " s ON i." + DbHelper.C_I_SOURCE + " = s." + DbHelper.C_S_ID);

        // Build WHERE from parameterised fragments only (no string concatenation
        // of user input) so search text can never alter the SQL.
        List<String> where = new ArrayList<>();
        List<String> args = new ArrayList<>();
        if (sourceId > 0) {
            where.add("i." + DbHelper.C_I_SOURCE + " = ?");
            args.add(String.valueOf(sourceId));
        }
        if (query != null && !query.trim().isEmpty()) {
            where.add("(i." + DbHelper.C_I_TITLE + " LIKE ? OR i." + DbHelper.C_I_DESC + " LIKE ?)");
            String like = "%" + query.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(TextUtils.join(" AND ", where));
        }
        sql.append(" ORDER BY ").append(order);

        Cursor c = helper.getReadableDatabase().rawQuery(sql.toString(),
                args.isEmpty() ? null : args.toArray(new String[0]));
        List<FeedItem> out = readItems(c);
        c.close();
        return out;
    }

    public FeedItem getItem(long id) {
        Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT i.*, s." + DbHelper.C_S_NAME + " AS s_name FROM " + DbHelper.T_ITEMS
                        + " i JOIN " + DbHelper.T_SOURCES + " s ON i." + DbHelper.C_I_SOURCE
                        + " = s." + DbHelper.C_S_ID + " WHERE i." + DbHelper.C_I_ID + " = ?",
                new String[]{String.valueOf(id)});
        List<FeedItem> list = readItems(c);
        c.close();
        return list.isEmpty() ? null : list.get(0);
    }

    private List<FeedItem> readItems(Cursor c) {
        List<FeedItem> out = new ArrayList<>();
        int iId = c.getColumnIndexOrThrow(DbHelper.C_I_ID);
        int iSource = c.getColumnIndexOrThrow(DbHelper.C_I_SOURCE);
        int iGuid = c.getColumnIndexOrThrow(DbHelper.C_I_GUID);
        int iTitle = c.getColumnIndexOrThrow(DbHelper.C_I_TITLE);
        int iLink = c.getColumnIndexOrThrow(DbHelper.C_I_LINK);
        int iDesc = c.getColumnIndexOrThrow(DbHelper.C_I_DESC);
        int iContent = c.getColumnIndexOrThrow(DbHelper.C_I_CONTENT);
        int iAuthor = c.getColumnIndexOrThrow(DbHelper.C_I_AUTHOR);
        int iPub = c.getColumnIndexOrThrow(DbHelper.C_I_PUBDATE);
        int iFetched = c.getColumnIndexOrThrow(DbHelper.C_I_FETCHED);
        int iRead = c.getColumnIndexOrThrow(DbHelper.C_I_READ);
        int iFav = c.getColumnIndexOrThrow(DbHelper.C_I_FAV);
        int iSname = c.getColumnIndexOrThrow("s_name");
        while (c.moveToNext()) {
            FeedItem it = new FeedItem();
            it.id = c.getLong(iId);
            it.sourceId = c.getLong(iSource);
            it.guid = c.getString(iGuid);
            it.title = c.getString(iTitle);
            it.link = c.getString(iLink);
            it.description = c.getString(iDesc);
            it.content = c.getString(iContent);
            it.author = c.getString(iAuthor);
            it.pubDate = c.getLong(iPub);
            it.fetchedAt = c.getLong(iFetched);
            it.read = c.getInt(iRead) != 0;
            it.favorite = c.getInt(iFav) != 0;
            it.sourceName = c.getString(iSname);
            out.add(it);
        }
        return out;
    }

    /** Marks every item read (sourceId 0 = all sources). @return rows changed. */
    public int markAllRead(long sourceId) {
        return markAllRead(sourceId, null);
    }

    /**
     * Marks read exactly the items that match the current list view — i.e. the
     * same source filter and search query the user is looking at.
     *
     * @return rows changed.
     */
    public int markAllRead(long sourceId, String query) {
        List<String> where = new ArrayList<>();
        List<String> args = new ArrayList<>();
        if (sourceId > 0) {
            where.add(DbHelper.C_I_SOURCE + "=?");
            args.add(String.valueOf(sourceId));
        }
        if (query != null && !query.trim().isEmpty()) {
            where.add("(" + DbHelper.C_I_TITLE + " LIKE ? OR " + DbHelper.C_I_DESC + " LIKE ?)");
            String like = "%" + query.trim() + "%";
            args.add(like);
            args.add(like);
        }
        ContentValues v = new ContentValues();
        v.put(DbHelper.C_I_READ, 1);
        String selection = where.isEmpty() ? null : TextUtils.join(" AND ", where);
        String[] selArgs = args.isEmpty() ? null : args.toArray(new String[0]);
        return helper.getWritableDatabase().update(DbHelper.T_ITEMS, v, selection, selArgs);
    }

    /** Marks read every item that is currently favorited (matched by guid). */
    public int markFavoritesRead() {
        ContentValues v = new ContentValues();
        v.put(DbHelper.C_I_READ, 1);
        return helper.getWritableDatabase().update(DbHelper.T_ITEMS, v,
                DbHelper.C_I_GUID + " IN (SELECT " + DbHelper.C_F_GUID + " FROM " + DbHelper.T_FAVS + ")",
                null);
    }

    public void markRead(long itemId, boolean read) {
        ContentValues v = new ContentValues();
        v.put(DbHelper.C_I_READ, read ? 1 : 0);
        helper.getWritableDatabase().update(DbHelper.T_ITEMS, v,
                DbHelper.C_I_ID + "=?", new String[]{String.valueOf(itemId)});
    }

    // --------------------------------------------------------------- favorites

    /** Toggles favorite state for an item and mirrors it into the favorites table. */
    public boolean toggleFavorite(FeedItem item) {
        boolean nowFav = !isFavoriteGuid(guidFor(item));
        SQLiteDatabase db = helper.getWritableDatabase();
        if (nowFav) {
            ContentValues v = new ContentValues();
            v.put(DbHelper.C_F_GUID, guidFor(item));
            v.put(DbHelper.C_F_TITLE, item.title);
            v.put(DbHelper.C_F_LINK, item.link);
            v.put(DbHelper.C_F_DESC, item.description);
            v.put(DbHelper.C_F_CONTENT, item.content);
            v.put(DbHelper.C_F_AUTHOR, item.author);
            v.put(DbHelper.C_F_PUBDATE, item.pubDate);
            v.put(DbHelper.C_F_SOURCE_NAME, item.sourceName);
            v.put(DbHelper.C_F_SAVED, System.currentTimeMillis());
            db.insert(DbHelper.T_FAVS, null, v);
        } else {
            db.delete(DbHelper.T_FAVS, DbHelper.C_F_GUID + "=?",
                    new String[]{guidFor(item)});
        }
        // Mirror onto the item row if it still exists.
        ContentValues fv = new ContentValues();
        fv.put(DbHelper.C_I_FAV, nowFav ? 1 : 0);
        db.update(DbHelper.T_ITEMS, fv, DbHelper.C_I_ID + "=?",
                new String[]{String.valueOf(item.id)});
        item.favorite = nowFav;
        return nowFav;
    }

    public boolean isFavoriteGuid(String guid) {
        if (guid == null) return false;
        Cursor c = helper.getReadableDatabase().query(DbHelper.T_FAVS,
                new String[]{DbHelper.C_F_ID}, DbHelper.C_F_GUID + "=?",
                new String[]{guid}, null, null, null);
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    public void removeFavorite(long favId) {
        helper.getWritableDatabase().delete(DbHelper.T_FAVS,
                DbHelper.C_F_ID + "=?", new String[]{String.valueOf(favId)});
    }

    public List<Favorite> getFavorites() {
        List<Favorite> out = new ArrayList<>();
        Cursor c = helper.getReadableDatabase().query(DbHelper.T_FAVS, null, null, null,
                null, null, DbHelper.C_F_SAVED + " DESC");
        while (c.moveToNext()) {
            Favorite f = new Favorite();
            f.id = c.getLong(c.getColumnIndexOrThrow(DbHelper.C_F_ID));
            f.guid = c.getString(c.getColumnIndexOrThrow(DbHelper.C_F_GUID));
            f.title = c.getString(c.getColumnIndexOrThrow(DbHelper.C_F_TITLE));
            f.link = c.getString(c.getColumnIndexOrThrow(DbHelper.C_F_LINK));
            f.description = c.getString(c.getColumnIndexOrThrow(DbHelper.C_F_DESC));
            f.content = c.getString(c.getColumnIndexOrThrow(DbHelper.C_F_CONTENT));
            f.author = c.getString(c.getColumnIndexOrThrow(DbHelper.C_F_AUTHOR));
            f.pubDate = c.getLong(c.getColumnIndexOrThrow(DbHelper.C_F_PUBDATE));
            f.sourceName = c.getString(c.getColumnIndexOrThrow(DbHelper.C_F_SOURCE_NAME));
            f.savedAt = c.getLong(c.getColumnIndexOrThrow(DbHelper.C_F_SAVED));
            out.add(f);
        }
        c.close();
        return out;
    }
}

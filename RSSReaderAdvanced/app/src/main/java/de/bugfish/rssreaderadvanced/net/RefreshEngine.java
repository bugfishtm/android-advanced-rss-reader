package de.bugfish.rssreaderadvanced.net;

import android.content.Context;

import java.util.List;

import de.bugfish.rssreaderadvanced.data.RssRepository;
import de.bugfish.rssreaderadvanced.data.Source;
import de.bugfish.rssreaderadvanced.util.Prefs;

/**
 * Shared "fetch every source and store new items" routine, used both by the
 * manual refresh button and the background worker. Runs synchronously, so call
 * it off the main thread.
 */
public class RefreshEngine {

    public static class Result {
        public int newItems;
        public int sourcesOk;
        public int sourcesFailed;
    }

    private final RssRepository repo;
    private final Prefs prefs;

    public RefreshEngine(Context context) {
        this.repo = new RssRepository(context);
        this.prefs = new Prefs(context);
    }

    /** Refresh every subscribed source. */
    public Result refreshAll() {
        Result result = new Result();
        RssFetcher fetcher = new RssFetcher();
        boolean storeContent = prefs.isStoreLocal();
        List<Source> sources = repo.getSources();
        for (Source s : sources) {
            FetchResult fr = fetcher.fetch(s.url);
            if (fr.ok) {
                result.sourcesOk++;
                result.newItems += repo.insertItems(s.id, fr.items, storeContent);
            } else {
                result.sourcesFailed++;
            }
        }
        return result;
    }

    /** Refresh a single source. @return new item count, or -1 on failure. */
    public int refreshSource(Source s) {
        RssFetcher fetcher = new RssFetcher();
        FetchResult fr = fetcher.fetch(s.url);
        if (!fr.ok) return -1;
        return repo.insertItems(s.id, fr.items, prefs.isStoreLocal());
    }
}

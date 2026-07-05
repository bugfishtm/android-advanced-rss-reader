package de.bugfish.rssreaderadvanced.net;

import java.util.ArrayList;
import java.util.List;

import de.bugfish.rssreaderadvanced.data.FeedItem;

/** Outcome of fetching/parsing a feed. */
public class FetchResult {
    public boolean ok;
    public String error;
    /** The feed's own title (used to suggest a name for new custom sources). */
    public String feedTitle;
    public final List<FeedItem> items = new ArrayList<>();
}

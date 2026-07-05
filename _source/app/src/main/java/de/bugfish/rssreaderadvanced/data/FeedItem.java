package de.bugfish.rssreaderadvanced.data;

/** A single article/entry belonging to a {@link Source}. */
public class FeedItem {
    public long id;
    public long sourceId;
    /** Stable identity used for de-duplication (guid, else link, else title). */
    public String guid;
    public String title;
    public String link;
    public String description;
    public String content;
    public String author;
    public long pubDate;
    public long fetchedAt;
    public boolean read;
    public boolean favorite;

    /** Transient, populated when listing items across sources. */
    public String sourceName;
}

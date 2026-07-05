package de.bugfish.rssreaderadvanced.data;

/** A subscribed RSS/Atom feed source. */
public class Source {
    public long id;
    public String name;
    public String url;
    public long createdAt;
    /** Transient, populated by queries that join the item count. */
    public int itemCount;

    public Source() {
    }

    public Source(long id, String name, String url, long createdAt) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.createdAt = createdAt;
    }
}

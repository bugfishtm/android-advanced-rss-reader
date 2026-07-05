package de.bugfish.rssreaderadvanced.data;

/**
 * A favorited article. Self-contained: it snapshots the source name and all
 * article content so it survives deletion of the original source or item.
 */
public class Favorite {
    public long id;
    public String guid;
    public String title;
    public String link;
    public String description;
    public String content;
    public String author;
    public long pubDate;
    public String sourceName;
    public long savedAt;
}

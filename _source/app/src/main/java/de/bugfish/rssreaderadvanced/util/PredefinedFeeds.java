package de.bugfish.rssreaderadvanced.util;

import java.util.ArrayList;
import java.util.List;

/** A curated catalogue of well-known public news feeds offered when adding a source. */
public final class PredefinedFeeds {

    public static final class Entry {
        public final String name;
        public final String url;
        public final String category;

        public Entry(String name, String url, String category) {
            this.name = name;
            this.url = url;
            this.category = category;
        }
    }

    private PredefinedFeeds() {
    }

    public static List<Entry> all() {
        List<Entry> list = new ArrayList<>();
        // World / general news
        list.add(new Entry("BBC News – Top Stories", "https://feeds.bbci.co.uk/news/rss.xml", "World"));
        list.add(new Entry("BBC News – World", "https://feeds.bbci.co.uk/news/world/rss.xml", "World"));
        list.add(new Entry("Reuters – Top News", "https://www.reutersagency.com/feed/?best-topics=top-news&post_type=best", "World"));
        list.add(new Entry("The Guardian – World", "https://www.theguardian.com/world/rss", "World"));
        list.add(new Entry("Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", "World"));
        list.add(new Entry("NPR News", "https://feeds.npr.org/1001/rss.xml", "World"));
        list.add(new Entry("Deutsche Welle", "https://rss.dw.com/rdf/rss-en-all", "World"));

        // Technology
        list.add(new Entry("The Verge", "https://www.theverge.com/rss/index.xml", "Technology"));
        list.add(new Entry("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "Technology"));
        list.add(new Entry("Wired", "https://www.wired.com/feed/rss", "Technology"));
        list.add(new Entry("TechCrunch", "https://techcrunch.com/feed/", "Technology"));
        list.add(new Entry("Engadget", "https://www.engadget.com/rss.xml", "Technology"));
        list.add(new Entry("Hacker News (front page)", "https://hnrss.org/frontpage", "Technology"));

        // Science
        list.add(new Entry("NASA Breaking News", "https://www.nasa.gov/feed/", "Science"));
        list.add(new Entry("Science Daily", "https://www.sciencedaily.com/rss/all.xml", "Science"));
        list.add(new Entry("Nature – Latest", "https://www.nature.com/nature.rss", "Science"));

        // Business
        list.add(new Entry("BBC – Business", "https://feeds.bbci.co.uk/news/business/rss.xml", "Business"));
        list.add(new Entry("The Guardian – Business", "https://www.theguardian.com/uk/business/rss", "Business"));

        // Sport
        list.add(new Entry("BBC – Sport", "https://feeds.bbci.co.uk/sport/rss.xml", "Sport"));
        list.add(new Entry("ESPN – Top Headlines", "https://www.espn.com/espn/rss/news", "Sport"));

        // Germany (developer locale)
        list.add(new Entry("Tagesschau", "https://www.tagesschau.de/index~rss2.xml", "Germany"));
        list.add(new Entry("Spiegel", "https://www.spiegel.de/schlagzeilen/index.rss", "Germany"));
        list.add(new Entry("heise online", "https://www.heise.de/rss/heise-atom.xml", "Germany"));
        return list;
    }
}

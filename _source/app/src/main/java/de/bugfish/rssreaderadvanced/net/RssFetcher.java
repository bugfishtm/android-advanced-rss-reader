package de.bugfish.rssreaderadvanced.net;

import android.text.TextUtils;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import de.bugfish.rssreaderadvanced.data.FeedItem;

/**
 * Downloads and parses an RSS 2.0 or Atom feed using only the platform's
 * {@link XmlPullParser} and {@link HttpURLConnection} (no external libraries).
 */
public class RssFetcher {

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 20000;
    private static final int MAX_REDIRECTS = 5;
    /** Hard cap on how many bytes we will read from a feed (8 MB) to avoid OOM. */
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    /** Hard cap on how many items we keep from one feed. */
    private static final int MAX_ITEMS = 500;

    public FetchResult fetch(String feedUrl) {
        FetchResult result = new FetchResult();
        // Only ever talk plain HTTP(S). Anything else (file:, content:, ftp:, …)
        // from a malicious "feed URL" is refused outright.
        if (!isHttpScheme(feedUrl)) {
            result.ok = false;
            result.error = "Unsupported URL scheme";
            return result;
        }
        InputStream in = null;
        HttpURLConnection conn = null;
        try {
            conn = open(feedUrl, 0);
            in = new LimitedInputStream(conn.getInputStream(), MAX_BYTES);
            parse(in, result);
            result.ok = true;
        } catch (Exception e) {
            result.ok = false;
            result.error = e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : "");
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
        return result;
    }

    private HttpURLConnection open(String urlStr, int redirectCount) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent", "RSSReaderAdvanced/1.0 (Android)");
        conn.setRequestProperty("Accept",
                "application/rss+xml, application/atom+xml, application/xml, text/xml, */*");
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        // Manually follow cross-protocol redirects (http <-> https), which the
        // platform does not auto-follow.
        if ((code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_SEE_OTHER
                || code == 307 || code == 308)
                && redirectCount < MAX_REDIRECTS) {
            String loc = conn.getHeaderField("Location");
            conn.disconnect();
            if (!TextUtils.isEmpty(loc)) {
                String target = new URL(url, loc).toString();
                // A redirect must not be used to escape http(s) (e.g. to file:).
                if (!isHttpScheme(target)) {
                    throw new IOException("Refused redirect to unsupported scheme");
                }
                return open(target, redirectCount + 1);
            }
        }
        if (code >= 400) {
            conn.disconnect();
            throw new IOException("HTTP " + code);
        }
        return conn;
    }

    private void parse(InputStream in, FetchResult result)
            throws XmlPullParserException, IOException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        // Do NOT process DOCTYPE declarations. This keeps the parser from
        // expanding entities, which neutralises XML entity-expansion ("billion
        // laughs") and external-entity (XXE) attacks from a hostile feed.
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
        parser.setInput(in, null);

        FeedItem current = null;
        boolean inItem = false;
        boolean feedTitleSet = false;
        // Atom <link> can appear at feed level and entry level; prefer entry alternate.
        String atomLinkAlternate = null;

        int event = parser.getEventType();
        String text = "";
        while (event != XmlPullParser.END_DOCUMENT) {
            String name = parser.getName();
            switch (event) {
                case XmlPullParser.START_TAG:
                    if (name == null) break;
                    if (name.equalsIgnoreCase("item") || name.equalsIgnoreCase("entry")) {
                        inItem = true;
                        current = new FeedItem();
                        atomLinkAlternate = null;
                    } else if (inItem && current != null && name.equalsIgnoreCase("link")) {
                        // Atom links carry the URL in an href attribute.
                        String href = parser.getAttributeValue(null, "href");
                        if (!TextUtils.isEmpty(href)) {
                            String rel = parser.getAttributeValue(null, "rel");
                            if (rel == null || rel.equalsIgnoreCase("alternate")) {
                                atomLinkAlternate = href;
                            } else if (TextUtils.isEmpty(current.link)) {
                                current.link = href;
                            }
                        }
                    }
                    text = "";
                    break;

                case XmlPullParser.TEXT:
                    text = parser.getText();
                    break;

                case XmlPullParser.CDSECT:
                    // CDATA segments are delivered separately from TEXT.
                    text = parser.getText();
                    break;

                case XmlPullParser.END_TAG:
                    if (name == null) break;
                    if (name.equalsIgnoreCase("item") || name.equalsIgnoreCase("entry")) {
                        if (current != null) {
                            if (TextUtils.isEmpty(current.link) && atomLinkAlternate != null) {
                                current.link = atomLinkAlternate;
                            }
                            normalize(current);
                            if (result.items.size() < MAX_ITEMS) {
                                result.items.add(current);
                            }
                        }
                        inItem = false;
                        current = null;
                    } else if (inItem && current != null) {
                        applyItemField(current, name, text);
                    } else if (!feedTitleSet && name.equalsIgnoreCase("title")) {
                        // First title seen outside an item is the feed/channel title.
                        if (!TextUtils.isEmpty(text)) {
                            result.feedTitle = text.trim();
                            feedTitleSet = true;
                        }
                    }
                    text = "";
                    break;
                default:
                    break;
            }
            event = parser.next();
        }
    }

    private void applyItemField(FeedItem item, String tag, String value) {
        if (value == null) value = "";
        String v = value.trim();
        if (tag.equalsIgnoreCase("title")) {
            if (TextUtils.isEmpty(item.title)) item.title = v;
        } else if (tag.equalsIgnoreCase("link")) {
            if (TextUtils.isEmpty(item.link) && !TextUtils.isEmpty(v)) item.link = v;
        } else if (tag.equalsIgnoreCase("guid") || tag.equalsIgnoreCase("id")) {
            if (TextUtils.isEmpty(item.guid)) item.guid = v;
        } else if (tag.equalsIgnoreCase("description") || tag.equalsIgnoreCase("summary")) {
            if (TextUtils.isEmpty(item.description)) item.description = v;
        } else if (tag.equalsIgnoreCase("content") || tag.equalsIgnoreCase("content:encoded")
                || tag.equalsIgnoreCase("encoded")) {
            if (v.length() > safeLen(item.content)) item.content = v;
        } else if (tag.equalsIgnoreCase("author") || tag.equalsIgnoreCase("creator")
                || tag.equalsIgnoreCase("dc:creator") || tag.equalsIgnoreCase("name")) {
            if (TextUtils.isEmpty(item.author) && !TextUtils.isEmpty(v)) item.author = v;
        } else if (tag.equalsIgnoreCase("pubDate") || tag.equalsIgnoreCase("published")
                || tag.equalsIgnoreCase("updated") || tag.equalsIgnoreCase("date")
                || tag.equalsIgnoreCase("dc:date")) {
            long parsed = parseDate(v);
            if (parsed > 0 && item.pubDate == 0) item.pubDate = parsed;
        }
    }

    private static int safeLen(String s) {
        return s == null ? 0 : s.length();
    }

    private void normalize(FeedItem item) {
        if (item.title == null) item.title = "(untitled)";
        if (item.description == null) item.description = "";
        if (item.pubDate == 0) item.pubDate = System.currentTimeMillis();
    }

    // ------------------------------------------------------------- date parsing

    private static final String[] DATE_PATTERNS = {
            "EEE, dd MMM yyyy HH:mm:ss Z",   // RFC 822 (RSS)
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm Z",
            "dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssZ",        // ISO 8601 (Atom)
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd"
    };

    private long parseDate(String value) {
        if (TextUtils.isEmpty(value)) return 0;
        String v = value.trim();
        for (String pattern : DATE_PATTERNS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                if (pattern.endsWith("'Z'") || pattern.equals("yyyy-MM-dd")
                        || pattern.equals("yyyy-MM-dd'T'HH:mm:ss")) {
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date d = sdf.parse(v);
                if (d != null) return d.getTime();
            } catch (ParseException ignored) {
            }
        }
        return 0;
    }

    private static boolean isHttpScheme(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String lower = url.trim().toLowerCase(Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /** Throws once more than {@code limit} bytes have been read from the feed. */
    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1 && ++count > limit) {
                throw new IOException("Feed exceeds size limit");
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                count += n;
                if (count > limit) {
                    throw new IOException("Feed exceeds size limit");
                }
            }
            return n;
        }
    }
}

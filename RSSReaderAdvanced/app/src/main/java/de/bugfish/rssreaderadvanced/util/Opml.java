package de.bugfish.rssreaderadvanced.util;

import android.text.TextUtils;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import de.bugfish.rssreaderadvanced.data.Source;

/**
 * OPML import/export for feed subscriptions.
 *
 * <p>Import treats the file as fully untrusted: DOCTYPE/entity processing is
 * disabled (XXE / entity-expansion safe) and every imported {@code xmlUrl} must
 * be a plain http(s) URL ({@link Web#isHttpUrl}) before it is accepted.
 */
public final class Opml {

    /** Upper bound on imported feeds to bound work/memory. */
    private static final int MAX_IMPORT = 1000;

    public static final class Entry {
        public final String name;
        public final String url;

        public Entry(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    private Opml() {
    }

    /** Serialises the given sources to an OPML document, or null on failure. */
    public static String export(List<Source> sources) {
        try {
            XmlSerializer s = Xml.newSerializer();
            StringWriter writer = new StringWriter();
            s.setOutput(writer);
            s.startDocument("UTF-8", Boolean.TRUE);
            s.startTag(null, "opml");
            s.attribute(null, "version", "1.0");
            s.startTag(null, "head");
            s.startTag(null, "title");
            s.text("RSS Reader Advanced subscriptions");
            s.endTag(null, "title");
            s.endTag(null, "head");
            s.startTag(null, "body");
            for (Source src : sources) {
                s.startTag(null, "outline");
                s.attribute(null, "text", nz(src.name));
                s.attribute(null, "title", nz(src.name));
                s.attribute(null, "type", "rss");
                s.attribute(null, "xmlUrl", nz(src.url));
                s.endTag(null, "outline");
            }
            s.endTag(null, "body");
            s.endTag(null, "opml");
            s.endDocument();
            return writer.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Parses an OPML stream into validated feed entries. */
    public static List<Entry> parse(InputStream in) throws Exception {
        List<Entry> out = new ArrayList<>();
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
        p.setInput(in, null);

        int event = p.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG
                    && "outline".equalsIgnoreCase(p.getName())) {
                String url = p.getAttributeValue(null, "xmlUrl");
                if (TextUtils.isEmpty(url)) url = p.getAttributeValue(null, "url");
                if (Web.isHttpUrl(url)) {
                    String name = p.getAttributeValue(null, "title");
                    if (TextUtils.isEmpty(name)) name = p.getAttributeValue(null, "text");
                    if (TextUtils.isEmpty(name)) name = url;
                    out.add(new Entry(name.trim(), url.trim()));
                    if (out.size() >= MAX_IMPORT) break;
                }
            }
            event = p.next();
        }
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

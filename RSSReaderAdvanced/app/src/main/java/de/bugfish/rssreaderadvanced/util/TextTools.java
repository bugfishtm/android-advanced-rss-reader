package de.bugfish.rssreaderadvanced.util;

import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;

import java.text.DateFormat;
import java.util.Date;

/** Small formatting helpers for list previews. */
public final class TextTools {

    private TextTools() {
    }

    /**
     * Renders feed HTML to a formatted, link-preserving {@link Spanned} for the
     * in-app reader. Script/style/iframe/object blocks and images are removed
     * first as defence-in-depth (Html.fromHtml ignores scripts anyway, and we
     * never enable an ImageGetter, so nothing is fetched or executed).
     */
    public static Spanned richText(String html) {
        return richText(html, false, null);
    }

    /**
     * @param keepImages  when true, {@code <img>} tags are preserved and rendered
     *                    through {@code imageGetter}; when false they are removed.
     * @param imageGetter resolver for images (only used when {@code keepImages}).
     */
    public static Spanned richText(String html, boolean keepImages, Html.ImageGetter imageGetter) {
        if (TextUtils.isEmpty(html)) return new android.text.SpannableString("");
        String cleaned = html
                .replaceAll("(?is)<script.*?</script>", "")
                .replaceAll("(?is)<style.*?</style>", "")
                .replaceAll("(?is)<iframe.*?</iframe>", "")
                .replaceAll("(?is)<object.*?</object>", "")
                .replaceAll("(?is)<embed[^>]*>", "");
        if (!keepImages) {
            cleaned = cleaned.replaceAll("(?is)<img[^>]*>", "");
        }
        return Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY, imageGetter, null);
    }

    /** Strips HTML tags to a single-line plain-text snippet. */
    public static String plain(String html) {
        if (TextUtils.isEmpty(html)) return "";
        CharSequence cs = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        return cs.toString().replaceAll("\\s+", " ").trim();
    }

    public static String snippet(String html, int max) {
        String s = plain(html);
        if (s.length() <= max) return s;
        return s.substring(0, max).trim() + "…";
    }

    public static String formatDate(long millis) {
        if (millis <= 0) return "";
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        return df.format(new Date(millis));
    }

    public static String relativeOrDate(long millis) {
        if (millis <= 0) return "";
        long diff = System.currentTimeMillis() - millis;
        long min = diff / 60000L;
        if (min < 1) return "just now";
        if (min < 60) return min + " min ago";
        long hours = min / 60;
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        if (days < 7) return days + (days == 1 ? " day ago" : " days ago");
        return formatDate(millis);
    }
}

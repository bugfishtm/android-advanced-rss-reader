package de.bugfish.rssreaderadvanced.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import de.bugfish.rssreaderadvanced.R;

/**
 * URL-safety and WebView-hardening helpers.
 *
 * <p>Feed content is fully untrusted, so any URL that originates from a feed
 * (an item link, a link inside the article HTML, a redirect target) is treated
 * as hostile until proven to be a plain {@code http}/{@code https} web address.
 * This blocks {@code javascript:}, {@code file:}, {@code content:},
 * {@code data:}, {@code intent:} and similar schemes that could read local
 * files or run code.
 */
public final class Web {

    private Web() {
    }

    /** True only for absolute http/https URLs with a host. */
    public static boolean isHttpUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        try {
            Uri uri = Uri.parse(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null) return false;
            scheme = scheme.toLowerCase();
            return (scheme.equals("http") || scheme.equals("https"))
                    && !TextUtils.isEmpty(uri.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    /** Opens a URL in an external browser, but only if it is a safe web URL. */
    public static void openExternally(Context context, String url) {
        if (!isHttpUrl(url)) {
            Toast.makeText(context, R.string.blocked_link, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.blocked_link, Toast.LENGTH_SHORT).show();
        }
    }

    /** Locks a WebView down before it loads any feed-supplied page. */
    public static void harden(WebView web) {
        WebSettings s = web.getSettings();
        // JavaScript stays on so real news sites render, but every avenue to the
        // local device is closed off.
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setGeolocationEnabled(false);
        s.setSupportMultipleWindows(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(true);
        }
        web.setNetworkAvailable(true);
        // We never register a JavaScript interface, so page JS cannot call back
        // into the app.
    }

    /**
     * Returns a copy of {@code html} where each link opens through
     * {@link #openExternally} (with scheme validation) instead of Android's
     * default URL handling.
     */
    public static CharSequence linkifySafely(final Context context, Spanned html) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(html);
        URLSpan[] spans = ssb.getSpans(0, ssb.length(), URLSpan.class);
        for (URLSpan span : spans) {
            final String url = span.getURL();
            int start = ssb.getSpanStart(span);
            int end = ssb.getSpanEnd(span);
            int flags = ssb.getSpanFlags(span);
            ssb.removeSpan(span);
            ssb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    openExternally(context, url);
                }
            }, start, end, flags);
        }
        return ssb;
    }
}

package de.bugfish.rssreaderadvanced.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import de.bugfish.rssreaderadvanced.R;
import de.bugfish.rssreaderadvanced.data.FeedItem;
import de.bugfish.rssreaderadvanced.data.RssRepository;
import de.bugfish.rssreaderadvanced.util.Bg;
import de.bugfish.rssreaderadvanced.util.HtmlImageGetter;
import de.bugfish.rssreaderadvanced.util.Net;
import de.bugfish.rssreaderadvanced.util.Prefs;
import de.bugfish.rssreaderadvanced.util.TextTools;
import de.bugfish.rssreaderadvanced.util.Web;

/** Reads a single article either as stored text (local) or online (WebView). */
public class ItemDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ITEM_ID = "item_id";
    public static final String EXTRA_FAVORITE_MODE = "favorite_mode";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_CONTENT = "content";
    public static final String EXTRA_DESC = "desc";
    public static final String EXTRA_LINK = "link";
    public static final String EXTRA_AUTHOR = "author";
    public static final String EXTRA_PUBDATE = "pubdate";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_GUID = "guid";

    private RssRepository repo;
    private Prefs prefs;
    private FeedItem item;
    private boolean favoriteMode;
    private boolean isFavorite;

    private MaterialToolbar toolbar;
    private View scrollLocal;
    private WebView webView;
    private TextView titleView;
    private TextView metaView;
    private TextView contentView;
    private MaterialButton openWebButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);

        repo = new RssRepository(this);
        prefs = new Prefs(this);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(this::onMenu);

        scrollLocal = findViewById(R.id.scroll_local);
        webView = findViewById(R.id.webview);
        titleView = findViewById(R.id.title);
        metaView = findViewById(R.id.meta);
        contentView = findViewById(R.id.content);
        openWebButton = findViewById(R.id.btn_open_web);

        // Let the WebView consume Back for in-page navigation before we finish.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        favoriteMode = getIntent().getBooleanExtra(EXTRA_FAVORITE_MODE, false);
        long itemId = getIntent().getLongExtra(EXTRA_ITEM_ID, -1);

        if (itemId > 0) {
            Bg.run(() -> repo.getItem(itemId), loaded -> {
                if (loaded == null) {
                    Toast.makeText(this, R.string.invalid_feed, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                item = loaded;
                bind();
            });
        } else {
            item = fromExtras();
            bind();
        }
    }

    private FeedItem fromExtras() {
        FeedItem fi = new FeedItem();
        android.content.Intent i = getIntent();
        fi.title = i.getStringExtra(EXTRA_TITLE);
        fi.content = i.getStringExtra(EXTRA_CONTENT);
        fi.description = i.getStringExtra(EXTRA_DESC);
        fi.link = i.getStringExtra(EXTRA_LINK);
        fi.author = i.getStringExtra(EXTRA_AUTHOR);
        fi.pubDate = i.getLongExtra(EXTRA_PUBDATE, 0);
        fi.sourceName = i.getStringExtra(EXTRA_SOURCE);
        fi.guid = i.getStringExtra(EXTRA_GUID);
        return fi;
    }

    private void bind() {
        titleView.setText(item.title);
        metaView.setText(buildMeta());

        Bg.run(() -> repo.isFavoriteGuid(guid()), fav -> {
            isFavorite = fav;
            updateFavoriteIcon();
        });

        // Favorites are always read from their stored snapshot; normal items
        // follow the user's online/local preference. We only ever open the
        // WebView for a validated http(s) link.
        boolean wantOnline = !favoriteMode && !prefs.isStoreLocal();
        boolean linkOk = Web.isHttpUrl(item.link);

        if (wantOnline && linkOk) {
            if (Net.isOnline(this)) {
                showOnline();
            } else {
                // No connection in online mode: fall back to whatever we have,
                // with a clear explanation.
                showLocal(getString(R.string.offline_article));
            }
        } else {
            showLocal(null);
        }
    }

    private void showLocal(@Nullable String prefixNote) {
        scrollLocal.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);

        String body = !TextUtils.isEmpty(item.content) ? item.content : item.description;
        if (TextUtils.isEmpty(body)) {
            contentView.setText(prefixNote != null ? prefixNote
                    : getString(R.string.no_content_online));
        } else {
            // Only fetch remote images when the user has opted in (privacy).
            android.text.Spanned spanned = prefs.isShowImages()
                    ? TextTools.richText(body, true, new HtmlImageGetter(this, contentView))
                    : TextTools.richText(body);
            CharSequence rich = Web.linkifySafely(this, spanned);
            if (prefixNote != null) {
                android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
                sb.append(prefixNote).append("\n\n").append(rich);
                contentView.setText(sb);
            } else {
                contentView.setText(rich);
            }
            // Make the safe links tappable.
            contentView.setMovementMethod(LinkMovementMethod.getInstance());
        }

        if (Web.isHttpUrl(item.link)) {
            openWebButton.setVisibility(View.VISIBLE);
            openWebButton.setOnClickListener(v -> Web.openExternally(this, item.link));
        } else {
            openWebButton.setVisibility(View.GONE);
        }
        applyReaderFont();
    }

    /** Applies the saved reader font size to the in-app text and the WebView. */
    private void applyReaderFont() {
        int sp = prefs.getReaderFontSp();
        contentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        webView.getSettings().setTextZoom(Math.round(sp * 100f / Prefs.READER_FONT_DEFAULT));
    }

    private void showOnline() {
        scrollLocal.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        final View progress = findViewById(R.id.progress);
        progress.setVisibility(View.VISIBLE);

        Web.harden(webView);
        // Block remote image loading unless the user has opted in (privacy).
        boolean showImages = prefs.isShowImages();
        webView.getSettings().setLoadsImagesAutomatically(showImages);
        webView.getSettings().setBlockNetworkImage(!showImages);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Keep navigation inside the WebView to safe web URLs only;
                // anything else (intent:, market:, tel:, javascript:, …) is
                // refused rather than dispatched.
                String url = request.getUrl() != null ? request.getUrl().toString() : null;
                if (Web.isHttpUrl(url)) {
                    return false; // let the WebView load it
                }
                return true; // block
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(ItemDetailActivity.this, R.string.web_load_error,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        applyReaderFont();
        webView.loadUrl(item.link);
    }

    private CharSequence buildMeta() {
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(item.sourceName)) sb.append(item.sourceName);
        String date = TextTools.formatDate(item.pubDate);
        if (!TextUtils.isEmpty(date)) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(date);
        }
        if (!TextUtils.isEmpty(item.author)) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(getString(R.string.by_author, item.author));
        }
        return sb.toString();
    }

    private String guid() {
        if (!TextUtils.isEmpty(item.guid)) return item.guid;
        if (!TextUtils.isEmpty(item.link)) return item.link;
        return (item.title == null ? "" : item.title) + "|" + item.pubDate;
    }

    private void updateFavoriteIcon() {
        if (toolbar.getMenu().findItem(R.id.action_favorite) != null) {
            toolbar.getMenu().findItem(R.id.action_favorite)
                    .setIcon(isFavorite ? R.drawable.ic_star : R.drawable.ic_star_border);
        }
    }

    private boolean onMenu(android.view.MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.action_favorite) {
            toggleFavorite();
            return true;
        } else if (id == R.id.action_open_web) {
            Web.openExternally(this, item.link);
            return true;
        } else if (id == R.id.action_share) {
            share();
            return true;
        } else if (id == R.id.action_email) {
            sendEmail();
            return true;
        } else if (id == R.id.action_copy_link) {
            copyLink();
            return true;
        } else if (id == R.id.action_text_larger) {
            prefs.setReaderFontSp(prefs.getReaderFontSp() + 2);
            applyReaderFont();
            return true;
        } else if (id == R.id.action_text_smaller) {
            prefs.setReaderFontSp(prefs.getReaderFontSp() - 2);
            applyReaderFont();
            return true;
        }
        return false;
    }

    private void sendEmail() {
        String subject = item.title == null ? "" : item.title;
        StringBuilder body = new StringBuilder();
        if (item.title != null) body.append(item.title).append("\n\n");
        String snippet = TextTools.snippet(
                !TextUtils.isEmpty(item.description) ? item.description : item.content, 400);
        if (!TextUtils.isEmpty(snippet)) body.append(snippet).append("\n\n");
        if (Web.isHttpUrl(item.link)) body.append(item.link);

        // ACTION_SENDTO with a mailto: URI targets email apps only.
        android.content.Intent email = new android.content.Intent(
                android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:"));
        email.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
        email.putExtra(android.content.Intent.EXTRA_TEXT, body.toString());
        try {
            startActivity(android.content.Intent.createChooser(email, getString(R.string.send_email)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_email_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyLink() {
        if (!Web.isHttpUrl(item.link)) {
            Toast.makeText(this, R.string.blocked_link, Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("link", item.link));
            Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleFavorite() {
        if (item.guid == null) item.guid = guid();
        Bg.run(() -> repo.toggleFavorite(item), nowFav -> {
            isFavorite = nowFav;
            updateFavoriteIcon();
            Toast.makeText(this, nowFav ? R.string.favorited : R.string.unfavorited,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void share() {
        if (!Web.isHttpUrl(item.link) && TextUtils.isEmpty(item.title)) return;
        android.content.Intent share = new android.content.Intent(android.content.Intent.ACTION_SEND);
        share.setType("text/plain");
        String text = (item.title == null ? "" : item.title)
                + (Web.isHttpUrl(item.link) ? "\n" + item.link : "");
        share.putExtra(android.content.Intent.EXTRA_TEXT, text);
        startActivity(android.content.Intent.createChooser(share, getString(R.string.share)));
    }

    @Override
    protected void onDestroy() {
        // Tear the WebView down cleanly to avoid leaks.
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(new WebViewClient());
            webView.loadUrl("about:blank");
            webView.removeAllViews();
            webView.destroy();
        }
        super.onDestroy();
    }
}

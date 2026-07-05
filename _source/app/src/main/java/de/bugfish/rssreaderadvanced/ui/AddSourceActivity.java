package de.bugfish.rssreaderadvanced.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.bugfish.rssreaderadvanced.AppConfig;
import de.bugfish.rssreaderadvanced.R;
import de.bugfish.rssreaderadvanced.data.RssRepository;
import de.bugfish.rssreaderadvanced.data.Source;
import de.bugfish.rssreaderadvanced.net.FetchResult;
import de.bugfish.rssreaderadvanced.net.RefreshEngine;
import de.bugfish.rssreaderadvanced.net.RssFetcher;
import de.bugfish.rssreaderadvanced.util.Bg;
import de.bugfish.rssreaderadvanced.util.Net;
import de.bugfish.rssreaderadvanced.util.PredefinedFeeds;
import de.bugfish.rssreaderadvanced.util.Store;
import de.bugfish.rssreaderadvanced.ui.adapters.PredefinedAdapter;

/** Add a feed: validate + add a custom URL, or one-tap add from the catalogue. */
public class AddSourceActivity extends AppCompatActivity {

    private RssRepository repo;
    private TextInputEditText urlInput;
    private TextInputEditText nameInput;
    private ProgressBar progress;
    private MaterialButton addButton;
    private PredefinedAdapter catalogueAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_source);
        repo = new RssRepository(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        urlInput = findViewById(R.id.input_url);
        nameInput = findViewById(R.id.input_name);
        progress = findViewById(R.id.progress);
        addButton = findViewById(R.id.btn_add_custom);
        addButton.setOnClickListener(v -> addCustom());

        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        catalogueAdapter = new PredefinedAdapter(new ArrayList<>(), this::addPredefined);
        recycler.setAdapter(catalogueAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-filter every time we return: feeds deleted elsewhere reappear here,
        // feeds already added stay hidden.
        refreshCatalogue();
    }

    /** Shows only catalogue feeds whose URL isn't already a subscribed source. */
    private void refreshCatalogue() {
        Bg.run(() -> {
            Set<String> existing = new HashSet<>();
            for (Source s : repo.getSources()) {
                if (s.url != null) existing.add(s.url);
            }
            List<PredefinedFeeds.Entry> available = new ArrayList<>();
            for (PredefinedFeeds.Entry e : PredefinedFeeds.all()) {
                if (!existing.contains(e.url)) available.add(e);
            }
            return available;
        }, available -> {
            if (isFinishing() || isDestroyed() || available == null) return;
            catalogueAdapter.setEntries(available);
        });
    }

    private void addCustom() {
        if (!ensureCanAddSource()) return;
        String url = normalizeUrl(textOf(urlInput));
        final String name = textOf(nameInput);
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, R.string.url_required, Toast.LENGTH_SHORT).show();
            return;
        }
        final String finalUrl = url;
        if (repo.sourceUrlExists(finalUrl)) {
            Toast.makeText(this, R.string.source_exists, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Net.isOnline(this)) {
            Toast.makeText(this, R.string.offline_validate, Toast.LENGTH_LONG).show();
            return;
        }

        setBusy(true);
        Toast.makeText(this, R.string.validating, Toast.LENGTH_SHORT).show();
        // Validate by actually fetching/parsing the feed first.
        Bg.run(() -> new RssFetcher().fetch(finalUrl), result -> {
            setBusy(false);
            if (!result.ok || result.items.isEmpty()) {
                Toast.makeText(this, R.string.invalid_feed, Toast.LENGTH_LONG).show();
                return;
            }
            String finalName = !TextUtils.isEmpty(name) ? name
                    : (!TextUtils.isEmpty(result.feedTitle) ? result.feedTitle : finalUrl);
            commitSource(finalName, finalUrl, result);
        });
    }

    private void addPredefined(PredefinedFeeds.Entry entry) {
        if (!ensureCanAddSource()) return;
        if (repo.sourceUrlExists(entry.url)) {
            Toast.makeText(this, R.string.source_exists, Toast.LENGTH_SHORT).show();
            return;
        }
        long id = repo.addSource(entry.name, entry.url);
        if (id == RssRepository.ADD_LIMIT_REACHED) {
            showProDialog();
            return;
        }
        if (id < 0) {
            Toast.makeText(this, R.string.source_exists, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.source_added, Toast.LENGTH_SHORT).show();
        refreshNewSourceInBackground(getApplicationContext(), id, entry.name, entry.url);
        refreshCatalogue(); // hide the entry we just added
    }

    /**
     * Enforces the free-build source limit. In a Pro build (or under the limit)
     * returns true; otherwise shows the upgrade prompt and returns false.
     */
    private boolean ensureCanAddSource() {
        if (!AppConfig.FREE_VERSION) return true;
        if (repo.getSourceCount() < AppConfig.FREE_MAX_SOURCES) return true;
        showProDialog();
        return false;
    }

    private void showProDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.pro_dialog_title)
                .setMessage(getString(R.string.pro_dialog_message, AppConfig.FREE_MAX_SOURCES))
                .setPositiveButton(R.string.pro_get, (d, w) ->
                        Store.openPlayListing(this, AppConfig.PRO_PACKAGE))
                .setNegativeButton(R.string.pro_later, null)
                .show();
    }

    /** Persists a validated custom source and stores the already-fetched items. */
    private void commitSource(String name, String url, FetchResult result) {
        long id = repo.addSource(name, url);
        if (id == RssRepository.ADD_LIMIT_REACHED) {
            showProDialog();
            return;
        }
        if (id < 0) {
            Toast.makeText(this, R.string.source_exists, Toast.LENGTH_SHORT).show();
            return;
        }
        final long sourceId = id;
        final boolean storeContent = new de.bugfish.rssreaderadvanced.util.Prefs(this).isStoreLocal();
        Bg.run(() -> repo.insertItems(sourceId, result.items, storeContent), inserted -> {
            Toast.makeText(this, R.string.source_added, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private static void refreshNewSourceInBackground(Context appContext, long id, String name, String url) {
        final Source s = new Source(id, name, url, System.currentTimeMillis());
        Bg.run(() -> new RefreshEngine(appContext).refreshSource(s), ignored -> {
        });
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        addButton.setEnabled(!busy);
    }

    private static String normalizeUrl(String url) {
        if (TextUtils.isEmpty(url)) return url;
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://" + u;
        }
        return u;
    }

    private static String textOf(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }
}

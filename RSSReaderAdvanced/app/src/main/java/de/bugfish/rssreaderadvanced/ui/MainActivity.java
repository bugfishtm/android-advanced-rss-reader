package de.bugfish.rssreaderadvanced.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import de.bugfish.rssreaderadvanced.AppConfig;
import de.bugfish.rssreaderadvanced.R;
import de.bugfish.rssreaderadvanced.data.RssRepository;
import de.bugfish.rssreaderadvanced.data.Source;
import de.bugfish.rssreaderadvanced.net.RefreshEngine;
import de.bugfish.rssreaderadvanced.ui.fragments.AllItemsFragment;
import de.bugfish.rssreaderadvanced.ui.fragments.FavoritesFragment;
import de.bugfish.rssreaderadvanced.ui.fragments.MarksAllRead;
import de.bugfish.rssreaderadvanced.ui.fragments.Reloadable;
import de.bugfish.rssreaderadvanced.ui.fragments.SourcesFragment;
import de.bugfish.rssreaderadvanced.util.Bg;
import de.bugfish.rssreaderadvanced.util.Net;
import de.bugfish.rssreaderadvanced.util.Notifications;
import de.bugfish.rssreaderadvanced.util.Opml;
import de.bugfish.rssreaderadvanced.util.Prefs;
import de.bugfish.rssreaderadvanced.work.RefreshScheduler;

public class MainActivity extends AppCompatActivity {

    private final AllItemsFragment allItems = new AllItemsFragment();
    private final SourcesFragment sources = new SourcesFragment();
    private final FavoritesFragment favorites = new FavoritesFragment();
    private Fragment active;

    private MaterialToolbar toolbar;
    private BottomNavigationView nav;
    private RssRepository repo;
    private boolean refreshing;

    // OPML export: pick a destination file, then write the subscriptions.
    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/xml"),
                    uri -> {
                        if (uri != null) doExportOpml(uri);
                    });

    // OPML import: pick a file, then read + validate the subscriptions.
    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) doImportOpml(uri);
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        repo = new RssRepository(this);
        Notifications.ensureChannel(this);

        nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_all) {
                switchTo(allItems, R.string.nav_all);
            } else if (id == R.id.nav_sources) {
                switchTo(sources, R.string.nav_sources);
            } else if (id == R.id.nav_favorites) {
                switchTo(favorites, R.string.nav_favorites);
            }
            return true;
        });

        if (savedInstanceState == null) {
            switchTo(allItems, R.string.nav_all);
        }

        maybeShowFirstRun();
        RefreshScheduler.apply(this);
    }

    private void switchTo(Fragment fragment, int titleRes) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        active = fragment;
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(titleRes);
        }
        // Refresh the toolbar so "Mark all as read" shows only where it applies.
        invalidateOptionsMenu();
    }

    private void maybeShowFirstRun() {
        final Prefs prefs = new Prefs(this);
        if (prefs.isFirstRunDone()) return;

        String message = getString(R.string.first_run_message)
                + "\n\n• " + getString(R.string.mode_local_title) + " — "
                + getString(R.string.mode_local_desc)
                + "\n\n• " + getString(R.string.mode_online_title) + " — "
                + getString(R.string.mode_online_desc);

        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(R.string.first_run_title)
                .setMessage(message)
                .setPositiveButton(R.string.mode_local_title, (d, w) -> {
                    prefs.setStoreLocal(true);
                    prefs.setFirstRunDone(true);
                })
                .setNegativeButton(R.string.mode_online_title, (d, w) -> {
                    prefs.setStoreLocal(false);
                    prefs.setFirstRunDone(true);
                })
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_overflow, menu);
        setupSearch(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // "Mark all as read" only where a list is shown (All Items / Favorites).
        MenuItem markRead = menu.findItem(R.id.action_mark_read);
        if (markRead != null) {
            markRead.setVisible(active instanceof MarksAllRead);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void setupSearch(Menu menu) {
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView == null) return;
        searchView.setQueryHint(getString(R.string.search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                allItems.setSearchQuery(query);
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                allItems.setSearchQuery(newText);
                return true;
            }
        });
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(@NonNull MenuItem item) {
                // Search applies to the All Items list.
                nav.setSelectedItemId(R.id.nav_all);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(@NonNull MenuItem item) {
                allItems.setSearchQuery(null);
                return true;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            refreshAll();
            return true;
        } else if (id == R.id.action_mark_read) {
            if (active instanceof MarksAllRead) {
                ((MarksAllRead) active).markAllReadVisible();
            }
            return true;
        } else if (id == R.id.action_import_opml) {
            importLauncher.launch(new String[]{"*/*"});
            return true;
        } else if (id == R.id.action_export_opml) {
            exportLauncher.launch("feeds.opml");
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_privacy) {
            startActivity(new Intent(this, PrivacyActivity.class));
            return true;
        } else if (id == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doExportOpml(Uri uri) {
        Bg.run(() -> {
            List<Source> srcs = repo.getSources();
            String xml = Opml.export(srcs);
            if (xml == null) return -1;
            try (OutputStream os = getContentResolver().openOutputStream(uri, "w")) {
                if (os == null) return -1;
                os.write(xml.getBytes(StandardCharsets.UTF_8));
                return srcs.size();
            } catch (Exception e) {
                return -1;
            }
        }, count -> {
            if (isFinishing() || isDestroyed()) return;
            if (count < 0) {
                Toast.makeText(this, R.string.opml_export_failed, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, getString(R.string.opml_exported, count), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void doImportOpml(Uri uri) {
        Bg.run(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) return null;
                List<Opml.Entry> entries = Opml.parse(is);
                int added = 0;
                int limitSkipped = 0;
                for (Opml.Entry e : entries) {
                    if (AppConfig.FREE_VERSION
                            && repo.getSourceCount() >= AppConfig.FREE_MAX_SOURCES) {
                        limitSkipped++;
                        continue;
                    }
                    if (repo.sourceUrlExists(e.url)) continue;
                    if (repo.addSource(e.name, e.url) >= 0) added++;
                }
                return new int[]{added, limitSkipped};
            } catch (Exception e) {
                return null;
            }
        }, res -> {
            if (isFinishing() || isDestroyed()) return;
            if (res == null) {
                Toast.makeText(this, R.string.opml_import_failed, Toast.LENGTH_LONG).show();
                return;
            }
            int added = res[0];
            int limitSkipped = res[1];
            if (added == 0 && limitSkipped == 0) {
                Toast.makeText(this, R.string.opml_import_none, Toast.LENGTH_LONG).show();
            } else if (limitSkipped > 0) {
                Toast.makeText(this,
                        getString(R.string.opml_import_limited, added, AppConfig.FREE_MAX_SOURCES),
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, getString(R.string.opml_imported, added), Toast.LENGTH_LONG).show();
            }
            if (active instanceof Reloadable) {
                ((Reloadable) active).reload();
            }
            if (added > 0 && Net.isOnline(this)) {
                refreshAll();
            }
        });
    }

    /** Switch to the All Items tab pre-filtered to the given source. */
    public void openSourceItems(long sourceId) {
        new Prefs(this).setFilterSource(sourceId);
        nav.setSelectedItemId(R.id.nav_all);
    }

    /** Manual "refresh all sources" from the toolbar. */
    public void refreshAll() {
        if (refreshing) return;
        if (!Net.isOnline(this)) {
            Toast.makeText(this, R.string.offline_cant_refresh, Toast.LENGTH_LONG).show();
            return;
        }
        refreshing = true;
        Toast.makeText(this, R.string.refreshing, Toast.LENGTH_SHORT).show();
        final RefreshEngine engine = new RefreshEngine(this);
        Bg.run(engine::refreshAll, result -> {
            refreshing = false;
            if (isFinishing() || isDestroyed()) return;
            if (result == null) {
                Toast.makeText(this, R.string.refresh_failed, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this,
                        getString(R.string.refresh_done, result.newItems, result.sourcesOk, result.sourcesFailed),
                        Toast.LENGTH_LONG).show();
            }
            if (active instanceof Reloadable) {
                ((Reloadable) active).reload();
            }
        });
    }

}

package de.bugfish.rssreaderadvanced.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

import de.bugfish.rssreaderadvanced.R;
import de.bugfish.rssreaderadvanced.data.FeedItem;
import de.bugfish.rssreaderadvanced.data.RssRepository;
import de.bugfish.rssreaderadvanced.data.Source;
import de.bugfish.rssreaderadvanced.net.RefreshEngine;
import de.bugfish.rssreaderadvanced.ui.ItemDetailActivity;
import de.bugfish.rssreaderadvanced.ui.SwipeActionCallback;
import de.bugfish.rssreaderadvanced.ui.adapters.FeedItemAdapter;
import de.bugfish.rssreaderadvanced.util.Bg;
import de.bugfish.rssreaderadvanced.util.Net;
import de.bugfish.rssreaderadvanced.util.Prefs;

/** Shows all items across sources, with sorting and per-source filtering. */
public class AllItemsFragment extends Fragment
        implements Reloadable, MarksAllRead, FeedItemAdapter.Listener {

    private RssRepository repo;
    private Prefs prefs;
    private FeedItemAdapter adapter;
    private Spinner sortSpinner;
    private Spinner filterSpinner;
    private SwipeRefreshLayout swipe;
    private TextView empty;

    private final List<Long> filterSourceIds = new ArrayList<>();
    private boolean filterInitialized;
    private boolean sortInitialized;
    private String searchQuery;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_all_items, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repo = new RssRepository(requireContext());
        prefs = new Prefs(requireContext());

        RecyclerView recycler = view.findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.addItemDecoration(new DividerItemDecoration(requireContext(),
                DividerItemDecoration.VERTICAL));
        adapter = new FeedItemAdapter(this);
        recycler.setAdapter(adapter);
        attachSwipe(recycler);

        empty = view.findViewById(R.id.empty);
        swipe = view.findViewById(R.id.swipe);
        swipe.setOnRefreshListener(this::refreshFromNetwork);

        sortSpinner = view.findViewById(R.id.spinner_sort);
        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.sort_options, android.R.layout.simple_spinner_item);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);
        sortSpinner.setSelection(clampSort(prefs.getSortMode()));
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                if (!sortInitialized) {
                    sortInitialized = true;
                    return;
                }
                prefs.setSortMode(pos);
                reload();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        filterSpinner = view.findViewById(R.id.spinner_filter);
        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                if (!filterInitialized) {
                    filterInitialized = true;
                    return;
                }
                long sourceId = pos >= 0 && pos < filterSourceIds.size()
                        ? filterSourceIds.get(pos) : 0L;
                prefs.setFilterSource(sourceId);
                reload();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void attachSwipe(RecyclerView recycler) {
        // Swipe right = toggle favorite, swipe left = mark read.
        SwipeActionCallback callback = new SwipeActionCallback(requireContext(),
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT,
                R.drawable.ic_star, R.color.swipe_favorite,
                R.drawable.ic_check, R.color.swipe_read,
                (position, direction) -> {
                    final FeedItem it = adapter.getItemAt(position);
                    if (it == null) return;
                    // Restore the row first so it doesn't stay swiped away.
                    adapter.notifyItemChanged(position);
                    if (direction == ItemTouchHelper.RIGHT) {
                        Bg.run(() -> repo.toggleFavorite(it), nowFav -> {
                            if (isAdded()) adapter.notifyItemChanged(position);
                        });
                    } else {
                        it.read = true;
                        Bg.run(() -> {
                            repo.markRead(it.id, true);
                            return null;
                        }, ignored -> {
                            if (isAdded()) adapter.notifyItemChanged(position);
                        });
                    }
                });
        new ItemTouchHelper(callback).attachToRecyclerView(recycler);
    }

    private int clampSort(int mode) {
        return (mode < 0 || mode > 3) ? 0 : mode;
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuildFilterAndReload();
    }

    /** Rebuilds the source filter list (sources may have changed) then reloads items. */
    private void rebuildFilterAndReload() {
        Bg.run(() -> repo.getSources(), sources -> {
            if (!isAdded()) return;
            buildFilterSpinner(sources);
            reload();
        });
    }

    private void buildFilterSpinner(List<Source> sources) {
        List<String> labels = new ArrayList<>();
        filterSourceIds.clear();
        labels.add(getString(R.string.all_sources));
        filterSourceIds.add(0L);
        for (Source s : sources) {
            labels.add(s.name);
            filterSourceIds.add(s.id);
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        long current = prefs.getFilterSource();
        int index = filterSourceIds.indexOf(current);
        if (index < 0) {
            // The previously selected source was deleted: fall back to "All
            // sources" so the list isn't left showing nothing.
            index = 0;
            prefs.setFilterSource(0L);
        }

        filterInitialized = false; // suppress the programmatic selection callback
        filterSpinner.setAdapter(a);
        filterSpinner.setSelection(index);
    }

    /** Applies a search filter from the toolbar SearchView. */
    public void setSearchQuery(String query) {
        this.searchQuery = query;
        if (isAdded()) reload();
    }

    @Override
    public void markAllReadVisible() {
        // Only the items the user currently sees (same source filter + search).
        final long sourceId = prefs.getFilterSource();
        final String query = searchQuery;
        Bg.run(() -> repo.markAllRead(sourceId, query), rows -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(), R.string.marked_all_read, Toast.LENGTH_SHORT).show();
            reload();
        });
    }

    @Override
    public void reload() {
        final long sourceId = prefs.getFilterSource();
        final int sort = clampSort(prefs.getSortMode());
        final String query = searchQuery;
        Bg.run(() -> repo.getItems(sourceId, sort, query), items -> {
            if (!isAdded()) return;
            adapter.setItems(items);
            empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void refreshFromNetwork() {
        if (!Net.isOnline(requireContext())) {
            swipe.setRefreshing(false);
            Toast.makeText(requireContext(), R.string.offline_cant_refresh, Toast.LENGTH_LONG).show();
            return;
        }
        final RefreshEngine engine = new RefreshEngine(requireContext());
        Bg.run(engine::refreshAll, result -> {
            if (!isAdded()) return;
            swipe.setRefreshing(false);
            if (result == null) {
                Toast.makeText(requireContext(), R.string.refresh_failed, Toast.LENGTH_SHORT).show();
            }
            reload();
        });
    }

    // FeedItemAdapter.Listener -------------------------------------------------

    @Override
    public void onOpen(FeedItem item) {
        if (!item.read) {
            item.read = true;
            Bg.run(() -> {
                repo.markRead(item.id, true);
                return null;
            }, ignored -> {
            });
        }
        Intent intent = new Intent(requireContext(), ItemDetailActivity.class);
        intent.putExtra(ItemDetailActivity.EXTRA_ITEM_ID, item.id);
        startActivity(intent);
    }

    @Override
    public void onToggleFavorite(FeedItem item) {
        Bg.run(() -> repo.toggleFavorite(item), nowFav -> {
            if (!isAdded()) return;
            adapter.notifyDataSetChanged();
        });
    }
}

package de.bugfish.rssreaderadvanced.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.textfield.TextInputEditText;

import de.bugfish.rssreaderadvanced.R;
import de.bugfish.rssreaderadvanced.data.RssRepository;
import de.bugfish.rssreaderadvanced.data.Source;
import de.bugfish.rssreaderadvanced.net.RefreshEngine;
import de.bugfish.rssreaderadvanced.ui.AddSourceActivity;
import de.bugfish.rssreaderadvanced.ui.MainActivity;
import de.bugfish.rssreaderadvanced.ui.adapters.SourceAdapter;
import de.bugfish.rssreaderadvanced.util.Bg;
import de.bugfish.rssreaderadvanced.util.Net;

/** Lists subscribed sources with add / refresh / edit / delete actions. */
public class SourcesFragment extends Fragment implements Reloadable, SourceAdapter.Listener {

    private RssRepository repo;
    private SourceAdapter adapter;
    private SwipeRefreshLayout swipe;
    private TextView empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sources, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repo = new RssRepository(requireContext());

        RecyclerView recycler = view.findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.addItemDecoration(new DividerItemDecoration(requireContext(),
                DividerItemDecoration.VERTICAL));
        adapter = new SourceAdapter(this);
        recycler.setAdapter(adapter);

        empty = view.findViewById(R.id.empty);
        swipe = view.findViewById(R.id.swipe);
        swipe.setOnRefreshListener(this::refreshAll);

        view.findViewById(R.id.fab_add).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddSourceActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void reload() {
        Bg.run(() -> repo.getSources(), sources -> {
            if (!isAdded()) return;
            adapter.setSources(sources);
            empty.setVisibility(sources.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void refreshAll() {
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
                Toast.makeText(requireContext(), R.string.refresh_failed, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(requireContext(),
                        getString(R.string.refresh_done, result.newItems, result.sourcesOk, result.sourcesFailed),
                        Toast.LENGTH_LONG).show();
            }
            reload();
        });
    }

    // SourceAdapter.Listener ---------------------------------------------------

    @Override
    public void onOpenItems(Source source) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openSourceItems(source.id);
        }
    }

    @Override
    public void onRefresh(Source source) {
        if (!Net.isOnline(requireContext())) {
            Toast.makeText(requireContext(), R.string.offline_cant_refresh, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(requireContext(), R.string.refreshing, Toast.LENGTH_SHORT).show();
        final RefreshEngine engine = new RefreshEngine(requireContext());
        Bg.run(() -> engine.refreshSource(source), newCount -> {
            if (!isAdded()) return;
            if (newCount == null || newCount < 0) {
                Toast.makeText(requireContext(), R.string.source_refresh_failed, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(),
                        getString(R.string.source_refreshed, newCount), Toast.LENGTH_SHORT).show();
            }
            reload();
        });
    }

    @Override
    public void onMore(Source source, View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.inflate(R.menu.source_more);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_view_items) {
                onOpenItems(source);
                return true;
            } else if (id == R.id.action_edit) {
                showEditDialog(source);
                return true;
            } else if (id == R.id.action_delete) {
                confirmDelete(source);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showEditDialog(Source source) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_source, null);
        TextInputEditText nameInput = dialogView.findViewById(R.id.input_name);
        TextInputEditText urlInput = dialogView.findViewById(R.id.input_url);
        nameInput.setText(source.name);
        urlInput.setText(source.url);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.title_edit_source)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String name = textOf(nameInput);
                    String url = textOf(urlInput);
                    if (TextUtils.isEmpty(url)) {
                        Toast.makeText(requireContext(), R.string.url_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (TextUtils.isEmpty(name)) name = url;
                    final String fName = name;
                    final String fUrl = url;
                    Bg.run(() -> {
                        repo.updateSource(source.id, fName, fUrl);
                        return null;
                    }, ignored -> reload());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(Source source) {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.delete_source_q)
                .setPositiveButton(R.string.delete, (d, w) ->
                        Bg.run(() -> {
                            repo.deleteSource(source.id);
                            return null;
                        }, ignored -> reload()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static String textOf(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }
}

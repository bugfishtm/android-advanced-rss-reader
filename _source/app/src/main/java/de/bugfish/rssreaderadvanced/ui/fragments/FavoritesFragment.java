package de.bugfish.rssreaderadvanced.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import de.bugfish.rssreaderadvanced.R;
import de.bugfish.rssreaderadvanced.data.Favorite;
import de.bugfish.rssreaderadvanced.data.RssRepository;
import de.bugfish.rssreaderadvanced.ui.ItemDetailActivity;
import de.bugfish.rssreaderadvanced.ui.SwipeActionCallback;
import de.bugfish.rssreaderadvanced.ui.adapters.FavoriteAdapter;
import de.bugfish.rssreaderadvanced.util.Bg;

/** Shows saved favorites, kept independently of their source. */
public class FavoritesFragment extends Fragment
        implements Reloadable, MarksAllRead, FavoriteAdapter.Listener {

    private RssRepository repo;
    private FavoriteAdapter adapter;
    private TextView empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repo = new RssRepository(requireContext());

        RecyclerView recycler = view.findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.addItemDecoration(new DividerItemDecoration(requireContext(),
                DividerItemDecoration.VERTICAL));
        adapter = new FavoriteAdapter(this);
        recycler.setAdapter(adapter);

        empty = view.findViewById(R.id.empty);

        // Swipe either direction to remove a favorite.
        SwipeActionCallback callback = new SwipeActionCallback(requireContext(),
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT,
                R.drawable.ic_delete, R.color.swipe_delete,
                R.drawable.ic_delete, R.color.swipe_delete,
                (position, direction) -> {
                    final Favorite f = adapter.getItemAt(position);
                    if (f == null) return;
                    adapter.removeAt(position);
                    if (adapter.getItemCount() == 0) empty.setVisibility(View.VISIBLE);
                    Bg.run(() -> {
                        repo.removeFavorite(f.id);
                        return null;
                    }, ignored -> {
                    });
                });
        new ItemTouchHelper(callback).attachToRecyclerView(recycler);
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void reload() {
        Bg.run(() -> repo.getFavorites(), favs -> {
            if (!isAdded()) return;
            adapter.setItems(favs);
            empty.setVisibility(favs.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void markAllReadVisible() {
        // On the favorites tab, "mark all as read" applies to favorited articles.
        Bg.run(() -> repo.markFavoritesRead(), rows -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(), R.string.marked_all_read, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onOpen(Favorite favorite) {
        Intent intent = new Intent(requireContext(), ItemDetailActivity.class);
        intent.putExtra(ItemDetailActivity.EXTRA_FAVORITE_MODE, true);
        intent.putExtra(ItemDetailActivity.EXTRA_TITLE, favorite.title);
        intent.putExtra(ItemDetailActivity.EXTRA_CONTENT, favorite.content);
        intent.putExtra(ItemDetailActivity.EXTRA_DESC, favorite.description);
        intent.putExtra(ItemDetailActivity.EXTRA_LINK, favorite.link);
        intent.putExtra(ItemDetailActivity.EXTRA_AUTHOR, favorite.author);
        intent.putExtra(ItemDetailActivity.EXTRA_PUBDATE, favorite.pubDate);
        intent.putExtra(ItemDetailActivity.EXTRA_SOURCE, favorite.sourceName);
        intent.putExtra(ItemDetailActivity.EXTRA_GUID, favorite.guid);
        startActivity(intent);
    }

    @Override
    public void onRemove(Favorite favorite) {
        Bg.run(() -> {
            repo.removeFavorite(favorite.id);
            return null;
        }, ignored -> reload());
    }
}

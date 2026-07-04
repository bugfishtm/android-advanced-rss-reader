package de.bugfish.rssreaderadvanced.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import de.bugfish.rssreaderadvanced.R;
import de.bugfish.rssreaderadvanced.data.Favorite;
import de.bugfish.rssreaderadvanced.util.TextTools;

public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.VH> {

    public interface Listener {
        void onOpen(Favorite favorite);

        void onRemove(Favorite favorite);
    }

    private final List<Favorite> items = new ArrayList<>();
    private final Listener listener;

    public FavoriteAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setItems(List<Favorite> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public Favorite getItemAt(int position) {
        if (position < 0 || position >= items.size()) return null;
        return items.get(position);
    }

    public void removeAt(int position) {
        if (position < 0 || position >= items.size()) return;
        items.remove(position);
        notifyItemRemoved(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Favorite f = items.get(position);
        h.title.setText(f.title);

        String snippet = TextTools.snippet(
                f.description != null && !f.description.isEmpty() ? f.description : f.content, 160);
        if (snippet.isEmpty()) {
            h.snippet.setVisibility(View.GONE);
        } else {
            h.snippet.setVisibility(View.VISIBLE);
            h.snippet.setText(snippet);
        }

        StringBuilder meta = new StringBuilder();
        if (f.sourceName != null && !f.sourceName.isEmpty()) meta.append(f.sourceName);
        String time = TextTools.relativeOrDate(f.pubDate);
        if (!time.isEmpty()) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append(time);
        }
        h.meta.setText(meta.toString());

        h.remove.setOnClickListener(v -> listener.onRemove(f));
        h.itemView.setOnClickListener(v -> listener.onOpen(f));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView snippet;
        final TextView meta;
        final ImageButton remove;

        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.title);
            snippet = v.findViewById(R.id.snippet);
            meta = v.findViewById(R.id.meta);
            remove = v.findViewById(R.id.btn_remove);
        }
    }
}

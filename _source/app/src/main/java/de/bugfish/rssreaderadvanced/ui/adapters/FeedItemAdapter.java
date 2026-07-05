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
import de.bugfish.rssreaderadvanced.data.FeedItem;
import de.bugfish.rssreaderadvanced.util.TextTools;

public class FeedItemAdapter extends RecyclerView.Adapter<FeedItemAdapter.VH> {

    public interface Listener {
        void onOpen(FeedItem item);

        void onToggleFavorite(FeedItem item);
    }

    private final List<FeedItem> items = new ArrayList<>();
    private final Listener listener;

    public FeedItemAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setItems(List<FeedItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public FeedItem getItemAt(int position) {
        if (position < 0 || position >= items.size()) return null;
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_feed, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final FeedItem item = items.get(position);
        h.title.setText(item.title);
        h.title.setAlpha(item.read ? 0.6f : 1f);

        String snippet = TextTools.snippet(
                item.description != null && !item.description.isEmpty()
                        ? item.description : item.content, 160);
        if (snippet.isEmpty()) {
            h.snippet.setVisibility(View.GONE);
        } else {
            h.snippet.setVisibility(View.VISIBLE);
            h.snippet.setText(snippet);
        }

        StringBuilder meta = new StringBuilder();
        if (item.sourceName != null) meta.append(item.sourceName);
        String time = TextTools.relativeOrDate(item.pubDate);
        if (!time.isEmpty()) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append(time);
        }
        h.meta.setText(meta.toString());

        h.favorite.setActivated(item.favorite);
        h.favorite.setOnClickListener(v -> listener.onToggleFavorite(item));
        h.itemView.setOnClickListener(v -> listener.onOpen(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView snippet;
        final TextView meta;
        final ImageButton favorite;

        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.title);
            snippet = v.findViewById(R.id.snippet);
            meta = v.findViewById(R.id.meta);
            favorite = v.findViewById(R.id.btn_favorite);
        }
    }
}

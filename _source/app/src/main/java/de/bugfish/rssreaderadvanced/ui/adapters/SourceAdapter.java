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
import de.bugfish.rssreaderadvanced.data.Source;

public class SourceAdapter extends RecyclerView.Adapter<SourceAdapter.VH> {

    public interface Listener {
        void onOpenItems(Source source);

        void onRefresh(Source source);

        void onMore(Source source, View anchor);
    }

    private final List<Source> sources = new ArrayList<>();
    private final Listener listener;

    public SourceAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setSources(List<Source> list) {
        sources.clear();
        sources.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return sources.get(position).id;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_source, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Source s = sources.get(position);
        h.name.setText(s.name);
        h.sub.setText(h.itemView.getResources().getString(R.string.items_count, s.itemCount));
        h.itemView.setOnClickListener(v -> listener.onOpenItems(s));
        h.refresh.setOnClickListener(v -> listener.onRefresh(s));
        h.more.setOnClickListener(v -> listener.onMore(s, v));
    }

    @Override
    public int getItemCount() {
        return sources.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView sub;
        final ImageButton refresh;
        final ImageButton more;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.name);
            sub = v.findViewById(R.id.sub);
            refresh = v.findViewById(R.id.btn_refresh);
            more = v.findViewById(R.id.btn_more);
        }
    }
}

package de.bugfish.rssreaderadvanced.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import de.bugfish.rssreaderadvanced.R;
import de.bugfish.rssreaderadvanced.util.PredefinedFeeds;

public class PredefinedAdapter extends RecyclerView.Adapter<PredefinedAdapter.VH> {

    public interface Listener {
        void onPick(PredefinedFeeds.Entry entry);
    }

    private final List<PredefinedFeeds.Entry> entries = new ArrayList<>();
    private final Listener listener;

    public PredefinedAdapter(List<PredefinedFeeds.Entry> data, Listener listener) {
        this.entries.addAll(data);
        this.listener = listener;
    }

    public void setEntries(List<PredefinedFeeds.Entry> data) {
        entries.clear();
        entries.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_predefined, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final PredefinedFeeds.Entry e = entries.get(position);
        h.name.setText(e.name);
        h.category.setText(e.category);
        h.itemView.setOnClickListener(v -> listener.onPick(e));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView category;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.name);
            category = v.findViewById(R.id.category);
        }
    }
}

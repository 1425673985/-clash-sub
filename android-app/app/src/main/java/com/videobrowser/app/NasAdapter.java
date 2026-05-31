package com.videobrowser.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** NAS 目录列表：文件夹 / 视频条目，点击回调。 */
public class NasAdapter extends RecyclerView.Adapter<NasAdapter.NasHolder> {

    public interface OnItemClick {
        void onClick(JItem item);
    }

    private final List<JItem> items;
    private final OnItemClick listener;

    public NasAdapter(List<JItem> items, OnItemClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NasHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_nas, parent, false);
        return new NasHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull NasHolder holder, int position) {
        JItem item = items.get(position);
        holder.name.setText(item.name);
        holder.icon.setImageResource(item.isFolder
                ? R.drawable.ic_tab_nas : R.drawable.ic_play);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class NasHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;

        NasHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.nasItemIcon);
            name = itemView.findViewById(R.id.nasItemName);
        }
    }
}

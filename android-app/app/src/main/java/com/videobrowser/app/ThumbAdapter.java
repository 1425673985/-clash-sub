package com.videobrowser.app;

import android.graphics.Bitmap;
import android.util.LruCache;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 缩略图九宫格：异步取视频首帧/图片缩略图，内存缓存，点击跳转到对应媒体。 */
public class ThumbAdapter extends RecyclerView.Adapter<ThumbAdapter.ThumbHolder> {

    public interface OnThumbClick {
        void onClick(int position);
    }

    private final List<MediaItem> items;
    private final OnThumbClick listener;
    private final int spanCount;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> cache;

    public ThumbAdapter(List<MediaItem> items, int spanCount, OnThumbClick listener) {
        this.items = items;
        this.spanCount = spanCount;
        this.listener = listener;
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        cache = new LruCache<String, Bitmap>(maxKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    @NonNull
    @Override
    public ThumbHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_thumb, parent, false);
        int size = parent.getWidth() > 0 ? parent.getWidth() / spanCount : 360;
        v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size));
        return new ThumbHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ThumbHolder holder, int position) {
        MediaItem item = items.get(position);
        holder.index.setText(String.valueOf(position + 1));
        holder.playBadge.setVisibility(item.isVideo ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(holder.getBindingAdapterPosition());
        });

        final String key = item.uri.toString();
        holder.thumb.setTag(key);
        Bitmap cached = cache.get(key);
        if (cached != null) {
            holder.thumb.setImageBitmap(cached);
            return;
        }
        holder.thumb.setImageDrawable(null);
        executor.execute(() -> {
            Bitmap bmp = null;
            try {
                bmp = holder.thumb.getContext().getContentResolver()
                        .loadThumbnail(item.uri, new Size(360, 360), null);
            } catch (Throwable ignore) {
            }
            if (bmp == null) return;
            final Bitmap result = bmp;
            cache.put(key, result);
            holder.thumb.post(() -> {
                if (key.equals(holder.thumb.getTag())) {
                    holder.thumb.setImageBitmap(result);
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ThumbHolder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final ImageView playBadge;
        final TextView index;

        ThumbHolder(@NonNull View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.thumb);
            playBadge = itemView.findViewById(R.id.playBadge);
            index = itemView.findViewById(R.id.thumbIndex);
        }
    }
}

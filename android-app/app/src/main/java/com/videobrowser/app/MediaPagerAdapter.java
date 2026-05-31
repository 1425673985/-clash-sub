package com.videobrowser.app;

import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 竖向媒体适配器：每页一个视频(VideoView)或图片(ImageView)。
 * 点击=视频播放/暂停，左右滑=视频快进/快退 3 秒，上下滑由 ViewPager2 切换。
 */
public class MediaPagerAdapter extends RecyclerView.Adapter<MediaPagerAdapter.MediaHolder> {

    private static final int SEEK_MS = 3000;

    private final List<MediaItem> items;
    private final Set<MediaHolder> attached = new HashSet<>();
    private int activePosition = 0;

    public MediaPagerAdapter(List<MediaItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public MediaHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media, parent, false);
        return new MediaHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaHolder holder, int position) {
        MediaItem item = items.get(position);
        holder.bind(item, position, items.size(), position == activePosition);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public void onViewAttachedToWindow(@NonNull MediaHolder holder) {
        attached.add(holder);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull MediaHolder holder) {
        attached.remove(holder);
        holder.stop();
    }

    @Override
    public void onViewRecycled(@NonNull MediaHolder holder) {
        holder.stop();
    }

    /** 切到某页：播放该页视频，停止其它页。 */
    public void setActivePosition(int position) {
        activePosition = position;
        for (MediaHolder h : attached) {
            if (h.getBindingAdapterPosition() == position) {
                h.play();
            } else {
                h.stop();
            }
        }
    }

    /** 仅暂停当前页（退到后台时用）。 */
    public void pauseActive(int position) {
        for (MediaHolder h : attached) {
            if (h.getBindingAdapterPosition() == position) {
                h.pauseOnly();
            }
        }
    }

    /** 停止所有页（返回选择界面时用）。 */
    public void stopAll() {
        for (MediaHolder h : attached) {
            h.stop();
        }
    }

    class MediaHolder extends RecyclerView.ViewHolder {
        final VideoView videoView;
        final ImageView imageView;
        final TextView name;
        final ImageView playIcon;
        boolean isVideo = false;
        boolean prepared = false;
        boolean shouldPlay = false;

        MediaHolder(@NonNull View itemView) {
            super(itemView);
            videoView = itemView.findViewById(R.id.videoView);
            imageView = itemView.findViewById(R.id.imageView);
            name = itemView.findViewById(R.id.name);
            playIcon = itemView.findViewById(R.id.playIcon);

            final GestureDetector detector = new GestureDetector(itemView.getContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(MotionEvent e) {
                            return true;
                        }

                        @Override
                        public boolean onSingleTapConfirmed(MotionEvent e) {
                            if (isVideo) toggle();
                            return true;
                        }

                        @Override
                        public boolean onFling(MotionEvent e1, MotionEvent e2,
                                               float velocityX, float velocityY) {
                            if (!isVideo || e1 == null || e2 == null) return false;
                            float dx = e2.getX() - e1.getX();
                            float dy = e2.getY() - e1.getY();
                            if (Math.abs(dx) > Math.abs(dy)
                                    && Math.abs(dx) > 120
                                    && Math.abs(velocityX) > Math.abs(velocityY)) {
                                seek(dx < 0 ? SEEK_MS : -SEEK_MS);
                                return true;
                            }
                            return false;
                        }
                    });

            itemView.setOnTouchListener((v, event) -> {
                detector.onTouchEvent(event);
                // 返回 true 保持接收手势；纵向拖动由 ViewPager2 父层拦截换页
                return true;
            });
        }

        void bind(MediaItem item, int position, int total, boolean active) {
            this.isVideo = item.isVideo;
            this.prepared = false;
            this.shouldPlay = active && item.isVideo;
            name.setText((position + 1) + " / " + total
                    + (item.name != null ? "  ·  " + item.name : ""));
            playIcon.setVisibility(View.GONE);

            if (item.isVideo) {
                imageView.setVisibility(View.GONE);
                imageView.setImageDrawable(null);
                videoView.setVisibility(View.VISIBLE);
                videoView.setVideoURI(item.uri);
                videoView.setOnPreparedListener(mp -> {
                    mp.setLooping(true);
                    prepared = true;
                    if (shouldPlay) {
                        videoView.start();
                        playIcon.setVisibility(View.GONE);
                    } else {
                        try { videoView.seekTo(1); } catch (Exception ignore) { }
                    }
                });
                videoView.setOnErrorListener((mp, what, extra) -> {
                    playIcon.setVisibility(View.GONE);
                    return true; // 无法解码时不弹系统错误框
                });
            } else {
                videoView.setVisibility(View.GONE);
                imageView.setVisibility(View.VISIBLE);
                try {
                    imageView.setImageURI(item.uri);
                } catch (Exception ignore) {
                    imageView.setImageDrawable(null);
                }
            }
        }

        void play() {
            if (!isVideo) return;
            shouldPlay = true;
            playIcon.setVisibility(View.GONE);
            if (prepared && !videoView.isPlaying()) {
                videoView.start();
            }
        }

        void stop() {
            shouldPlay = false;
            if (isVideo) {
                if (videoView.isPlaying()) videoView.pause();
                if (prepared) {
                    try { videoView.seekTo(0); } catch (Exception ignore) { }
                }
            }
            playIcon.setVisibility(View.GONE);
        }

        void pauseOnly() {
            shouldPlay = false;
            if (isVideo && videoView.isPlaying()) {
                videoView.pause();
                playIcon.setVisibility(View.VISIBLE);
            }
        }

        void toggle() {
            if (!isVideo) return;
            if (videoView.isPlaying()) {
                videoView.pause();
                playIcon.setVisibility(View.VISIBLE);
                shouldPlay = false;
            } else {
                videoView.start();
                playIcon.setVisibility(View.GONE);
                shouldPlay = true;
            }
        }

        void seek(int deltaMs) {
            if (!isVideo || !prepared) return;
            int duration = videoView.getDuration();
            int target = videoView.getCurrentPosition() + deltaMs;
            if (target < 0) target = 0;
            if (duration > 0 && target > duration - 200) {
                target = Math.max(0, duration - 200);
            }
            videoView.seekTo(target);
            if (shouldPlay && !videoView.isPlaying()) {
                videoView.start();
            }
            Toast.makeText(itemView.getContext(),
                    deltaMs < 0 ? "⏪ 后退 3 秒" : "快进 3 秒 ⏩",
                    Toast.LENGTH_SHORT).show();
        }
    }
}

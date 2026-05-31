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
 * 竖向短视频适配器：每页一个 VideoView。
 * 点击=播放/暂停，左右滑=快进/快退 3 秒，上下滑由 ViewPager2 切换。
 */
public class VideoPagerAdapter extends RecyclerView.Adapter<VideoPagerAdapter.VideoHolder> {

    private static final int SEEK_MS = 3000;

    private final List<VideoItem> items;
    private final Set<VideoHolder> attached = new HashSet<>();
    private int activePosition = 0;

    public VideoPagerAdapter(List<VideoItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public VideoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        return new VideoHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoHolder holder, int position) {
        VideoItem item = items.get(position);
        holder.badge.setText((position + 1) + " / " + items.size());
        holder.name.setText(item.name);
        holder.prepared = false;
        holder.shouldPlay = (position == activePosition);
        holder.playIcon.setVisibility(View.GONE);

        holder.videoView.setVideoURI(item.uri);
        holder.videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            holder.prepared = true;
            if (holder.shouldPlay) {
                holder.videoView.start();
                holder.playIcon.setVisibility(View.GONE);
            } else {
                // 离屏页面定格首帧作为预览
                try { holder.videoView.seekTo(1); } catch (Exception ignore) { }
            }
        });
        holder.videoView.setOnErrorListener((mp, what, extra) -> {
            holder.playIcon.setVisibility(View.GONE);
            return true; // 某些格式无法解码时不弹系统错误框
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public void onViewAttachedToWindow(@NonNull VideoHolder holder) {
        attached.add(holder);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull VideoHolder holder) {
        attached.remove(holder);
        holder.stop();
    }

    @Override
    public void onViewRecycled(@NonNull VideoHolder holder) {
        holder.stop();
    }

    /** 切换到某一页：播放该页，停止其它页。 */
    public void setActivePosition(int position) {
        activePosition = position;
        for (VideoHolder h : attached) {
            if (h.getBindingAdapterPosition() == position) {
                h.play();
            } else {
                h.stop();
            }
        }
    }

    /** 仅暂停当前页（用于退到后台）。 */
    public void pauseActive(int position) {
        for (VideoHolder h : attached) {
            if (h.getBindingAdapterPosition() == position) {
                h.pauseOnly();
            }
        }
    }

    class VideoHolder extends RecyclerView.ViewHolder {
        final VideoView videoView;
        final TextView badge;
        final TextView name;
        final ImageView playIcon;
        boolean prepared = false;
        boolean shouldPlay = false;

        VideoHolder(@NonNull View itemView) {
            super(itemView);
            videoView = itemView.findViewById(R.id.videoView);
            badge = itemView.findViewById(R.id.badge);
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
                            toggle();
                            return true;
                        }

                        @Override
                        public boolean onFling(MotionEvent e1, MotionEvent e2,
                                               float velocityX, float velocityY) {
                            if (e1 == null || e2 == null) return false;
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

        void play() {
            shouldPlay = true;
            playIcon.setVisibility(View.GONE);
            if (prepared && !videoView.isPlaying()) {
                videoView.start();
            }
        }

        void stop() {
            shouldPlay = false;
            if (videoView.isPlaying()) {
                videoView.pause();
            }
            if (prepared) {
                try { videoView.seekTo(0); } catch (Exception ignore) { }
            }
            playIcon.setVisibility(View.VISIBLE);
        }

        void pauseOnly() {
            shouldPlay = false;
            if (videoView.isPlaying()) {
                videoView.pause();
            }
            playIcon.setVisibility(View.VISIBLE);
        }

        void toggle() {
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
            if (!prepared) return;
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

package com.videobrowser.app;

import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
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
 * 点击=播放/暂停，左滑快退5s/右滑快进5s，长按2倍速，底部时间进度条，上下滑切换。
 */
public class MediaPagerAdapter extends RecyclerView.Adapter<MediaPagerAdapter.MediaHolder> {

    private static final int SEEK_MS = 5000;

    private final List<MediaItem> items;
    private final Set<MediaHolder> attached = new HashSet<>();
    private int activePosition = 0;
    private float defaultSpeed = 1f;

    public MediaPagerAdapter(List<MediaItem> items) {
        this.items = items;
    }

    public void setDefaultSpeed(float speed) {
        this.defaultSpeed = speed;
        for (MediaHolder h : attached) {
            if (h.getBindingAdapterPosition() == activePosition && !h.longPress2x) {
                h.applySpeed(speed);
            }
        }
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

    public void pauseActive(int position) {
        for (MediaHolder h : attached) {
            if (h.getBindingAdapterPosition() == position) {
                h.pauseOnly();
            }
        }
    }

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
        final ProgressBar progress;
        final TextView speedBadge;
        boolean isVideo = false;
        boolean prepared = false;
        boolean shouldPlay = false;
        boolean longPress2x = false;
        MediaPlayer mediaPlayer;

        private final Runnable progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (isVideo && prepared && videoView.isPlaying()) {
                    int d = videoView.getDuration();
                    if (d > 0) {
                        progress.setProgress((int) (1000L * videoView.getCurrentPosition() / d));
                    }
                }
                itemView.postDelayed(this, 400);
            }
        };

        MediaHolder(@NonNull View itemView) {
            super(itemView);
            videoView = itemView.findViewById(R.id.videoView);
            imageView = itemView.findViewById(R.id.imageView);
            name = itemView.findViewById(R.id.name);
            playIcon = itemView.findViewById(R.id.playIcon);
            progress = itemView.findViewById(R.id.progress);
            speedBadge = itemView.findViewById(R.id.speedBadge);

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
                        public void onLongPress(MotionEvent e) {
                            if (!isVideo || !prepared) return;
                            longPress2x = true;
                            applySpeed(2f);
                            speedBadge.setVisibility(View.VISIBLE);
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
                                // 左滑快退，右滑快进
                                seek(dx < 0 ? -SEEK_MS : SEEK_MS);
                                return true;
                            }
                            return false;
                        }
                    });

            itemView.setOnTouchListener((v, event) -> {
                detector.onTouchEvent(event);
                int a = event.getActionMasked();
                if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && longPress2x) {
                    longPress2x = false;
                    applySpeed(defaultSpeed);
                    speedBadge.setVisibility(View.GONE);
                }
                return true;
            });
        }

        void bind(MediaItem item, int position, int total, boolean active) {
            this.isVideo = item.isVideo;
            this.prepared = false;
            this.shouldPlay = active && item.isVideo;
            this.longPress2x = false;
            this.mediaPlayer = null;
            name.setText((position + 1) + " / " + total
                    + (item.name != null ? "  ·  " + item.name : ""));
            playIcon.setVisibility(View.GONE);
            speedBadge.setVisibility(View.GONE);
            progress.setProgress(0);

            if (item.isVideo) {
                progress.setVisibility(View.VISIBLE);
                imageView.setVisibility(View.GONE);
                imageView.setImageDrawable(null);
                videoView.setVisibility(View.VISIBLE);
                videoView.setVideoURI(item.uri);
                videoView.setOnPreparedListener(mp -> {
                    mp.setLooping(true);
                    mediaPlayer = mp;
                    prepared = true;
                    if (shouldPlay) {
                        videoView.start();
                        applySpeed(defaultSpeed);
                        startProgress();
                        playIcon.setVisibility(View.GONE);
                    } else {
                        try { videoView.seekTo(1); } catch (Exception ignore) { }
                    }
                });
                videoView.setOnErrorListener((mp, what, extra) -> {
                    playIcon.setVisibility(View.GONE);
                    return true;
                });
            } else {
                progress.setVisibility(View.GONE);
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
                applySpeed(longPress2x ? 2f : defaultSpeed);
                startProgress();
            }
        }

        void stop() {
            shouldPlay = false;
            longPress2x = false;
            speedBadge.setVisibility(View.GONE);
            stopProgress();
            if (isVideo) {
                if (videoView.isPlaying()) videoView.pause();
                if (prepared) {
                    try { videoView.seekTo(0); } catch (Exception ignore) { }
                }
                progress.setProgress(0);
            }
            playIcon.setVisibility(View.GONE);
        }

        void pauseOnly() {
            shouldPlay = false;
            stopProgress();
            if (isVideo && videoView.isPlaying()) {
                videoView.pause();
                playIcon.setVisibility(View.VISIBLE);
            }
        }

        void toggle() {
            if (!isVideo) return;
            if (videoView.isPlaying()) {
                videoView.pause();
                stopProgress();
                playIcon.setVisibility(View.VISIBLE);
                shouldPlay = false;
            } else {
                videoView.start();
                applySpeed(defaultSpeed);
                startProgress();
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
                    deltaMs < 0 ? "⏪ 后退 5 秒" : "快进 5 秒 ⏩",
                    Toast.LENGTH_SHORT).show();
        }

        void applySpeed(float speed) {
            if (mediaPlayer == null || !prepared) return;
            try {
                if (videoView.isPlaying()) {
                    mediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(speed));
                }
            } catch (Exception ignore) {
            }
        }

        void startProgress() {
            itemView.removeCallbacks(progressRunnable);
            itemView.postDelayed(progressRunnable, 400);
        }

        void stopProgress() {
            itemView.removeCallbacks(progressRunnable);
        }
    }
}

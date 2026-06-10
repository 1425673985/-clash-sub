package com.videobrowser.app;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/** 原生 ExoPlayer 播放 Jellyfin 强制转码 HLS：续播、倍速、长按2倍、左右滑±5秒。 */
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_START_MS = "start_ms";
    public static final String EXTRA_SPEED = "speed";
    public static final String EXTRA_SERVER = "server";
    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_ITEM_ID = "item_id";

    private static final long SEEK_MS = 5000;

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView status;
    private TextView speedBadge;
    private long startMs;
    private float defaultSpeed = 1f;
    private boolean longPress2x = false;
    private String serverUrl, token, deviceId, itemId;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);
        enterImmersive();
        playerView = findViewById(R.id.playerView);
        status = findViewById(R.id.playerStatus);
        speedBadge = findViewById(R.id.playerSpeedBadge);

        String url = getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        startMs = getIntent().getLongExtra(EXTRA_START_MS, 0);
        defaultSpeed = getIntent().getFloatExtra(EXTRA_SPEED, 1f);
        serverUrl = getIntent().getStringExtra(EXTRA_SERVER);
        token = getIntent().getStringExtra(EXTRA_TOKEN);
        deviceId = getIntent().getStringExtra(EXTRA_DEVICE);
        itemId = getIntent().getStringExtra(EXTRA_ITEM_ID);

        if (url == null || url.isEmpty()) {
            finish();
            return;
        }
        ((TextView) findViewById(R.id.playerTitle)).setText(title == null ? "" : title);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                status.setVisibility(View.VISIBLE);
                status.setText("播放失败：" + error.getMessage());
            }
        });

        MediaItem item = new MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build();
        player.setMediaItem(item);
        if (startMs > 1000) {
            player.seekTo(startMs);
        }
        player.prepare();
        player.setPlaybackSpeed(defaultSpeed);
        player.setPlayWhenReady(true);

        setupGestures();
    }

    private void enterImmersive() {
        try {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(
                    getWindow(), getWindow().getDecorView());
            c.hide(WindowInsetsCompat.Type.systemBars());
            c.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } catch (Exception ignore) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGestures() {
        final GestureDetector detector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public void onLongPress(MotionEvent e) {
                        if (player == null) return;
                        longPress2x = true;
                        player.setPlaybackSpeed(2f);
                        speedBadge.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                           float velocityX, float velocityY) {
                        if (player == null || e1 == null || e2 == null) return false;
                        float dx = e2.getX() - e1.getX();
                        float dy = e2.getY() - e1.getY();
                        if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 120
                                && Math.abs(velocityX) > Math.abs(velocityY)) {
                            seekBy(dx < 0 ? -SEEK_MS : SEEK_MS);
                            return true;
                        }
                        return false;
                    }
                });

        playerView.setOnTouchListener((v, event) -> {
            detector.onTouchEvent(event);
            int a = event.getActionMasked();
            if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && longPress2x) {
                longPress2x = false;
                if (player != null) player.setPlaybackSpeed(defaultSpeed);
                speedBadge.setVisibility(View.GONE);
            }
            return false; // 不消费，PlayerView 仍可显示/隐藏控制条
        });
    }

    private void seekBy(long deltaMs) {
        if (player == null) return;
        long pos = player.getCurrentPosition() + deltaMs;
        if (pos < 0) pos = 0;
        long dur = player.getDuration();
        if (dur > 0 && pos > dur) pos = dur;
        player.seekTo(pos);
        Toast.makeText(this, deltaMs < 0 ? "⏪ 后退 5 秒" : "快进 5 秒 ⏩",
                Toast.LENGTH_SHORT).show();
    }

    private void reportProgress() {
        if (player == null || TextUtils.isEmpty(serverUrl) || TextUtils.isEmpty(itemId)) return;
        final long pos = player.getCurrentPosition();
        final String server = serverUrl, tk = token, dev = deviceId, id = itemId;
        new Thread(() -> {
            JellyfinClient c = new JellyfinClient(server, dev == null ? "" : dev);
            c.setTokenForReport(tk);
            c.reportStopped(id, pos);
        }).start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        reportProgress();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}

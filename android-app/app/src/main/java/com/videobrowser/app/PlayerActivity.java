package com.videobrowser.app;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/** 原生 ExoPlayer 播放 Jellyfin 强制转码 HLS 流，支持续播与进度上报。 */
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_START_MS = "start_ms";
    public static final String EXTRA_SERVER = "server";
    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_ITEM_ID = "item_id";

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView status;
    private long startMs;
    private String serverUrl, token, deviceId, itemId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.playerView);
        status = findViewById(R.id.playerStatus);

        String url = getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        startMs = getIntent().getLongExtra(EXTRA_START_MS, 0);
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
        player.setPlayWhenReady(true);
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

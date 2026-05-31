package com.videobrowser.app;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private View homeView;
    private View feedContainer;
    private ViewPager2 pager;
    private TextView badge;
    private TextView emptyView;
    private MediaPagerAdapter adapter;
    private final List<MediaItem> items = new ArrayList<>();
    private ActivityResultLauncher<PickVisualMediaRequest> pickLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        homeView = findViewById(R.id.home);
        feedContainer = findViewById(R.id.feedContainer);
        pager = findViewById(R.id.pager);
        badge = findViewById(R.id.badge);
        emptyView = findViewById(R.id.empty);

        pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        adapter = new MediaPagerAdapter(items);
        pager.setAdapter(adapter);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                adapter.setActivePosition(position);
                updateBadge(position);
            }
        });

        pickLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(),
                this::onPicked);

        findViewById(R.id.btnVideo).setOnClickListener(
                v -> launchPick(PickVisualMedia.VideoOnly.INSTANCE));
        findViewById(R.id.btnImage).setOnClickListener(
                v -> launchPick(PickVisualMedia.ImageOnly.INSTANCE));
        findViewById(R.id.btnBoth).setOnClickListener(
                v -> launchPick(PickVisualMedia.ImageAndVideo.INSTANCE));
        findViewById(R.id.btnReset).setOnClickListener(v -> showHome());

        // 返回键：在浏览界面时回到选择界面
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (feedContainer.getVisibility() == View.VISIBLE) {
                    showHome();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        showHome();
    }

    private void launchPick(PickVisualMedia.VisualMediaType type) {
        pickLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(type)
                .build());
    }

    private void onPicked(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return; // 用户取消，留在选择界面
        }
        items.clear();
        for (Uri uri : uris) {
            String type = getContentResolver().getType(uri);
            boolean isVideo = type != null && type.startsWith("video/");
            items.add(new MediaItem(uri, isVideo, queryName(uri)));
        }
        adapter.notifyDataSetChanged();
        showFeed();
    }

    private String queryName(Uri uri) {
        try (Cursor c = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private void showHome() {
        adapter.stopAll();
        feedContainer.setVisibility(View.GONE);
        homeView.setVisibility(View.VISIBLE);
    }

    private void showFeed() {
        homeView.setVisibility(View.GONE);
        feedContainer.setVisibility(View.VISIBLE);
        if (items.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            pager.setVisibility(View.GONE);
            badge.setText("");
            return;
        }
        emptyView.setVisibility(View.GONE);
        pager.setVisibility(View.VISIBLE);
        pager.setCurrentItem(0, false);
        updateBadge(0);
        pager.post(() -> adapter.setActivePosition(0));
    }

    private void updateBadge(int position) {
        if (items.isEmpty()) {
            badge.setText("");
        } else {
            badge.setText((position + 1) + " / " + items.size());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (feedContainer.getVisibility() == View.VISIBLE && !items.isEmpty()) {
            adapter.pauseActive(pager.getCurrentItem());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (feedContainer.getVisibility() == View.VISIBLE && !items.isEmpty()) {
            adapter.setActivePosition(pager.getCurrentItem());
        }
    }
}

package com.videobrowser.app;

import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

/** 图片/媒体全屏滑动浏览器，复用 MediaPagerAdapter。 */
public class ViewerActivity extends AppCompatActivity {

    public static final String EXTRA_URIS = "uris";
    public static final String EXTRA_NAMES = "names";
    public static final String EXTRA_VIDEO = "video_flags";
    public static final String EXTRA_START = "start";

    private final List<MediaItem> items = new ArrayList<>();
    private ViewPager2 pager;
    private TextView badge;
    private MediaPagerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewer);
        pager = findViewById(R.id.viewerPager);
        badge = findViewById(R.id.viewerBadge);

        ArrayList<String> uris = getIntent().getStringArrayListExtra(EXTRA_URIS);
        ArrayList<String> names = getIntent().getStringArrayListExtra(EXTRA_NAMES);
        boolean[] videoFlags = getIntent().getBooleanArrayExtra(EXTRA_VIDEO);
        int start = getIntent().getIntExtra(EXTRA_START, 0);
        if (uris == null || uris.isEmpty()) {
            finish();
            return;
        }
        for (int i = 0; i < uris.size(); i++) {
            boolean isVideo = videoFlags != null && i < videoFlags.length && videoFlags[i];
            String n = (names != null && i < names.size()) ? names.get(i) : "";
            items.add(new MediaItem(Uri.parse(uris.get(i)), isVideo, n));
        }

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

        if (start < 0 || start >= items.size()) start = 0;
        pager.setCurrentItem(start, false);
        updateBadge(start);
        final int s = start;
        pager.post(() -> adapter.setActivePosition(s));
    }

    private void updateBadge(int position) {
        badge.setText((position + 1) + " / " + items.size());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!items.isEmpty()) adapter.pauseActive(pager.getCurrentItem());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!items.isEmpty()) adapter.setActivePosition(pager.getCurrentItem());
    }
}

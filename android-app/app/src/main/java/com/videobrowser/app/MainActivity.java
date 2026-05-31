package com.videobrowser.app;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 pager;
    private TextView emptyView;
    private VideoPagerAdapter adapter;
    private final List<VideoItem> items = new ArrayList<>();
    private ActivityResultLauncher<String> permLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        pager = findViewById(R.id.pager);
        emptyView = findViewById(R.id.empty);

        pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        adapter = new VideoPagerAdapter(items);
        pager.setAdapter(adapter);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                adapter.setActivePosition(position);
            }
        });

        permLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        loadVideos();
                    } else {
                        showEmpty(getString(R.string.need_permission));
                    }
                });

        requestPermissionAndLoad();
    }

    private String requiredPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private void requestPermissionAndLoad() {
        String perm = requiredPermission();
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            loadVideos();
        } else {
            permLauncher.launch(perm);
        }
    }

    private void loadVideos() {
        items.clear();
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME
        };
        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContentResolver().query(collection, projection, null, null, sortOrder)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String name = cursor.getString(nameCol);
                    Uri uri = ContentUris.withAppendedId(collection, id);
                    items.add(new VideoItem(uri, name != null ? name : "未命名视频"));
                }
            }
        } catch (Exception e) {
            showEmpty("读取视频失败：" + e.getMessage());
            return;
        }

        adapter.notifyDataSetChanged();

        if (items.isEmpty()) {
            showEmpty(getString(R.string.no_video));
        } else {
            emptyView.setVisibility(View.GONE);
            pager.setVisibility(View.VISIBLE);
            pager.post(() -> adapter.setActivePosition(pager.getCurrentItem()));
        }
    }

    private void showEmpty(String msg) {
        emptyView.setText(msg);
        emptyView.setVisibility(View.VISIBLE);
        pager.setVisibility(View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!items.isEmpty()) {
            adapter.pauseActive(pager.getCurrentItem());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!items.isEmpty()) {
            adapter.setActivePosition(pager.getCurrentItem());
        }
    }
}

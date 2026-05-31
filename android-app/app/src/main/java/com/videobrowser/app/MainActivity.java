package com.videobrowser.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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
    private ActivityResultLauncher<Intent> pickLauncher;
    private ActivityResultLauncher<Intent> folderLauncher;
    private ActivityResultLauncher<String[]> permLauncher;
    private boolean pendingIncludeImages;

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
                new ActivityResultContracts.StartActivityForResult(),
                this::onPickResult);
        folderLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onFolderResult);
        permLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                granted -> scanDevice(pendingIncludeImages));

        findViewById(R.id.btnScanVideo).setOnClickListener(v -> requestScan(false));
        findViewById(R.id.btnScanAll).setOnClickListener(v -> requestScan(true));
        findViewById(R.id.btnFolder).setOnClickListener(v -> launchFolder());
        findViewById(R.id.btnManual).setOnClickListener(v -> launchPick("*/*"));
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

    private void launchPick(String mime) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        if ("*/*".equals(mime)) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "image/*"});
        }
        try {
            pickLauncher.launch(intent);
        } catch (Exception e) {
            // 个别机型无文档界面时退回 GET_CONTENT
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType(mime);
            fallback.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            pickLauncher.launch(fallback);
        }
    }

    private void onPickResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
            return; // 用户取消，留在选择界面
        }
        Intent data = result.getData();
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null) uris.add(u);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        onPicked(uris);
    }

    private void onPicked(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return;
        }
        items.clear();
        for (Uri uri : uris) {
            String type = getContentResolver().getType(uri);
            boolean isVideo = type != null && type.startsWith("video/");
            if (type == null) {
                // 类型未知时按扩展名兜底判断
                String s = uri.toString().toLowerCase();
                isVideo = s.matches(".*\\.(mp4|m4v|mov|webm|mkv|avi|3gp|ts)(\\?.*)?$");
            }
            items.add(new MediaItem(uri, isVideo, queryName(uri)));
        }
        adapter.notifyDataSetChanged();
        showFeed();
    }

    // ---------- 自动扫描全机媒体 ----------

    private String[] neededPermissions(boolean includeImages) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return includeImages
                    ? new String[]{Manifest.permission.READ_MEDIA_VIDEO,
                                   Manifest.permission.READ_MEDIA_IMAGES}
                    : new String[]{Manifest.permission.READ_MEDIA_VIDEO};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private boolean hasAll(String[] perms) {
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestScan(boolean includeImages) {
        pendingIncludeImages = includeImages;
        String[] perms = neededPermissions(includeImages);
        if (hasAll(perms)) {
            scanDevice(includeImages);
        } else {
            permLauncher.launch(perms);
        }
    }

    private void scanDevice(final boolean includeImages) {
        Toast.makeText(this, R.string.scanning, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final List<Scanned> all = new ArrayList<>();
            queryStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, all);
            if (includeImages) {
                queryStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, all);
            }
            all.sort((a, b) -> Long.compare(b.date, a.date)); // 按添加时间倒序
            final List<MediaItem> result = new ArrayList<>();
            for (Scanned s : all) result.add(s.item);
            runOnUiThread(() -> {
                items.clear();
                items.addAll(result);
                adapter.notifyDataSetChanged();
                if (result.isEmpty()) emptyView.setText(R.string.nothing_found);
                showFeed();
            });
        }).start();
    }

    private void queryStore(Uri collection, boolean isVideo, List<Scanned> out) {
        String[] proj = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED
        };
        try (Cursor c = getContentResolver().query(collection, proj, null, null, null)) {
            if (c == null) return;
            int idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
            while (c.moveToNext()) {
                long id = c.getLong(idCol);
                String name = c.getString(nameCol);
                long date = c.getLong(dateCol);
                Uri uri = ContentUris.withAppendedId(collection, id);
                out.add(new Scanned(new MediaItem(uri, isVideo, name), date));
            }
        } catch (Exception ignore) {
        }
    }

    private static class Scanned {
        final MediaItem item;
        final long date;
        Scanned(MediaItem item, long date) {
            this.item = item;
            this.date = date;
        }
    }

    private void launchFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            folderLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "此设备不支持选择文件夹", Toast.LENGTH_SHORT).show();
        }
    }

    private void onFolderResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
            return;
        }
        final Uri treeUri = result.getData().getData();
        if (treeUri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignore) {
        }
        Toast.makeText(this, R.string.loading_folder, Toast.LENGTH_SHORT).show();
        // 目录可能很大，放后台线程遍历，避免卡界面
        new Thread(() -> {
            final List<MediaItem> found = new ArrayList<>();
            try {
                walkTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri), found, 0);
            } catch (Exception ignore) {
            }
            found.sort((a, b) -> {
                String na = a.name == null ? "" : a.name;
                String nb = b.name == null ? "" : b.name;
                return na.compareToIgnoreCase(nb);
            });
            runOnUiThread(() -> {
                items.clear();
                items.addAll(found);
                adapter.notifyDataSetChanged();
                showFeed();
            });
        }).start();
    }

    /** 递归遍历目录，收集视频和图片（限制递归深度，避免极端深目录）。 */
    private void walkTree(Uri treeUri, String parentDocId, List<MediaItem> out, int depth) {
        if (depth > 12) return;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId);
        String[] proj = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        };
        try (Cursor c = getContentResolver().query(childrenUri, proj, null, null, null)) {
            if (c == null) return;
            while (c.moveToNext()) {
                String docId = c.getString(0);
                String mime = c.getString(1);
                String name = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    walkTree(treeUri, docId, out, depth + 1);
                } else if (mime != null
                        && (mime.startsWith("video/") || mime.startsWith("image/"))) {
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                    out.add(new MediaItem(docUri, mime.startsWith("video/"), name));
                }
            }
        } catch (Exception ignore) {
        }
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

package com.videobrowser.app;

import android.Manifest;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "settings";
    private static final String KEY_JELLYFIN = "jellyfin_url";
    private static final String KEY_JF_USER = "jellyfin_user";
    private static final String KEY_JF_PASS = "jellyfin_pass";
    private static final String KEY_DEVICE = "device_id";
    private static final String KEY_COLUMNS = "span_count";
    private static final String KEY_QUALITY = "jellyfin_quality";
    private static final String KEY_SPEED = "play_speed";
    private static final String KEY_SEL = "home_selection";
    private static final String KEY_SEL_POS = "home_position";
    private static final String KEY_SEL_FILTER = "home_filter";
    private static final String KEY_HISTORY = "watch_history";

    // 首页
    private View sectionHome, sectionNas, sectionDownload, sectionProfile;
    private View homeView, feedContainer;
    private ViewPager2 pager;
    private RecyclerView grid;
    private GridLayoutManager gridLm;
    private TextView badge, emptyView;
    private MediaPagerAdapter adapter;
    private ThumbAdapter thumbAdapter;
    private boolean gridVisible = false;
    private int filterMode = 0; // 0 全部, 1 视频, 2 图片
    private int spanCount = 3;
    private final List<MediaItem> allItems = new ArrayList<>(); // 选中的全部
    private final List<MediaItem> items = new ArrayList<>();     // 过滤后(适配器数据源)

    // NAS (Jellyfin 原生)
    private RecyclerView nasList;
    private NasAdapter nasAdapter;
    private final List<JItem> nasItems = new ArrayList<>();
    private final List<String> nasStackIds = new ArrayList<>();
    private final List<String> nasStackTitles = new ArrayList<>();
    private JellyfinClient jelly;
    private View nasHint;
    private TextView nasTitle, nasHintText, nasUp;
    private boolean nasLoadedOnce = false;
    private String deviceId;
    private int quality = 0; // 0 高(8M) 1 中(4M) 2 低(2M)
    private float playSpeed = 1f;

    // 视频 / 图片 页
    private View sectionVideo, sectionImage;
    private RecyclerView videoGrid, imageGrid;
    private ThumbAdapter videoAdapter, imageAdapter;
    private TextView videoEmpty, imageEmpty;
    private final List<MediaItem> videoAll = new ArrayList<>();
    private final List<MediaItem> videoItems = new ArrayList<>();
    private final List<MediaItem> imageAll = new ArrayList<>();
    private final List<MediaItem> imageItems = new ArrayList<>();
    private int videoCat = 0; // 0 全部 1 短 2 长 3 最近
    private boolean videoScanned = false, imageScanned = false;
    private int pendingScanTarget = 0; // 0 首页 1 视频页 2 图片页

    // 下载 / 设置
    private EditText dlUrl, jellyfinUrl, jellyfinUser, jellyfinPass;
    private TextView versionText, loginStatus;

    private int currentTab = 0;
    private SharedPreferences prefs;

    private ActivityResultLauncher<Intent> pickLauncher;
    private ActivityResultLauncher<Intent> folderLauncher;
    private ActivityResultLauncher<String[]> permLauncher;
    private boolean pendingIncludeImages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        spanCount = prefs.getInt(KEY_COLUMNS, 3);

        deviceId = prefs.getString(KEY_DEVICE, null);
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString().replace("-", "");
            prefs.edit().putString(KEY_DEVICE, deviceId).apply();
        }
        jelly = new JellyfinClient(prefs.getString(KEY_JELLYFIN, ""), deviceId);
        quality = prefs.getInt(KEY_QUALITY, 0);
        jelly.setBitrate(bitrateFor(quality));
        playSpeed = prefs.getFloat(KEY_SPEED, 1f);

        sectionHome = findViewById(R.id.section_home);
        sectionVideo = findViewById(R.id.section_video);
        sectionImage = findViewById(R.id.section_image);
        sectionNas = findViewById(R.id.section_nas);
        sectionDownload = findViewById(R.id.section_download);
        sectionProfile = findViewById(R.id.section_profile);

        homeView = findViewById(R.id.home);
        feedContainer = findViewById(R.id.feedContainer);
        pager = findViewById(R.id.pager);
        grid = findViewById(R.id.grid);
        badge = findViewById(R.id.badge);
        emptyView = findViewById(R.id.empty);

        pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        adapter = new MediaPagerAdapter(items);
        adapter.setDefaultSpeed(playSpeed);
        pager.setAdapter(adapter);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                adapter.setActivePosition(position);
                updateBadge(position);
                savePosition();
            }
        });

        gridLm = new GridLayoutManager(this, spanCount);
        grid.setLayoutManager(gridLm);
        thumbAdapter = new ThumbAdapter(items, spanCount, position -> {
            showGrid(false);
            pager.setCurrentItem(position, false);
            updateBadge(position);
        });
        grid.setAdapter(thumbAdapter);

        setupActivityLaunchers();
        setupHomeButtons();
        setupFeedBar();
        setupBottomNav();
        setupVideoImageTabs();
        setupNas();
        setupDownload();
        setupProfile();

        // 返回键
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (nasCanGoUp()) {
                    nasGoUp();
                    return;
                }
                if (currentTab != 0) {
                    switchTab(0);
                    return;
                }
                if (feedContainer.getVisibility() == View.VISIBLE) {
                    if (gridVisible) showGrid(false);
                    else showHome();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        switchTab(0);
        if (!restoreSelection()) {
            showHome();
        }
    }

    // ---------------- 选择器 ----------------

    private void setupActivityLaunchers() {
        pickLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::onPickResult);
        folderLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::onFolderResult);
        permLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                granted -> dispatchScan());
    }

    private void dispatchScan() {
        switch (pendingScanTarget) {
            case 1: scanVideoTab(); break;
            case 2: scanImageTab(); break;
            default: scanDevice(pendingIncludeImages); break;
        }
    }

    private void setupHomeButtons() {
        findViewById(R.id.btnScanVideo).setOnClickListener(v -> requestScan(false));
        findViewById(R.id.btnScanAll).setOnClickListener(v -> requestScan(true));
        findViewById(R.id.btnFolder).setOnClickListener(v -> launchFolder());
        findViewById(R.id.btnManual).setOnClickListener(v -> launchPick("*/*"));
    }

    private void setupFeedBar() {
        findViewById(R.id.btnReset).setOnClickListener(v -> {
            clearSelection();
            showHome();
        });
        findViewById(R.id.btnGrid).setOnClickListener(v -> showGrid(!gridVisible));
        findViewById(R.id.filterAll).setOnClickListener(v -> applyFilter(0));
        findViewById(R.id.filterVideo).setOnClickListener(v -> applyFilter(1));
        findViewById(R.id.filterImage).setOnClickListener(v -> applyFilter(2));
        updateFilterStyle();
    }

    // ---------------- 底部导航 ----------------
    // 0 首页 1 视频 2 图片 3 NAS 4 下载 5 我的

    private void setupBottomNav() {
        findViewById(R.id.navHome).setOnClickListener(v -> switchTab(0));
        findViewById(R.id.navVideo).setOnClickListener(v -> switchTab(1));
        findViewById(R.id.navImage).setOnClickListener(v -> switchTab(2));
        findViewById(R.id.navNas).setOnClickListener(v -> switchTab(3));
        findViewById(R.id.navDownload).setOnClickListener(v -> switchTab(4));
        findViewById(R.id.navMe).setOnClickListener(v -> switchTab(5));
    }

    private void switchTab(int tab) {
        // 离开首页时暂停播放
        if (currentTab == 0 && tab != 0
                && feedContainer.getVisibility() == View.VISIBLE && !gridVisible) {
            adapter.pauseActive(pager.getCurrentItem());
        }
        currentTab = tab;
        sectionHome.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        sectionVideo.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        sectionImage.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        sectionNas.setVisibility(tab == 3 ? View.VISIBLE : View.GONE);
        sectionDownload.setVisibility(tab == 4 ? View.VISIBLE : View.GONE);
        sectionProfile.setVisibility(tab == 5 ? View.VISIBLE : View.GONE);
        updateNavStyle(tab);

        if (tab == 0 && feedContainer.getVisibility() == View.VISIBLE
                && !gridVisible && !items.isEmpty()) {
            adapter.setActivePosition(pager.getCurrentItem());
        }
        if (tab == 1) loadVideoTab();
        if (tab == 2) loadImageTab();
        if (tab == 3) loadNas();
        if (tab == 5) fillProfile();
    }

    private void updateNavStyle(int tab) {
        int[] icons = {R.id.navHomeIcon, R.id.navVideoIcon, R.id.navImageIcon,
                R.id.navNasIcon, R.id.navDownloadIcon, R.id.navMeIcon};
        int[] texts = {R.id.navHomeText, R.id.navVideoText, R.id.navImageText,
                R.id.navNasText, R.id.navDownloadText, R.id.navMeText};
        int accent = 0xFFFE2C55;
        int dim = 0xFF8A8A99;
        for (int i = 0; i < icons.length; i++) {
            boolean sel = (i == tab);
            ImageView ic = findViewById(icons[i]);
            ic.setColorFilter(sel ? accent : dim);
            ic.setAlpha(1f);
            ((TextView) findViewById(texts[i])).setTextColor(sel ? accent : dim);
        }
    }

    // ---------------- 自动扫描 ----------------

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
        pendingScanTarget = 0;
        pendingIncludeImages = includeImages;
        String[] perms = neededPermissions(includeImages);
        if (hasAll(perms)) scanDevice(includeImages);
        else permLauncher.launch(perms);
    }

    private String[] permFor(boolean image) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{image ? Manifest.permission.READ_MEDIA_IMAGES
                    : Manifest.permission.READ_MEDIA_VIDEO};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private void scanDevice(final boolean includeImages) {
        Toast.makeText(this, R.string.scanning, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final List<Scanned> tmp = new ArrayList<>();
            queryStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, tmp);
            if (includeImages) {
                queryStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, tmp);
            }
            tmp.sort((a, b) -> Long.compare(b.date, a.date));
            final List<MediaItem> result = new ArrayList<>();
            for (Scanned s : tmp) result.add(s.item);
            runOnUiThread(() -> setMedia(result));
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
        Scanned(MediaItem item, long date) { this.item = item; this.date = date; }
    }

    // ---------------- 文件夹 ----------------

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
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
        final Uri treeUri = result.getData().getData();
        if (treeUri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignore) {
        }
        Toast.makeText(this, R.string.loading_folder, Toast.LENGTH_SHORT).show();
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
            runOnUiThread(() -> setMedia(found));
        }).start();
    }

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

    // ---------------- 手动选择 ----------------

    private void launchPick(String mime) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if ("*/*".equals(mime)) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "image/*"});
        }
        try {
            pickLauncher.launch(intent);
        } catch (Exception e) {
            Intent fb = new Intent(Intent.ACTION_GET_CONTENT);
            fb.addCategory(Intent.CATEGORY_OPENABLE);
            fb.setType(mime);
            fb.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            pickLauncher.launch(fb);
        }
    }

    private void onPickResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
        Intent data = result.getData();
        List<MediaItem> picked = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null) {
                    persist(u);
                    picked.add(toMediaItem(u));
                }
            }
        } else if (data.getData() != null) {
            persist(data.getData());
            picked.add(toMediaItem(data.getData()));
        }
        setMedia(picked);
    }

    private void persist(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignore) {
        }
    }

    private MediaItem toMediaItem(Uri uri) {
        String type = getContentResolver().getType(uri);
        boolean isVideo = type != null && type.startsWith("video/");
        if (type == null) {
            String s = uri.toString().toLowerCase();
            isVideo = s.matches(".*\\.(mp4|m4v|mov|webm|mkv|avi|3gp|ts)(\\?.*)?$");
        }
        return new MediaItem(uri, isVideo, queryName(uri));
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

    // ---------------- 数据 / 过滤 ----------------

    private void setMedia(List<MediaItem> list) {
        allItems.clear();
        allItems.addAll(list);
        filterMode = 0;
        updateFilterStyle();
        applyFilterInternal();
        if (allItems.isEmpty()) emptyView.setText(R.string.nothing_found);
        saveSelection();
        showFeed();
    }

    private void applyFilter(int mode) {
        filterMode = mode;
        updateFilterStyle();
        applyFilterInternal();
        if (!items.isEmpty()) {
            pager.setCurrentItem(0, false);
            updateBadge(0);
            if (!gridVisible) {
                pager.post(() -> adapter.setActivePosition(0));
            }
        } else {
            updateBadge(0);
        }
        savePosition();
    }

    private void applyFilterInternal() {
        items.clear();
        for (MediaItem m : allItems) {
            if (filterMode == 0
                    || (filterMode == 1 && m.isVideo)
                    || (filterMode == 2 && !m.isVideo)) {
                items.add(m);
            }
        }
        adapter.notifyDataSetChanged();
        thumbAdapter.notifyDataSetChanged();
    }

    private void updateFilterStyle() {
        TextView a = findViewById(R.id.filterAll);
        TextView v = findViewById(R.id.filterVideo);
        TextView i = findViewById(R.id.filterImage);
        a.setTextColor(filterMode == 0 ? 0xFFFFFFFF : 0xFF9A9AAA);
        a.setTypeface(null, filterMode == 0 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        v.setTextColor(filterMode == 1 ? 0xFFFFFFFF : 0xFF9A9AAA);
        v.setTypeface(null, filterMode == 1 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        i.setTextColor(filterMode == 2 ? 0xFFFFFFFF : 0xFF9A9AAA);
        i.setTypeface(null, filterMode == 2 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    // ---------------- 首页界面切换 ----------------

    private void showHome() {
        adapter.stopAll();
        gridVisible = false;
        grid.setVisibility(View.GONE);
        feedContainer.setVisibility(View.GONE);
        homeView.setVisibility(View.VISIBLE);
    }

    private void showFeed() {
        showFeedAt(0);
    }

    private void showFeedAt(final int pos) {
        homeView.setVisibility(View.GONE);
        feedContainer.setVisibility(View.VISIBLE);
        gridVisible = false;
        grid.setVisibility(View.GONE);
        if (items.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            pager.setVisibility(View.GONE);
            badge.setText("");
            return;
        }
        emptyView.setVisibility(View.GONE);
        pager.setVisibility(View.VISIBLE);
        final int p = (pos < 0 || pos >= items.size()) ? 0 : pos;
        pager.setCurrentItem(p, false);
        updateBadge(p);
        pager.post(() -> adapter.setActivePosition(p));
    }

    private void showGrid(boolean show) {
        if (items.isEmpty()) return;
        gridVisible = show;
        if (show) {
            adapter.pauseActive(pager.getCurrentItem());
            thumbAdapter.notifyDataSetChanged();
            grid.scrollToPosition(pager.getCurrentItem());
            grid.setVisibility(View.VISIBLE);
            pager.setVisibility(View.GONE);
        } else {
            grid.setVisibility(View.GONE);
            pager.setVisibility(View.VISIBLE);
            adapter.setActivePosition(pager.getCurrentItem());
        }
    }

    private void updateBadge(int position) {
        badge.setText(items.isEmpty() ? "" : (position + 1) + " / " + items.size());
    }

    // ---------------- 首页选择持久化 ----------------

    private void saveSelection() {
        try {
            JSONArray arr = new JSONArray();
            for (MediaItem m : allItems) {
                JSONObject o = new JSONObject();
                o.put("u", m.uri.toString());
                o.put("v", m.isVideo);
                o.put("n", m.name == null ? "" : m.name);
                arr.put(o);
            }
            prefs.edit()
                    .putString(KEY_SEL, arr.toString())
                    .putInt(KEY_SEL_FILTER, filterMode)
                    .putInt(KEY_SEL_POS, 0)
                    .apply();
        } catch (Exception ignore) {
        }
    }

    private void savePosition() {
        if (feedContainer.getVisibility() != View.VISIBLE || items.isEmpty()) return;
        prefs.edit()
                .putInt(KEY_SEL_POS, pager.getCurrentItem())
                .putInt(KEY_SEL_FILTER, filterMode)
                .apply();
    }

    private void clearSelection() {
        prefs.edit().remove(KEY_SEL).remove(KEY_SEL_POS).remove(KEY_SEL_FILTER).apply();
        allItems.clear();
    }

    private boolean restoreSelection() {
        String saved = prefs.getString(KEY_SEL, "");
        if (TextUtils.isEmpty(saved)) return false;
        try {
            JSONArray arr = new JSONArray(saved);
            allItems.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String u = o.optString("u");
                if (TextUtils.isEmpty(u)) continue;
                allItems.add(new MediaItem(Uri.parse(u),
                        o.optBoolean("v", false),
                        o.optString("n", "")));
            }
            if (allItems.isEmpty()) return false;
            filterMode = prefs.getInt(KEY_SEL_FILTER, 0);
            updateFilterStyle();
            applyFilterInternal();
            if (items.isEmpty()) return false;
            showFeedAt(prefs.getInt(KEY_SEL_POS, 0));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------- 视频 / 图片 页 ----------------

    private void setupVideoImageTabs() {
        videoGrid = findViewById(R.id.videoGrid);
        imageGrid = findViewById(R.id.imageGrid);
        videoEmpty = findViewById(R.id.videoEmpty);
        imageEmpty = findViewById(R.id.imageEmpty);

        videoGrid.setLayoutManager(new GridLayoutManager(this, 3));
        imageGrid.setLayoutManager(new GridLayoutManager(this, 4));
        videoAdapter = new ThumbAdapter(videoItems, 3, pos -> {
            if (pos >= 0 && pos < videoItems.size()) openLocalVideo(videoItems.get(pos));
        });
        imageAdapter = new ThumbAdapter(imageItems, 4, this::openImageViewer);
        videoGrid.setAdapter(videoAdapter);
        imageGrid.setAdapter(imageAdapter);

        findViewById(R.id.vAll).setOnClickListener(v -> applyVideoCat(0));
        findViewById(R.id.vShort).setOnClickListener(v -> applyVideoCat(1));
        findViewById(R.id.vLong).setOnClickListener(v -> applyVideoCat(2));
        findViewById(R.id.vRecent).setOnClickListener(v -> applyVideoCat(3));
        videoEmpty.setOnClickListener(v -> requestVideoScan());
        imageEmpty.setOnClickListener(v -> requestImageScan());
        updateVideoChips();
    }

    private void loadVideoTab() {
        if (videoScanned || videoCat == 3) applyVideoCat(videoCat);
        else requestVideoScan();
    }

    private void loadImageTab() {
        if (imageScanned) {
            imageEmpty.setVisibility(imageItems.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            requestImageScan();
        }
    }

    private void requestVideoScan() {
        pendingScanTarget = 1;
        String[] p = permFor(false);
        if (hasAll(p)) scanVideoTab();
        else permLauncher.launch(p);
    }

    private void requestImageScan() {
        pendingScanTarget = 2;
        String[] p = permFor(true);
        if (hasAll(p)) scanImageTab();
        else permLauncher.launch(p);
    }

    private void scanVideoTab() {
        new Thread(() -> {
            final List<MediaItem> list = new ArrayList<>();
            String[] proj = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DURATION};
            // 排除 App 缓存目录与过小的垃圾文件
            String sel = "(" + MediaStore.Video.Media.RELATIVE_PATH + " IS NULL OR "
                    + MediaStore.Video.Media.RELATIVE_PATH + " NOT LIKE ?) AND "
                    + MediaStore.Video.Media.SIZE + ">=?";
            String[] args = {"%Android/%", "51200"};
            try (Cursor c = getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, sel, args,
                    MediaStore.Video.Media.DATE_ADDED + " DESC")) {
                if (c != null) {
                    int idc = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                    int nc = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                    int dc = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                    while (c.moveToNext()) {
                        long id = c.getLong(idc);
                        Uri uri = ContentUris.withAppendedId(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                        list.add(new MediaItem(uri, true, c.getString(nc), c.getLong(dc)));
                    }
                }
            } catch (Exception ignore) {
            }
            runOnUiThread(() -> {
                videoScanned = true;
                videoAll.clear();
                videoAll.addAll(list);
                applyVideoCat(videoCat);
            });
        }).start();
    }

    private void scanImageTab() {
        new Thread(() -> {
            final List<MediaItem> list = new ArrayList<>();
            String[] proj = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME};
            // 只要够大的真实照片/截图：排除小图标，排除 App 缓存目录
            String sel = "((" + MediaStore.Images.Media.WIDTH + ">=? AND "
                    + MediaStore.Images.Media.HEIGHT + ">=?) OR "
                    + MediaStore.Images.Media.SIZE + ">=?) AND ("
                    + MediaStore.Images.Media.RELATIVE_PATH + " IS NULL OR "
                    + MediaStore.Images.Media.RELATIVE_PATH + " NOT LIKE ?)";
            String[] args = {"300", "300", "153600", "%Android/%"};
            try (Cursor c = getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, sel, args,
                    MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                if (c != null) {
                    int idc = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                    int nc = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                    while (c.moveToNext()) {
                        long id = c.getLong(idc);
                        Uri uri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                        list.add(new MediaItem(uri, false, c.getString(nc)));
                    }
                }
            } catch (Exception ignore) {
            }
            runOnUiThread(() -> {
                imageScanned = true;
                imageAll.clear();
                imageAll.addAll(list);
                imageItems.clear();
                imageItems.addAll(list);
                imageAdapter.notifyDataSetChanged();
                imageEmpty.setVisibility(imageItems.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    private void applyVideoCat(int cat) {
        videoCat = cat;
        updateVideoChips();
        videoItems.clear();
        if (cat == 3) {
            videoItems.addAll(loadHistoryVideos());
        } else {
            for (MediaItem m : videoAll) {
                long d = m.durationMs;
                if (cat == 0 || (cat == 1 && d > 0 && d <= 60000) || (cat == 2 && d > 60000)) {
                    videoItems.add(m);
                }
            }
        }
        if (videoAdapter != null) videoAdapter.notifyDataSetChanged();
        boolean needScan = (cat != 3) && !videoScanned;
        videoEmpty.setVisibility((videoItems.isEmpty() && needScan) ? View.VISIBLE : View.GONE);
        if (videoItems.isEmpty() && !needScan) {
            videoEmpty.setVisibility(View.GONE);
        }
    }

    private void updateVideoChips() {
        int[] ids = {R.id.vAll, R.id.vShort, R.id.vLong, R.id.vRecent};
        for (int i = 0; i < ids.length; i++) {
            ((TextView) findViewById(ids[i]))
                    .setTextColor(videoCat == i ? 0xFFFFFFFF : 0xFF9A9AAA);
        }
    }

    private void openLocalVideo(MediaItem m) {
        addHistory(m);
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_URL, m.uri.toString());
        i.putExtra(PlayerActivity.EXTRA_IS_HLS, false);
        i.putExtra(PlayerActivity.EXTRA_TITLE, m.name == null ? "" : m.name);
        i.putExtra(PlayerActivity.EXTRA_SPEED, playSpeed);
        startActivity(i);
    }

    private void openImageViewer(int pos) {
        if (imageItems.isEmpty()) return;
        ArrayList<String> uris = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        boolean[] flags = new boolean[imageItems.size()];
        for (int i = 0; i < imageItems.size(); i++) {
            MediaItem m = imageItems.get(i);
            uris.add(m.uri.toString());
            names.add(m.name == null ? "" : m.name);
            flags[i] = false;
        }
        Intent i = new Intent(this, ViewerActivity.class);
        i.putStringArrayListExtra(ViewerActivity.EXTRA_URIS, uris);
        i.putStringArrayListExtra(ViewerActivity.EXTRA_NAMES, names);
        i.putExtra(ViewerActivity.EXTRA_VIDEO, flags);
        i.putExtra(ViewerActivity.EXTRA_START, pos);
        startActivity(i);
    }

    private void addHistory(MediaItem m) {
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            JSONArray out = new JSONArray();
            JSONObject head = new JSONObject();
            head.put("u", m.uri.toString());
            head.put("n", m.name == null ? "" : m.name);
            out.put(head);
            for (int i = 0; i < arr.length() && out.length() < 100; i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!m.uri.toString().equals(o.optString("u"))) out.put(o);
            }
            prefs.edit().putString(KEY_HISTORY, out.toString()).apply();
        } catch (Exception ignore) {
        }
    }

    private List<MediaItem> loadHistoryVideos() {
        List<MediaItem> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String u = o.optString("u");
                if (!TextUtils.isEmpty(u)) {
                    out.add(new MediaItem(Uri.parse(u), true, o.optString("n")));
                }
            }
        } catch (Exception ignore) {
        }
        return out;
    }

    // ---------------- NAS (Jellyfin 原生) ----------------

    private void setupNas() {
        nasList = findViewById(R.id.nasList);
        nasHint = findViewById(R.id.nasHint);
        nasTitle = findViewById(R.id.nasTitle);
        nasHintText = findViewById(R.id.nasHintText);
        nasUp = findViewById(R.id.nasUp);
        nasList.setLayoutManager(new LinearLayoutManager(this));
        nasAdapter = new NasAdapter(nasItems, jelly, this::onNasItemClick);
        nasList.setAdapter(nasAdapter);
        findViewById(R.id.nasReload).setOnClickListener(v -> reloadNas());
        findViewById(R.id.nasGoSettings).setOnClickListener(v -> switchTab(5));
        nasUp.setOnClickListener(v -> nasGoUp());
    }

    /** 进入 NAS Tab 时调用。 */
    private void loadNas() {
        String url = prefs.getString(KEY_JELLYFIN, "").trim();
        String user = prefs.getString(KEY_JF_USER, "").trim();
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(user)) {
            showNasHint(getString(R.string.nas_no_server));
            return;
        }
        if (!jelly.isLoggedIn()) {
            // 自动用已保存的账号登录，成功后再列目录
            loginJellyfin(false);
            return;
        }
        if (!nasLoadedOnce) {
            openNasLevel(null, getString(R.string.nas_title), true);
        } else {
            nasHint.setVisibility(View.GONE);
            nasList.setVisibility(View.VISIBLE);
        }
    }

    private void reloadNas() {
        if (!jelly.isLoggedIn()) {
            loginJellyfin(false);
            return;
        }
        String id = nasStackIds.isEmpty() ? null : nasStackIds.get(nasStackIds.size() - 1);
        String title = nasStackTitles.isEmpty()
                ? getString(R.string.nas_title) : nasStackTitles.get(nasStackTitles.size() - 1);
        loadNasItems(id, title);
    }

    private void showNasHint(String text) {
        nasHintText.setText(text);
        nasHint.setVisibility(View.VISIBLE);
        nasList.setVisibility(View.GONE);
    }

    /** 进入新一层目录（push 到导航栈）。 */
    private void openNasLevel(String parentId, String title, boolean resetStack) {
        if (resetStack) {
            nasStackIds.clear();
            nasStackTitles.clear();
        }
        nasStackIds.add(parentId == null ? "" : parentId);
        nasStackTitles.add(title);
        loadNasItems(parentId, title);
    }

    private void nasGoUp() {
        if (nasStackIds.size() <= 1) return;
        nasStackIds.remove(nasStackIds.size() - 1);
        nasStackTitles.remove(nasStackTitles.size() - 1);
        String id = nasStackIds.get(nasStackIds.size() - 1);
        String title = nasStackTitles.get(nasStackTitles.size() - 1);
        loadNasItems(id.isEmpty() ? null : id, title);
    }

    private boolean nasCanGoUp() {
        return currentTab == 3 && nasStackIds.size() > 1;
    }

    private void loadNasItems(final String parentId, final String title) {
        nasHint.setVisibility(View.GONE);
        nasList.setVisibility(View.VISIBLE);
        nasTitle.setText(title);
        nasUp.setVisibility(nasStackIds.size() > 1 ? View.VISIBLE : View.GONE);
        new Thread(() -> {
            List<JItem> result = new ArrayList<>();
            String error = null;
            try {
                result = jelly.listItems(parentId);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final List<JItem> fr = result;
            final String fe = error;
            runOnUiThread(() -> {
                if (fe != null) {
                    showNasHint("读取失败：" + fe);
                    return;
                }
                nasLoadedOnce = true;
                nasItems.clear();
                nasItems.addAll(fr);
                nasAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void onNasItemClick(JItem item) {
        if (item.isFolder) {
            openNasLevel(item.id, item.name, false);
        } else {
            String url = jelly.buildStreamUrl(item.id);
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra(PlayerActivity.EXTRA_URL, url);
            intent.putExtra(PlayerActivity.EXTRA_TITLE, item.name);
            intent.putExtra(PlayerActivity.EXTRA_START_MS, item.resumeMs);
            intent.putExtra(PlayerActivity.EXTRA_SPEED, playSpeed);
            intent.putExtra(PlayerActivity.EXTRA_SERVER, jelly.getServerUrl());
            intent.putExtra(PlayerActivity.EXTRA_TOKEN, jelly.getToken());
            intent.putExtra(PlayerActivity.EXTRA_DEVICE, deviceId);
            intent.putExtra(PlayerActivity.EXTRA_ITEM_ID, item.id);
            startActivity(intent);
        }
    }

    private void loginJellyfin(final boolean fromButton) {
        final String url = prefs.getString(KEY_JELLYFIN, "").trim();
        final String user = prefs.getString(KEY_JF_USER, "").trim();
        final String pass = prefs.getString(KEY_JF_PASS, "");
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(user)) {
            if (fromButton && loginStatus != null) loginStatus.setText(R.string.nas_no_server);
            else showNasHint(getString(R.string.nas_no_server));
            return;
        }
        jelly.setServer(url);
        if (fromButton && loginStatus != null) loginStatus.setText(R.string.settings_logging_in);
        if (!fromButton) showNasHint(getString(R.string.nas_loading));
        new Thread(() -> {
            String error = null;
            try {
                jelly.authenticate(user, pass);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String fe = error;
            runOnUiThread(() -> {
                if (fe != null) {
                    if (loginStatus != null) loginStatus.setText(getString(R.string.settings_login_fail) + fe);
                    if (currentTab == 3) showNasHint(getString(R.string.settings_login_fail) + fe);
                    return;
                }
                if (loginStatus != null) loginStatus.setText(R.string.settings_login_ok);
                if (currentTab == 3) openNasLevel(null, getString(R.string.nas_title), true);
            });
        }).start();
    }

    // ---------------- 下载 ----------------

    private void setupDownload() {
        dlUrl = findViewById(R.id.dlUrl);
        findViewById(R.id.btnDownload).setOnClickListener(v -> startDownload());
        findViewById(R.id.btnOpenDownloads).setOnClickListener(v -> {
            try {
                startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
            } catch (Exception ignore) {
            }
        });
    }

    private void startDownload() {
        String url = dlUrl.getText().toString().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, R.string.download_bad_url, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = Uri.parse(url);
            String name = uri.getLastPathSegment();
            if (TextUtils.isEmpty(name) || !name.contains(".")) {
                name = "download_" + System.currentTimeMillis() + ".mp4";
            }
            DownloadManager.Request req = new DownloadManager.Request(uri);
            req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            req.setTitle(name);
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(req);
                Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show();
                dlUrl.setText("");
            }
        } catch (Exception e) {
            Toast.makeText(this, "下载失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- 我的 / 设置 ----------------

    private void setupProfile() {
        jellyfinUrl = findViewById(R.id.jellyfinUrl);
        jellyfinUser = findViewById(R.id.jellyfinUser);
        jellyfinPass = findViewById(R.id.jellyfinPass);
        loginStatus = findViewById(R.id.loginStatus);
        versionText = findViewById(R.id.versionText);
        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> {
            prefs.edit()
                    .putString(KEY_JELLYFIN, jellyfinUrl.getText().toString().trim())
                    .putString(KEY_JF_USER, jellyfinUser.getText().toString().trim())
                    .putString(KEY_JF_PASS, jellyfinPass.getText().toString())
                    .apply();
            jelly.setServer(jellyfinUrl.getText().toString().trim());
            nasLoadedOnce = false;
            loginJellyfin(true);
        });
        findViewById(R.id.col3).setOnClickListener(v -> applyColumns(3));
        findViewById(R.id.col4).setOnClickListener(v -> applyColumns(4));
        findViewById(R.id.col5).setOnClickListener(v -> applyColumns(5));
        findViewById(R.id.qHigh).setOnClickListener(v -> applyQuality(0));
        findViewById(R.id.qMid).setOnClickListener(v -> applyQuality(1));
        findViewById(R.id.qLow).setOnClickListener(v -> applyQuality(2));
        findViewById(R.id.sp05).setOnClickListener(v -> applySpeed(0.5f));
        findViewById(R.id.sp10).setOnClickListener(v -> applySpeed(1.0f));
        findViewById(R.id.sp125).setOnClickListener(v -> applySpeed(1.25f));
        findViewById(R.id.sp15).setOnClickListener(v -> applySpeed(1.5f));
        findViewById(R.id.sp20).setOnClickListener(v -> applySpeed(2.0f));
        updateColStyle();
        updateQualityStyle();
        updateSpeedStyle();
        try {
            String vn = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionText.setText("版本 " + vn);
        } catch (Exception ignore) {
        }
    }

    private void fillProfile() {
        if (jellyfinUrl != null) jellyfinUrl.setText(prefs.getString(KEY_JELLYFIN, ""));
        if (jellyfinUser != null) jellyfinUser.setText(prefs.getString(KEY_JF_USER, ""));
        if (jellyfinPass != null) jellyfinPass.setText(prefs.getString(KEY_JF_PASS, ""));
        if (loginStatus != null) {
            loginStatus.setText(jelly.isLoggedIn() ? getString(R.string.settings_login_ok) : "");
        }
        updateColStyle();
    }

    private void applyColumns(int n) {
        spanCount = n;
        prefs.edit().putInt(KEY_COLUMNS, n).apply();
        gridLm.setSpanCount(n);
        thumbAdapter = new ThumbAdapter(items, spanCount, position -> {
            showGrid(false);
            pager.setCurrentItem(position, false);
            updateBadge(position);
        });
        grid.setAdapter(thumbAdapter);
        updateColStyle();
    }

    private void updateColStyle() {
        TextView c3 = findViewById(R.id.col3);
        TextView c4 = findViewById(R.id.col4);
        TextView c5 = findViewById(R.id.col5);
        c3.setTextColor(spanCount == 3 ? 0xFFFFFFFF : 0xFF9A9AAA);
        c4.setTextColor(spanCount == 4 ? 0xFFFFFFFF : 0xFF9A9AAA);
        c5.setTextColor(spanCount == 5 ? 0xFFFFFFFF : 0xFF9A9AAA);
    }

    private long bitrateFor(int q) {
        switch (q) {
            case 1: return 4000000L;
            case 2: return 2000000L;
            default: return 8000000L;
        }
    }

    private void applyQuality(int q) {
        quality = q;
        prefs.edit().putInt(KEY_QUALITY, q).apply();
        jelly.setBitrate(bitrateFor(q));
        updateQualityStyle();
    }

    private void updateQualityStyle() {
        TextView a = findViewById(R.id.qHigh);
        TextView b = findViewById(R.id.qMid);
        TextView c = findViewById(R.id.qLow);
        a.setTextColor(quality == 0 ? 0xFFFFFFFF : 0xFF9A9AAA);
        b.setTextColor(quality == 1 ? 0xFFFFFFFF : 0xFF9A9AAA);
        c.setTextColor(quality == 2 ? 0xFFFFFFFF : 0xFF9A9AAA);
    }

    private void applySpeed(float s) {
        playSpeed = s;
        prefs.edit().putFloat(KEY_SPEED, s).apply();
        if (adapter != null) adapter.setDefaultSpeed(s);
        updateSpeedStyle();
    }

    private void updateSpeedStyle() {
        int[] ids = {R.id.sp05, R.id.sp10, R.id.sp125, R.id.sp15, R.id.sp20};
        float[] vals = {0.5f, 1.0f, 1.25f, 1.5f, 2.0f};
        for (int i = 0; i < ids.length; i++) {
            TextView t = findViewById(ids[i]);
            boolean sel = Math.abs(playSpeed - vals[i]) < 0.001f;
            t.setTextColor(sel ? 0xFFFFFFFF : 0xFF9A9AAA);
            t.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    // ---------------- 生命周期 ----------------

    @Override
    protected void onPause() {
        super.onPause();
        savePosition();
        if (currentTab == 0 && feedContainer.getVisibility() == View.VISIBLE && !items.isEmpty()) {
            adapter.pauseActive(pager.getCurrentItem());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentTab == 0 && feedContainer.getVisibility() == View.VISIBLE
                && !items.isEmpty() && !gridVisible) {
            adapter.setActivePosition(pager.getCurrentItem());
        }
    }
}

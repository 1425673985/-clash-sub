package com.videobrowser.app;

import android.net.Uri;

/** 一条媒体记录：视频或图片。 */
public class MediaItem {
    public final Uri uri;
    public final boolean isVideo;
    public final String name;
    public final long durationMs; // 视频时长(毫秒)，图片为 0

    public MediaItem(Uri uri, boolean isVideo, String name) {
        this(uri, isVideo, name, 0L);
    }

    public MediaItem(Uri uri, boolean isVideo, String name, long durationMs) {
        this.uri = uri;
        this.isVideo = isVideo;
        this.name = name;
        this.durationMs = durationMs;
    }
}

package com.videobrowser.app;

import android.net.Uri;

/** 一条媒体记录：视频或图片。 */
public class MediaItem {
    public final Uri uri;
    public final boolean isVideo;
    public final String name;

    public MediaItem(Uri uri, boolean isVideo, String name) {
        this.uri = uri;
        this.isVideo = isVideo;
        this.name = name;
    }
}

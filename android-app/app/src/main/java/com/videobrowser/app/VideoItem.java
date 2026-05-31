package com.videobrowser.app;

import android.net.Uri;

/** 一条视频记录。 */
public class VideoItem {
    public final Uri uri;
    public final String name;

    public VideoItem(Uri uri, String name) {
        this.uri = uri;
        this.name = name;
    }
}

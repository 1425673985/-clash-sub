package com.videobrowser.app;

/** Jellyfin 媒体条目。 */
public class JItem {
    public final String id;
    public final String name;
    public final boolean isFolder;
    public final String type;
    public final boolean hasPrimary;   // 是否有封面图
    public final long resumeMs;        // 上次播放位置(毫秒)，0 表示从头

    public JItem(String id, String name, boolean isFolder, String type,
                 boolean hasPrimary, long resumeMs) {
        this.id = id;
        this.name = name;
        this.isFolder = isFolder;
        this.type = type;
        this.hasPrimary = hasPrimary;
        this.resumeMs = resumeMs;
    }
}

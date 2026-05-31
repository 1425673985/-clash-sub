package com.videobrowser.app;

/** Jellyfin 媒体条目。 */
public class JItem {
    public final String id;
    public final String name;
    public final boolean isFolder;
    public final String type;

    public JItem(String id, String name, boolean isFolder, String type) {
        this.id = id;
        this.name = name;
        this.isFolder = isFolder;
        this.type = type;
    }
}

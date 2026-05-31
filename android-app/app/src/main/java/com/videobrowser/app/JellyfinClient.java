package com.videobrowser.app;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 极简 Jellyfin 客户端：登录、列目录、构造强制转码(H.264/AAC) 的 HLS 播放地址。
 * 所有网络请求需在后台线程调用。
 */
public class JellyfinClient {

    private String serverUrl;
    private final String deviceId;
    private String token;
    private String userId;

    public JellyfinClient(String serverUrl, String deviceId) {
        this.serverUrl = normalize(serverUrl);
        this.deviceId = deviceId;
    }

    public void setServer(String url) {
        this.serverUrl = normalize(url);
        this.token = null;
        this.userId = null;
    }

    public boolean isLoggedIn() {
        return token != null && userId != null;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    private static String normalize(String url) {
        if (url == null) return "";
        url = url.trim();
        if (url.isEmpty()) return "";
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private String authHeader() {
        return "MediaBrowser Client=\"VideoBrowser\", Device=\"Android\", DeviceId=\""
                + deviceId + "\", Version=\"2.1\"";
    }

    public void authenticate(String username, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("Username", username);
        body.put("Pw", password == null ? "" : password);
        String resp = request("POST", "/Users/AuthenticateByName", body.toString());
        JSONObject o = new JSONObject(resp);
        token = o.getString("AccessToken");
        userId = o.getJSONObject("User").getString("Id");
    }

    /** parentId 为 null 时返回媒体库(Views)，否则返回该目录下条目。 */
    public List<JItem> listItems(String parentId) throws Exception {
        String path;
        if (parentId == null) {
            path = "/Users/" + userId + "/Views";
        } else {
            path = "/Users/" + userId + "/Items?ParentId=" + Uri.encode(parentId)
                    + "&SortBy=IsFolder,SortName&SortOrder=Ascending"
                    + "&Fields=BasicSyncInfo&Limit=2000";
        }
        String resp = request("GET", path, null);
        JSONObject o = new JSONObject(resp);
        JSONArray arr = o.optJSONArray("Items");
        List<JItem> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.getJSONObject(i);
                String id = it.optString("Id");
                String name = it.optString("Name", "未命名");
                boolean isFolder = it.optBoolean("IsFolder", false);
                String type = it.optString("Type", "");
                list.add(new JItem(id, name, isFolder, type));
            }
        }
        return list;
    }

    /** 强制转码为 H.264/AAC 的 HLS 地址，几乎任何编码都能播。 */
    public String buildStreamUrl(String itemId) {
        return serverUrl + "/Videos/" + itemId + "/master.m3u8"
                + "?MediaSourceId=" + itemId
                + "&api_key=" + token
                + "&DeviceId=" + Uri.encode(deviceId)
                + "&VideoCodec=h264"
                + "&AudioCodec=aac,mp3"
                + "&TranscodingProtocol=hls"
                + "&TranscodingContainer=ts"
                + "&SegmentContainer=ts"
                + "&maxAudioChannels=2"
                + "&VideoBitrate=8000000"
                + "&AudioBitrate=192000"
                + "&ManifestSubtitles=vtt";
    }

    private String request(String method, String path, String body) throws Exception {
        URL url = new URL(serverUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("X-Emby-Authorization", authHeader());
            conn.setRequestProperty("Accept", "application/json");
            if (token != null) {
                conn.setRequestProperty("X-Emby-Token", token);
            }
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300)
                    ? conn.getInputStream() : conn.getErrorStream();
            String resp = readAll(is);
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + (resp.isEmpty() ? "" : ": " + resp));
            }
            return resp;
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}

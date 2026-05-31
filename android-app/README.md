# 📱 视频浏览器（安卓原生 App）

原生安卓短视频浏览器：授权后**自动扫描手机里的全部视频**，无需手动选择，竖向滑动切换，体验接近抖音。

## 功能

- **自动扫描**：通过 `MediaStore` 读取手机里所有视频（按添加时间倒序）。
- **竖向滑动切换**：`ViewPager2` 竖向分页，一次滑一个视频，自动播放当前、暂停其它。
- **序号标签**：顶部显示 `当前 / 总数`（如 `12 / 188`）。
- **左右滑动快进/快退 3 秒**，点击屏幕暂停/播放，视频循环播放。
- 仅需「读取视频」权限，不联网、不上传。

## 如何拿到 APK（自动编译）

本仓库配置了 GitHub Actions，推送到开发分支后会**自动编译 APK 并发布到 Releases**：

1. 打开仓库的 **Releases** 页面，找到 `手机视频浏览器 APK（最新）`（tag：`app-latest`）。
2. 下载里面的 `VideoBrowser.apk`，在安卓手机上点击安装。
3. 首次安装需在弹窗里允许“安装未知来源应用”。
4. 打开 App → 允许「读取视频」权限 → 自动加载手机里的视频，上下滑动浏览。

> 也可在 **Actions** 页面对应的运行记录里，从 Artifacts 下载 `VideoBrowser-apk`。

## 本地编译（可选）

需要 JDK 17 + Android SDK（platform 34、build-tools 34）：

```bash
cd android-app
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 技术说明

- 语言：Java，`minSdk 26`，`targetSdk 34`。
- 播放：系统 `VideoView`（无需第三方解码库），支持系统能解码的常见格式（mp4/mov/webm 等）。
- 权限：Android 13+ 用 `READ_MEDIA_VIDEO`，12 及以下用 `READ_EXTERNAL_STORAGE`。

## 操作

| 操作 | 效果 |
| --- | --- |
| 上 / 下滑动 | 切换上一个 / 下一个视频 |
| 左滑 / 右滑 | 快进 / 快退 3 秒 |
| 点击屏幕 | 暂停 / 播放 |
| 顶部标签 | 当前序号 / 总数 |

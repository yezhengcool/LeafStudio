# LeafStudio TV Player

一个功能完整的 Android TV 播放器应用,支持从 M3U 播放列表加载频道并播放多种协议的视频流。

## 功能特性

- ✅ 支持 M3U/M3U8 播放列表格式
- ✅ 支持多种流媒体协议 (HTTP, HTTPS, HLS, RTSP)
- ✅ Android TV Leanback UI 界面
- ✅ 频道分组显示
- ✅ 遥控器导航支持
- ✅ ExoPlayer 视频播放引擎

## 技术栈

- **语言**: Kotlin
- **最低 Android 版本**: API 21 (Android 5.0)
- **目标 SDK**: API 34 (Android 14)
- **播放器**: ExoPlayer (Media3)
- **UI 框架**: AndroidX Leanback
- **网络**: OkHttp
- **协程**: Kotlin Coroutines

## 项目结构

```
LeafStudio/
├── app/
│   ├── src/main/
│   │   ├── java/com/leafstudio/tvplayer/
│   │   │   ├── MainActivity.kt          # 主界面 - 频道列表
│   │   │   ├── PlaybackActivity.kt      # 播放界面
│   │   │   ├── model/
│   │   │   │   └── Channel.kt           # 频道数据模型
│   │   │   ├── parser/
│   │   │   │   └── M3UParser.kt         # M3U 解析器
│   │   │   ├── network/
│   │   │   │   └── PlaylistLoader.kt    # 播放列表加载器
│   │   │   └── ui/
│   │   │       └── ChannelPresenter.kt  # 频道卡片展示
│   │   ├── res/
│   │   │   ├── layout/                  # 布局文件
│   │   │   ├── values/                  # 资源文件
│   │   │   └── mipmap/                  # 应用图标
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## 构建项目

### 前置要求

- Android Studio Arctic Fox 或更高版本
- JDK 8 或更高版本
- Android SDK (API 21+)

### 构建步骤

1. 克隆项目:
```bash
git clone <repository-url>
cd LeafStudio
```

2. 使用 Gradle 构建:
```bash
./gradlew build
```

3. 在 Android TV 模拟器或设备上安装:
```bash
./gradlew installDebug
```

## 使用说明

1. **启动应用**: 在 Android TV 启动器中找到 "LeafStudio TV Player"
2. **浏览频道**: 使用遥控器方向键浏览频道列表
3. **选择频道**: 按遥控器确认键选择要播放的频道
4. **播放控制**: 播放界面支持暂停/播放、快进/快退等操作

## 配置

默认播放列表 URL 在 `app/src/main/res/values/strings.xml` 中配置:

```xml
<string name="default_playlist_url">https://yezheng.dpdns.org/tv/ydtv.txt</string>
```

可以修改此 URL 以使用自己的 M3U 播放列表。

## M3U 播放列表格式

应用支持标准的 M3U 格式,示例:

```
#EXTM3U
#EXTINF:-1 tvg-logo="http://example.com/logo.png" group-title="新闻",CCTV1
http://example.com/stream1.m3u8
#EXTINF:-1 tvg-logo="http://example.com/logo2.png" group-title="体育",CCTV5
http://example.com/stream2.m3u8
```

## 依赖库

- AndroidX Core & AppCompat
- AndroidX Leanback (TV UI)
- Media3 ExoPlayer (视频播放)
- OkHttp (网络请求)
- Kotlin Coroutines (异步处理)
- Glide (图片加载)

## 许可证

本项目仅供学习和研究使用。

## 注意事项

> **重要**: 
> - 需要在 `app/src/main/res/mipmap-*` 目录中添加应用图标 (`ic_launcher.png`)
> - 需要在 `app/src/main/res/drawable` 目录中添加 TV banner 图片 (`app_banner.png`)
> - 确保播放列表 URL 可访问且格式正确
> - 某些流媒体可能需要特定的网络配置或权限

## 故障排除

### 播放列表加载失败
- 检查网络连接
- 验证播放列表 URL 是否正确
- 确保设备可以访问播放列表服务器

### 视频无法播放
- 检查流媒体 URL 是否有效
- 验证流媒体协议是否支持
- 查看 Logcat 日志获取详细错误信息

## 开发计划

- [ ] 添加收藏功能
- [ ] 实现搜索功能
- [ ] 支持 EPG (电子节目指南)
- [ ] 添加播放历史记录
- [ ] 支持多个播放列表源
- [ ] 添加设置界面

## 联系方式

如有问题或建议,请提交 Issue。

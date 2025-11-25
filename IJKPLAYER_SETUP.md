# LeafStudio TV Player - IJKPlayer 集成说明

## 当前状态

✅ **已完成的工作:**
1. 替换应用图标为熊猫图标 🐼
2. 恢复并完善 IJKPlayer 的完整代码实现
3. 修复 RTMP 和其他协议的闪退问题
4. 添加全面的错误处理机制
5. 支持 ExoPlayer、IJKPlayer 硬解、IJKPlayer 软解三种解码器切换

## 关于 IJKPlayer

IJKPlayer 是一个基于 FFmpeg 的强大视频播放器,支持:
- 硬件解码(性能更好,功耗更低)
- 软件解码(兼容性更好)
- 更好的 RTMP、RTSP 等协议支持

## 如何启用 IJKPlayer

由于 IJKPlayer 的官方 Maven 仓库已不再维护,您需要手动添加 IJKPlayer 库:

### 方法一:使用预编译的 AAR 文件(推荐)

1. **下载 IJKPlayer AAR 文件**
   - 访问: https://github.com/bilibili/ijkplayer/releases
   - 下载最新版本的 AAR 文件,通常包括:
     - `ijkplayer-java-x.x.x.aar`
     - `ijkplayer-armv7a-x.x.x.aar`
     - `ijkplayer-arm64-x.x.x.aar`

2. **创建 libs 目录**
   ```bash
   mkdir -p app/libs
   ```

3. **复制 AAR 文件到 libs 目录**
   ```bash
   cp ijkplayer-*.aar app/libs/
   ```

4. **修改 app/build.gradle**
   在 dependencies 部分,取消注释:
   ```gradle
   implementation fileTree(dir: 'libs', include: ['*.aar'])
   ```

5. **重新编译应用**
   ```bash
   ./gradlew assembleDebug
   ```

### 方法二:使用本地 Maven 仓库

如果您已经在本地 Maven 仓库中安装了 IJKPlayer,可以在 `app/build.gradle` 中取消注释:

```gradle
implementation 'tv.danmaku.ijk.media:ijkplayer-java:0.8.8'
implementation 'tv.danmaku.ijk.media:ijkplayer-armv7a:0.8.8'
implementation 'tv.danmaku.ijk.media:ijkplayer-arm64:0.8.8'
```

## 当前版本功能

### ✅ 已修复的问题

1. **RTMP 协议闪退** - 添加了完整的错误处理
2. **PHP 重定向链接闪退** - 添加了 URL 验证和异常捕获
3. **IJKPlayer 库未安装提示** - 改为友好的提示信息,并自动回退到 ExoPlayer

### ✅ 支持的功能

1. **三种解码器**:
   - ExoPlayer(默认,无需额外配置)
   - IJKPlayer 硬解(需要手动添加库)
   - IJKPlayer 软解(需要手动添加库)

2. **协议支持**:
   - HLS (m3u8)
   - DASH
   - RTSP
   - RTMP/RTMPS
   - HTTP/HTTPS
   - SmoothStreaming

3. **错误处理**:
   - 自动线路切换
   - 播放失败时的友好提示
   - 解码器不可用时自动回退

## 使用说明

1. **安装应用**
   - 使用桌面上的 `LeafStudio-熊猫图标-v1.1.apk`

2. **选择解码器**
   - 点击底部的"解码"按钮
   - 选择 ExoPlayer(默认)或 IJKPlayer(如果已安装)

3. **播放视频**
   - 应用会自动处理各种协议
   - 如果当前线路失败,会自动切换到下一个线路

## 技术细节

### ExoPlayer vs IJKPlayer

| 特性 | ExoPlayer | IJKPlayer |
|------|-----------|-----------|
| 协议支持 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| 性能 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 兼容性 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 配置难度 | 简单 | 需要手动添加 |
| 硬件解码 | 支持 | 支持 |
| 软件解码 | 有限 | 完整支持 |

### 错误处理机制

1. **URL 验证** - 在设置媒体源前验证 URL 格式
2. **数据源回退** - RTMP 不可用时自动使用默认数据源
3. **播放器回退** - IJKPlayer 不可用时自动切换到 ExoPlayer
4. **线路切换** - 播放失败时自动尝试其他线路

## 故障排除

### 问题: "IJKPlayer 库未安装"

**解决方案**: 按照上面的"如何启用 IJKPlayer"步骤添加 IJKPlayer 库

### 问题: 视频播放闪退

**解决方案**: 
1. 检查视频 URL 是否有效
2. 尝试切换到其他解码器
3. 查看 Logcat 日志获取详细错误信息

### 问题: RTMP 视频无法播放

**解决方案**:
1. 确保已添加 RTMP 数据源依赖(已包含在项目中)
2. 尝试使用 IJKPlayer 解码器
3. 检查网络连接

## 文件位置

- **APK 文件**: `~/Desktop/LeafStudio-熊猫图标-v1.1.apk`
- **项目目录**: `/Volumes/Ye 1/git_code/LeafStudio`
- **主要代码**: `app/src/main/java/com/leafstudio/tvplayer/PlaybackActivity.kt`

## 版本信息

- **版本号**: 1.1
- **编译时间**: 2025-11-24 10:46
- **文件大小**: 11MB

---

如有问题,请查看项目中的代码注释或联系开发者。

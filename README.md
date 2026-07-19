# AirPlay — Android DLNA/AirPlay 视频投屏

将手机本地视频投屏到 UPnP/DLNA 媒体渲染器（智能电视、电视盒子等）。

<table>
<tr>
<td><img src="docs/screenshots/home.jpg" width="240" alt="视频列表"/></td>
<td><img src="docs/screenshots/player.jpg" width="240" alt="播放控制"/></td>
<td><img src="docs/screenshots/casting.jpg" width="240" alt="投屏中"/></td>
</tr>
</table>

## 功能

- **本地视频扫描** — 自动扫描设备上的视频文件，按标题/时长展示
- **DLNA 设备发现** — 自动发现局域网内的 UPnP 媒体渲染器
- **视频投屏** — 将本地视频推送至电视播放，支持播放/暂停/跳转/音量控制
- **HTTP 流媒体** — 内建 NanoHTTPD 服务器，即时转码并流式传输
- **多选批量投屏** — 支持多选视频，批量添加到播放队列
- **播放队列** — 支持队列管理、自动连播、进度持久化
- **后台播放** — 前台服务通知栏控制，锁屏继续播放

## 快速开始

```bash
git clone https://github.com/your-username/airplay.git
cd airplay
./gradlew assembleDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### 开发环境

| 工具 | 版本 |
|------|------|
| Android Gradle Plugin | 8.7.0 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 34 |
| minSdk | 26 |
| Java | 17+ |

## 使用说明

1. 安装 APK 后打开应用，授予文件读取权限
2. 应用自动扫描本地视频并展示在主页
3. 点击右下角 FAB 刷新设备列表，选择目标电视
4. 点击视频卡片开始投屏
5. 长按视频卡片可进入多选模式，批量投屏

> **注意**：部分电视（如酷喵）使用端口 7300 可能存在兼容问题，建议使用端口 38400 的渲染器。

## 技术栈

| 层 | 技术 |
|----|------|
| UI | Jetpack Compose + Material 3 |
| 播放/渲染 | Activity + Foreground Service |
| DLNA/UPnP | jUPnP (AndroidUpnpServiceConfiguration) |
| HTTP 流媒体 | NanoHTTPD |
| 图片加载 | Coil (coil-compose) |
| 视频缩略图 | MediaStore.Thumbnails + 缓存回退 |

## 架构

```
AirPlayApp (Application)
  ├── DeviceDiscoveryManager — UPnP 设备发现
  ├── MediaRenderManager — AVTransport 指令
  ├── StreamServerManager — NanoHTTPD 流媒体服务
  ├── MainViewModel — 队列状态 + 轮询 + 持久化
  ├── VideoScanner — MediaStore 视频扫描
  └── CastForegroundService — 后台投屏通知
```

<img src="docs/screenshots/architecture.png" width="480" alt="架构图"/>

## 许可证

[MIT](LICENSE)

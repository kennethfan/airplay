# ADR 0001: 视频缩略图来源策略

**状态**: 已接受  
**日期**: 2026-07-19  

## 背景

HomeScreen 的 `VideoGridItem` 使用 Coil `AsyncImage` 加载视频缩略图。原有实现将 `thumbnailUri` 设为视频的 content URI（`content://media/external/video/media/{id}`），Coil 在没有 `VideoFrameDecoder` 的情况下无法解码视频文件，导致缩略图实际不可用。

## 决策

采用单层来源策略：

1. **`MediaStore.Video.Thumbnails`（原生缩略图）** — Android 系统在媒体扫描时自动为每个视频生成迷你缩略图，存储在专用存储中。通过 `ContentUris.withAppendedId(MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI, videoId)` 获取 URI。特点是：轻量小文件、读取极快、不消耗解码资源。绝大多数设备上可用。

2. **兜底** — 当原生缩略图不可用时（极少数设备），UI 显示 Videocam 图标作为错误占位。

## 考量

### 备选方案

| 方案 | 优点 | 缺点 |
|------|------|------|
| 仅 MediaStore.Thumbnails | 最快、最省资源 | 部分设备可能没有生成缩略图 |
| 仅 Coil VideoFrameDecoder | 无额外依赖，始终可用 | 每次加载都解码视频帧，CPU 开销大、慢 |
| 自定义缩略图缓存（Bitmap 存本地文件） | 完全可控 | 维护成本高，与系统媒体库功能重叠 |
| 保持原样（使用视频 content URI） | 无需改动 | 实际不可用（Coil 缺乏视频解码器） |

### 选择两层策略的理由

- 性能优先：绝大部分场景命中第一层，体验接近原生相册
- 容错：第二层保证没有缩略图的视频也能显示
- 低维护成本：完全基于 Android SDK + Coil 已有能力

## 影响

- `VideoScanner.java`：`thumbnailUri` 改为指向 `MediaStore.Video.Thumbnails` 的 URI
- `HomeScreen.kt`：`AsyncImage` 增加 crossfade + shimmer placeholder + error 兜底
- 无需迁移：旧的 thumbnailUri 未被持久化，改动即时生效

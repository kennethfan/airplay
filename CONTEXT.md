# CONTEXT — AirPlay Domain Glossary

## VideoCard

用户界面中表示单个视频的卡片组件。在 2 列网格中展示：16:9 缩略图在上（右下角时长胶囊），**标题在图片下方**（单行省略，避免长文件名把扩展名挤成孤立一行）。选中时显示单层浅色蒙层 + 右上角对勾 + 边框变色。

## Thumbnail

视频的预览缩略图。两阶段加载：先发列表（content URI 占位，UI 显示 ErrorFallback），后台异步生成缩略图（查询 MediaStore.Video.Thumbnails → MediaStore API 生成 → 缓存到 cacheDir/thumbs/）。更新后 UI 淡入真正缩略图。

真实缩略图存放在 `MainViewModel.thumbnailUris: Map<Long, Uri>`，以 videoId 为键。**读取时必须走这个 Map**（`thumbnails[video.id]`），`VideoItem.thumbnailUri` 只是 content URI 占位符，直接用它渲染会显示灰底占位。

## Shimmer

缩略图加载中的骨架动画占位效果。视频列表首次加载时整个网格显示 ShimmerGrid，单个缩略图加载时每个卡片显示 shimmer 脉冲动画。滑光宽度按 `LocalDensity` 由 dp 换算，避免不同 DPI 下速度不一致。

## DurationPill

卡片右下角的半透明黑色圆角标签，显示视频时长（HH:MM:SS 或 MM:SS 格式）。

## MultiSelect

多选模式。进入后每个卡片显示选中态（蒙层 + 对勾），底部栏显示已选数量和投屏按钮。选中状态按 `videoId` 匹配，**不依赖列表位置**，因此排序变化不会丢失选中。

## ShimmerGrid

列表首次加载（或权限未授予时的等待状态）显示的骨架占位卡片，带脉冲亮度动画。

## SortOption / SortField

首页列表排序方式。`SortField` 有三个维度：`DATE`（按时间）、`DURATION`（按时长）、`SIZE`（按大小）；`SortOption = (field, descending)`。

- 点当前维度 → 翻转升降序；点其他维度 → 切换并重置为降序
- 主键相同则按文件名次级排序，**次级键固定升序，不随方向翻转**
- 持久化在 `airplay_queue` prefs 的 `sort_field` / `sort_descending`
- **排序只影响首页展示顺序，绝不打乱播放队列**（队列顺序由用户手动维护）

## VideoSorter

纯函数排序工具（`util/VideoSorter.kt`），不依赖 Android，可直接 JUnit 测试。`MainViewModel.applySort` 只是对它的一行委托。

## RecentPlay

最近播放记录。只在投屏指令成功下发后记录，字段为 `videoId / path / name / playedAt / deviceName`。

- **不持久化 `VideoItem`**（含 `Uri`，序列化麻烦），展示时用 `videoId` 回查最新扫描结果
- 上限 20 条，同一 videoId 再次播放时先移除再插到最前
- 回查不到的视频（文件已删除、或被扫描器当作短片段过滤）会被自动丢弃
- 存于 `airplay_queue` prefs 的 `recent_plays` JSON 数组
- 无记录时整块 UI 隐藏

## RelativeTime

相对时间格式化纯函数（`util/RelativeTime.kt`），输出 `刚刚 / N分钟前 / N小时前 / 昨天 / N天前 / MM-dd`。未来时间戳（设备时钟偏差）按「刚刚」处理，不产生负值。

## CastForegroundService

保持投屏期间进程存活的前台服务，持有 partial wake lock 并显示常驻通知。

**按需启动**：只在 `castVideos()` 时启动，在 `stopCasting()` / `stopAndClearQueue()` / 队列播完时停止。**不在 `Application.onCreate` 启动**——那会导致 App 一打开就常驻通知栏并持有 4 小时 wake lock。详见 ADR 0003。

## StreamWhitelist

`/stream` 接口的访问控制。只有通过 `buildStreamUrl()` 投屏过的路径才会被登记，其余请求返回 403。

**只做精确匹配，禁止二次 URL 解码**——否则 `%252F` 等双重编码可绕过白名单。详见 ADR 0003。

## Theme

深空蓝青配色（非 Netflix 红）。深色为默认设计主场景（背景 `#0B0E14`），同时提供浅色主题，`darkTheme` 参数跟随系统。

`surfaceTint` 设为透明，避免 Material3 给所有带 elevation 的卡片叠加彩色蒙层。Shapes 分 5 档，卡片用 `medium`（14dp），底部弹层用 `extraLarge`（28dp）。

# CONTEXT — AirPlay Domain Glossary

## VideoCard

用户界面中表示单个视频的卡片组件。在 2 列网格中展示，包含缩略图、标题覆盖和时长标签。

## Thumbnail

视频的预览缩略图。两阶段加载：先发列表（content URI 占位，UI 显示 ErrorFallback），后台异步生成缩略图（查询 MediaStore.Video.Thumbnails → MediaStore API 生成 → 缓存到 cacheDir/thumbs/）。更新后 UI 淡入真正缩略图。

## Shimmer

缩略图加载中的骨架动画占位效果。视频列表首次加载时整个网格显示 ShimmerGrid，单个缩略图加载时每个卡片显示 shimmer 脉冲动画。

## DurationPill

卡片右上角或右下角的半透明黑色圆角标签，显示视频时长（HH:MM:SS 或 MM:SS 格式）。

## MultiSelect

多选模式。进入后每个卡片左上角显示 checkbox，选中时卡片覆盖半透明遮罩，底部栏显示已选数量和批量投屏按钮。

## ShimmerGrid

列表首次加载（或权限未授予时的等待状态）显示的 8 个骨架占位卡片，带脉冲亮度动画。

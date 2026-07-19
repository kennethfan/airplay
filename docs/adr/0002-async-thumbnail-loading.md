# ADR 0002: Per-Item Lazy Thumbnail Resolution

**Status**: Accepted (revised)  
**Date**: 2026-07-19  
**Context**: Initial async approach resolved thumbnails for ALL videos in a background batch after emitting the list. On refresh, this still iterated every video even if cached, wasting I/O on off-screen items. Users reported "点刷新，所有的视频缩略图都重新生成了".

**Decision**: Each video's thumbnail is resolved lazily, triggered by Compose composition (`LaunchedEffect(video.id)` inside `LazyVerticalGrid`). This means only visible items trigger thumbnail resolution.

Architecture:
1. **`VideoScanner.resolveThumbnail()`** — resolves ONE video's thumbnail. Priority: disk cache → system thumbnails table → generate via MediaStore API + cache to `cacheDir/thumbs/`. Public, callable on demand.
2. **`MainViewModel._thumbnailUris: Map<Long, Uri>`** — accumulates resolved URIs. `resolveThumbnail(id)` skips if already resolved or pending, avoiding duplicate work.
3. **`HomeScreen.VideoGridItem`** — `LaunchedEffect(video.id)` calls `viewModel.resolveThumbnail(video.id)` when the item is composed (visible). Uses `thumbnailUri` parameter (resolved URI fallback → content URI placeholder → Coil error → ErrorFallback).

**Alternatives considered**:
- Batch resolve all after scan (v1 of this ADR): works but wastes work on off-screen items
- Batch resolve only first N items: fragile heuristic, still resolves all eventually
- No resolution at all, pure Coil VideoFrameDecoder: dependency unavailable

**Consequences**:
- Zero wasted thumbnail generation for off-screen items
- Scrolling triggers resolution naturally as items enter viewport
- Cache-first strategy makes subsequent loads near-instant (file existence check only)
- Over-resolve protection via `_pendingThumbnailIds` set prevents duplicate work on rapid scroll
- 500 videos in DB, grid shows instantly, only ~8 thumbnails are ever resolved

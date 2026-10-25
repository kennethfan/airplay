package com.airplay.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.airplay.model.PlaybackState
import com.airplay.model.VideoItem
import com.airplay.ui.viewmodel.MainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    video: VideoItem,
    onBack: () -> Unit
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val device by viewModel.currentDevice.collectAsState()
    val queue by viewModel.playQueue.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val allVideos by viewModel.videos.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val thumbnails by viewModel.thumbnailUris.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    var queueExpanded by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }

    val queueSize = queue.size
    val queuePosition = if (currentIndex >= 0) currentIndex + 1 else 1
    val hasNext = currentIndex >= 0 && currentIndex < queueSize - 1
    val hasPrevious = currentIndex > 0

    // 真实缩略图来自 ViewModel 的懒加载结果，回退到 VideoItem 自带 URI
    val thumbnail = thumbnails[video.id] ?: video.thumbnailUri
    val progress = if (video.durationMs > 0) {
        (positionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (queueSize > 1) "投屏中 · $queuePosition/$queueSize" else "投屏中",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopCasting()
                        onBack()
                    }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // 收藏当前正在投屏的视频；状态跟随 video.id，切换队列时自动更新
                    val isFavorite = video.id in favoriteIds
                    IconButton(onClick = { viewModel.toggleFavorite(video) }) {
                        Icon(
                            if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isFavorite) "取消收藏" else "收藏",
                            tint = if (isFavorite) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── 上部：16:9 大图 + 标题信息 ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // 16:9 主视觉，填满横向空间，取代原来的 160dp 小方块
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    if (thumbnail != null) {
                        AsyncImage(
                            model = thumbnail,
                            contentDescription = video.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Movie,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // 底部渐变 + 状态徽章
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                )
                            )
                    )

                    StatusBadge(
                        state = playbackState,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = video.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = device?.name ?: "未知设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── 下部：控制面板 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 只读进度条：让用户知道播到哪了
                    ProgressSection(
                        positionMs = positionMs,
                        durationMs = video.durationMs,
                        progress = progress
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // 播放控制
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedIconButton(
                            onClick = { viewModel.playPrevious() },
                            modifier = Modifier.size(52.dp),
                            enabled = hasPrevious
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "上一个",
                                modifier = Modifier.size(24.dp),
                                tint = if (hasPrevious)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                        }

                        Spacer(modifier = Modifier.width(28.dp))

                        FilledIconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(72.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = if (playbackState == PlaybackState.PLAYING)
                                    Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState == PlaybackState.PLAYING) "暂停" else "播放",
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(28.dp))

                        OutlinedIconButton(
                            onClick = { viewModel.playNext() },
                            modifier = Modifier.size(52.dp),
                            enabled = hasNext
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "下一个",
                                modifier = Modifier.size(24.dp),
                                tint = if (hasNext)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 次要操作：添加 / 停止，层级低于主播放键
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddSheet = true },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("加入队列", style = MaterialTheme.typography.labelLarge)
                        }

                        Button(
                            onClick = {
                                viewModel.stopCasting()
                                onBack()
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("停止投屏", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 队列（可折叠）
                    QueueSection(
                        queue = queue,
                        currentIndex = currentIndex,
                        expanded = queueExpanded,
                        onToggle = { queueExpanded = !queueExpanded },
                        onSkipTo = { viewModel.skipTo(it) },
                        onRemove = { viewModel.removeFromQueue(it) },
                        thumbnails = thumbnails
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddToQueueSheet(
            allVideos = allVideos,
            queue = queue,
            onDismiss = { showAddSheet = false },
            onAdd = { selected ->
                viewModel.addToQueue(selected)
                showAddSheet = false
            }
        )
    }
}

// ──────────────────────────────────────────────────────────────
// 进度展示（只读）
// ──────────────────────────────────────────────────────────────

@Composable
private fun ProgressSection(positionMs: Long, durationMs: Long, progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400),
        label = "progress"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        if (durationMs > 0) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (durationMs > 0) formatMs(positionMs) else "--:--",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (durationMs > 0) formatMs(durationMs) else "--:--",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusBadge(state: PlaybackState, modifier: Modifier = Modifier) {
    val (label, tint) = when (state) {
        PlaybackState.PLAYING -> "播放中" to MaterialTheme.colorScheme.secondary
        PlaybackState.PAUSED -> "已暂停" to MaterialTheme.colorScheme.tertiary
        PlaybackState.BUFFERING -> "缓冲中" to MaterialTheme.colorScheme.primary
        PlaybackState.STOPPED -> "已停止" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(tint, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────
// 队列折叠区
// ──────────────────────────────────────────────────────────────

@Composable
private fun QueueSection(
    queue: List<VideoItem>,
    currentIndex: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSkipTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    thumbnails: Map<Long, android.net.Uri>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = queue.isNotEmpty(), onClick = onToggle)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "播放列表",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (queue.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = "${queue.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (queue.isNotEmpty()) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(queue) { index, item ->
                        QueueItemRow(
                            index = index,
                            item = item,
                            thumbnail = thumbnails[item.id] ?: item.thumbnailUri,
                            isCurrent = index == currentIndex,
                            onSkipTo = { onSkipTo(index) },
                            onRemove = { onRemove(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItemRow(
    index: Int,
    item: VideoItem,
    thumbnail: android.net.Uri?,
    isCurrent: Boolean,
    onSkipTo: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .clickable(enabled = !isCurrent, onClick = onSkipTo)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 队列缩略图保持 16:9，不再压成正方形
        Box(
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = thumbnail,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.durationFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        if (isCurrent) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "正在播放",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "移除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────
// 添加到队列弹层
// ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToQueueSheet(
    allVideos: List<VideoItem>,
    queue: List<VideoItem>,
    onDismiss: () -> Unit,
    onAdd: (List<VideoItem>) -> Unit
) {
    // 预计算集合，避免每行 O(N) 的 in queue 判断
    val queuePaths = remember(queue) { queue.map { it.path }.toSet() }
    var selected by remember { mutableStateOf(setOf<VideoItem>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "添加到播放队列",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "已在队列中的视频会显示为不可选",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                itemsIndexed(allVideos) { _, item ->
                    val isInQueue = item.path in queuePaths
                    val isSelected = item in selected

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable(enabled = !isInQueue) {
                                selected = if (isSelected) selected - item else selected + item
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected || isInQueue,
                            enabled = !isInQueue,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + item else selected - item
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            AsyncImage(
                                model = item.thumbnailUri,
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isInQueue)
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.durationFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isInQueue) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "已在队列",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onAdd(selected.toList()) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(
                    if (selected.isEmpty()) "请选择视频" else "添加 ${selected.size} 个视频",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// 工具
// ──────────────────────────────────────────────────────────────

/** 毫秒格式化为 HH:MM:SS 或 MM:SS */
private fun formatMs(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L)) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

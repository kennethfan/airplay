package com.airplay.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.airplay.model.PlaybackState
import com.airplay.model.VideoItem
import com.airplay.ui.viewmodel.MainViewModel

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

    var queueExpanded by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }

    val queueSize = queue.size
    val queuePosition = if (currentIndex >= 0) currentIndex + 1 else 1
    val hasNext = currentIndex >= 0 && currentIndex < queueSize - 1
    val hasPrevious = currentIndex > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = video.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopCasting()
                        onBack()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
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
            // ── Upper half: content area ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Thumbnail or placeholder
                if (video.thumbnailUri != null) {
                    AsyncImage(
                        model = video.thumbnailUri,
                        contentDescription = video.name,
                        modifier = Modifier
                            .size(160.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Video name
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Device name
                Text(
                    text = device?.name ?: "未知设备",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Playback state indicator
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = buildString {
                            append(
                                when (playbackState) {
                                    PlaybackState.PLAYING -> "播放中"
                                    PlaybackState.PAUSED -> "已暂停"
                                    PlaybackState.STOPPED -> "已停止"
                                    PlaybackState.BUFFERING -> "缓冲中…"
                                }
                            )
                            if (queueSize > 0) {
                                append("  $queuePosition/$queueSize")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // ── Lower half: control panel card ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Queue header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { queueExpanded = !queueExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "播放列表 ($queueSize)",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Queue panel (expandable)
                    if (queueSize > 0) {
                        AnimatedVisibility(
                            visible = queueExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                itemsIndexed(queue) { index, item ->
                                    QueueItemRow(
                                        index = index,
                                        item = item,
                                        isCurrent = index == currentIndex,
                                        onSkipTo = { viewModel.skipTo(index) },
                                        onRemove = { viewModel.removeFromQueue(index) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Playback controls row ──
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Skip Previous
                        if (hasPrevious) {
                            OutlinedIconButton(
                                onClick = { viewModel.playPrevious() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious,
                                    contentDescription = "上一个",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(24.dp))
                        }

                        // Play / Pause
                        FilledIconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(72.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (playbackState == PlaybackState.PLAYING)
                                    Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState == PlaybackState.PLAYING) "暂停" else "播放",
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        // Skip Next
                        if (hasNext) {
                            Spacer(modifier = Modifier.width(24.dp))
                            OutlinedIconButton(
                                onClick = { viewModel.playNext() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = "下一个",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Add to queue button
                    OutlinedButton(
                        onClick = { showAddSheet = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("添加视频到队列")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Stop casting button
                    OutlinedButton(
                        onClick = {
                            viewModel.stopCasting()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("停止投屏")
                    }
                }
            }
        }
    }

    // Add-to-queue bottom sheet
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

@Composable
private fun QueueItemRow(
    index: Int,
    item: VideoItem,
    isCurrent: Boolean,
    onSkipTo: () -> Unit,
    onRemove: () -> Unit
) {
    val bgColor = if (isCurrent)
        MaterialTheme.colorScheme.primaryContainer
    else
        Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
            .clickable(enabled = !isCurrent, onClick = onSkipTo)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index number
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Thumbnail
        AsyncImage(
            model = item.thumbnailUri,
            contentDescription = item.name,
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Name and duration
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.durationFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Delete button
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "移除",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToQueueSheet(
    allVideos: List<VideoItem>,
    queue: List<VideoItem>,
    onDismiss: () -> Unit,
    onAdd: (List<VideoItem>) -> Unit
) {
    val queuePaths = remember(queue) { queue.map { it.path }.toSet() }
    var selected by remember { mutableStateOf(setOf<VideoItem>()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "添加到队列",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

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
                            .clickable(enabled = !isInQueue) {
                                selected = if (isSelected) selected - item else selected + item
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        AsyncImage(
                            model = item.thumbnailUri,
                            contentDescription = item.name,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodySmall,
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
                            Text(
                                text = "已在队列",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onAdd(selected.toList()) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("添加 (${selected.size})")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

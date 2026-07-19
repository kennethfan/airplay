package com.airplay.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    var showAddDrawer by remember { mutableStateOf(false) }
    var selectedAddVideos by remember { mutableStateOf(setOf<VideoItem>()) }

    val queueSize = queue.size
    val queuePosition = if (currentIndex >= 0) currentIndex + 1 else 1
    val hasNext = currentIndex >= 0 && currentIndex < queueSize - 1

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (queueSize > 1) "${video.name} ($queuePosition/$queueSize)" else video.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (hasNext) {
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { viewModel.playNext() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("跳过 ${queueSize - currentIndex - 1}")
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopCasting()
                        onBack()
                    }) {
                        Icon(Icons.Default.Stop, contentDescription = "停止投屏")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Now playing info
            Text(
                "正在投屏到",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                device?.name ?: "未知设备",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Video info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        video.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        video.durationFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Queue panel
            if (queueSize > 0) {
                QueuePanel(
                    queue = queue,
                    currentIndex = currentIndex,
                    expanded = queueExpanded,
                    onToggle = { queueExpanded = !queueExpanded },
                    onRemove = { viewModel.removeFromQueue(it) },
                    onMoveUp = { index ->
                        if (index > 0) viewModel.moveInQueue(index, index - 1)
                    },
                    onMoveDown = { index ->
                        if (index < queueSize - 1) viewModel.moveInQueue(index, index + 1)
                    },
                    onSkipTo = { viewModel.skipTo(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Add to queue button
            OutlinedButton(
                onClick = { showAddDrawer = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加到队列")
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Playback controls
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Skip button (only when queue has more items after current)
                if (hasNext) {
                    OutlinedButton(
                        onClick = { viewModel.playNext() },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("跳过")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }

                // Play/Pause button
                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (playbackState == PlaybackState.PLAYING)
                            Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState == PlaybackState.PLAYING) "暂停" else "播放",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // State indicator with queue position
            Text(
                buildString {
                    append(
                        when (playbackState) {
                            PlaybackState.PLAYING -> "播放中"
                            PlaybackState.PAUSED -> "已暂停"
                            PlaybackState.STOPPED -> "已停止"
                            PlaybackState.BUFFERING -> "缓冲中…"
                        }
                    )
                    if (queueSize > 0) {
                        append(" $queuePosition/$queueSize")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Stop button
            OutlinedButton(onClick = {
                viewModel.stopCasting()
                onBack()
            }) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("停止投屏")
            }
        }
    }

    // Add-to-queue bottom sheet
    if (showAddDrawer) {
        AddToQueueSheet(
            allVideos = allVideos,
            queue = queue,
            onDismiss = { showAddDrawer = false },
            onAdd = { selected ->
                viewModel.addToQueue(selected)
                showAddDrawer = false
            }
        )
    }
}

@Composable
private fun QueuePanel(
    queue: List<VideoItem>,
    currentIndex: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRemove: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onSkipTo: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "播放列表 (${queue.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开"
                )
            }

            // Expanded queue list
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    itemsIndexed(queue) { index, item ->
                        val isCurrent = index == currentIndex
                        QueueItemRow(
                            index = index,
                            item = item,
                            isCurrent = isCurrent,
                            onRemove = { onRemove(index) },
                            onMoveUp = { onMoveUp(index) },
                            onMoveDown = { onMoveDown(index) },
                            onSkipTo = { onSkipTo(index) },
                    queueSize = queue.size
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
    isCurrent: Boolean,
    queueSize: Int,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSkipTo: () -> Unit
) {
    val bgColor = if (isCurrent)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isCurrent, onClick = onSkipTo)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index
            Text(
                "${index + 1}",
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
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Name and duration
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.durationFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Move up
            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移", modifier = Modifier.size(18.dp))
            }
            // Move down
            IconButton(
                onClick = onMoveDown,
                enabled = index < queueSize - 1,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移", modifier = Modifier.size(18.dp))
            }

            // Delete
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "移除", modifier = Modifier.size(18.dp))
            }
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
                "添加到队列",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 400.dp)
            ) {
                itemsIndexed(allVideos) { _, item ->
                    val isInQueue = item.path in queue.map { it.path }
                    val isSelected = item in selected

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isInQueue) {
                                selected = if (isSelected)
                                    selected - item
                                else
                                    selected + item
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
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                item.durationFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isInQueue) {
                            Text(
                                "已在队列",
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

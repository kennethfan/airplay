package com.airplay.ui.screen

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.airplay.model.CastDevice
import com.airplay.model.VideoItem
import com.airplay.ui.viewmodel.MainViewModel
import com.airplay.ui.viewmodel.SortField
import com.airplay.util.LogBuffer

/** 网格外边距与卡片间距，统一从这里取，避免各处分叉 */
private val GridHorizontalPadding = 16.dp
private val GridSpacing = 12.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(),
    onCast: (VideoItem, CastDevice) -> Unit,
    onCastMultiple: (List<VideoItem>, CastDevice) -> Unit = { _, _ -> },
    onNavigateToPlayer: () -> Unit = {}
) {
    val context = LocalContext.current
    val videos by viewModel.videos.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isLoadingVideos by viewModel.isLoadingVideos.collectAsState()
    val thumbnails by viewModel.thumbnailUris.collectAsState()
    val playQueue by viewModel.playQueue.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val currentDevice by viewModel.currentDevice.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    val isQueueActive = playQueue.isNotEmpty() && currentIndex >= 0
    val currentQueueVideo = viewModel.currentQueueVideo

    // Multi-select state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedVideos by remember { mutableStateOf(setOf<VideoItem>()) }

    // Device sheet state
    var showDeviceSheet by remember { mutableStateOf(false) }
    var pendingSingleVideo by remember { mutableStateOf<VideoItem?>(null) }
    var pendingMultiVideos by remember { mutableStateOf<List<VideoItem>?>(null) }
    val isMultiCast = pendingMultiVideos != null

    // 调试入口收进溢出菜单，不再占用主操作区
    var showOverflow by remember { mutableStateOf(false) }
    var showDebugDialog by remember { mutableStateOf(false) }
    // 排序菜单
    var showSortMenu by remember { mutableStateOf(false) }

    // Determine required permission based on API level
    val videoPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) // API 33+
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val hasPermission = remember {
        ContextCompat.checkSelfPermission(
            context, videoPermission
        ) == PackageManager.PERMISSION_GRANTED
    }

    var permissionRequested by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRequested = true
        if (granted) {
            permissionDenied = false
            viewModel.loadVideos()
            viewModel.startDiscovery()
        } else {
            permissionDenied = true
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermission) {
            viewModel.loadVideos()
            viewModel.startDiscovery()
        } else if (!permissionRequested) {
            permissionLauncher.launch(videoPermission)
        }
    }

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                TopAppBar(
                    title = {
                        Text(
                            "已选择 ${selectedVideos.size} 项",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectedVideos = emptySet()
                            isMultiSelectMode = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "取消选择")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "本地视频",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (videos.isNotEmpty()) {
                                Text(
                                    "${videos.size} 个视频",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.loadVideos() },
                            enabled = !isLoadingVideos
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "重新扫描",
                                tint = if (isLoadingVideos) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        // 排序入口：点击当前项翻转升降序，点击其他项切换维度
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                enabled = videos.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "排序",
                                    tint = if (videos.isNotEmpty()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    }
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortField.entries.forEach { field ->
                                    val isCurrent = field == sortOption.field
                                    DropdownMenuItem(
                                        text = { Text(field.label) },
                                        trailingIcon = {
                                            if (isCurrent) {
                                                Icon(
                                                    if (sortOption.descending) {
                                                        Icons.Default.ArrowDownward
                                                    } else {
                                                        Icons.Default.ArrowUpward
                                                    },
                                                    contentDescription = if (sortOption.descending) {
                                                        "降序"
                                                    } else {
                                                        "升序"
                                                    },
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            if (isCurrent) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.setSortField(field)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "更多",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("调试日志") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.BugReport,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showOverflow = false
                                        showDebugDialog = true
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (isMultiSelectMode && selectedVideos.isNotEmpty()) {
                MultiSelectBar(
                    count = selectedVideos.size,
                    enabled = devices.isNotEmpty(),
                    onCast = {
                        pendingMultiVideos = selectedVideos.toList()
                        pendingSingleVideo = null
                        showDeviceSheet = true
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 队列横幅：细条 + 呼吸圆点，取代原来的整块红底
            // 注意：currentDevice 是委托属性，无法智能转换，先取局部变量
            val activeDevice = currentDevice
            if (isQueueActive && currentQueueVideo != null && activeDevice != null) {
                QueueBanner(
                    videoName = currentQueueVideo.name,
                    deviceName = activeDevice.name,
                    isPlaying = playbackState == com.airplay.model.PlaybackState.PLAYING,
                    onClick = onNavigateToPlayer
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    permissionDenied -> {
                        EmptyState(
                            icon = Icons.Default.Lock,
                            title = "需要访问视频的权限",
                            description = "请在系统设置中允许 AirPlay 读取媒体文件",
                            actionLabel = "授予权限",
                            onAction = { permissionLauncher.launch(videoPermission) }
                        )
                    }

                    videos.isEmpty() && isLoadingVideos -> {
                        ShimmerGrid()
                    }

                    videos.isEmpty() -> {
                        EmptyState(
                            icon = Icons.Default.VideoLibrary,
                            title = "未找到视频文件",
                            description = "把视频放入设备存储后，点击下方按钮重新扫描",
                            actionLabel = "重新扫描",
                            onAction = { viewModel.loadVideos() },
                            outlined = true
                        )
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = GridHorizontalPadding,
                                end = GridHorizontalPadding,
                                top = 8.dp,
                                bottom = 24.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
                            verticalArrangement = Arrangement.spacedBy(GridSpacing)
                        ) {
                            items(videos, key = { it.id }) { video ->
                                val isSelected = video in selectedVideos
                                val resolvedThumb = thumbnails[video.id]
                                LaunchedEffect(video.id) {
                                    viewModel.resolveThumbnail(video.id)
                                }
                                VideoGridItem(
                                    video = video,
                                    thumbnailUri = resolvedThumb,
                                    isMultiSelectMode = isMultiSelectMode,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            selectedVideos = if (isSelected) {
                                                selectedVideos - video
                                            } else {
                                                selectedVideos + video
                                            }
                                            if (selectedVideos.isEmpty()) {
                                                isMultiSelectMode = false
                                            }
                                        } else {
                                            pendingSingleVideo = video
                                            pendingMultiVideos = null
                                            showDeviceSheet = true
                                        }
                                    },
                                    onLongClick = {
                                        if (isMultiSelectMode) {
                                            if (isSelected && selectedVideos.size == 1) {
                                                selectedVideos = emptySet()
                                                isMultiSelectMode = false
                                                pendingSingleVideo = video
                                                pendingMultiVideos = null
                                                showDeviceSheet = true
                                            }
                                        } else {
                                            isMultiSelectMode = true
                                            selectedVideos = setOf(video)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDebugDialog) {
        DebugLogDialog(onDismiss = { showDebugDialog = false })
    }

    if (showDeviceSheet && (pendingSingleVideo != null || pendingMultiVideos != null)) {
        val subtitle = if (isMultiCast) {
            "已选择 ${pendingMultiVideos!!.size} 个视频，将依次投屏"
        } else {
            "即将播放：${pendingSingleVideo!!.name}"
        }
        DeviceSelectionSheet(
            devices = devices,
            isScanning = isScanning,
            title = "选择投屏设备",
            subtitle = subtitle,
            isMultiCast = isMultiCast,
            onDeviceSelected = { device ->
                showDeviceSheet = false
                if (isMultiCast) {
                    onCastMultiple(pendingMultiVideos!!, device)
                    pendingMultiVideos = null
                    selectedVideos = emptySet()
                    isMultiSelectMode = false
                } else {
                    onCast(pendingSingleVideo!!, device)
                    pendingSingleVideo = null
                }
            },
            onDismiss = {
                showDeviceSheet = false
                pendingSingleVideo = null
                pendingMultiVideos = null
            },
            onRefresh = { viewModel.refreshDiscovery() }
        )
    }
}

// ──────────────────────────────────────────────────────────────
// 队列横幅
// ──────────────────────────────────────────────────────────────

@Composable
private fun QueueBanner(
    videoName: String,
    deviceName: String,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GridHorizontalPadding, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 呼吸圆点表示"正在投屏"
            PulsingDot(active = isPlaying)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPlaying) "正在投屏" else "已暂停",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$videoName · $deviceName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "前往播放页",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PulsingDot(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val dotColor = MaterialTheme.colorScheme.secondary
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(
                color = dotColor.copy(alpha = if (active) 0.18f * pulse else 0.12f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = dotColor.copy(alpha = if (active) pulse else 0.5f),
                    shape = CircleShape
                )
        )
    }
}

// ──────────────────────────────────────────────────────────────
// 多选底部操作条
// ──────────────────────────────────────────────────────────────

@Composable
private fun MultiSelectBar(count: Int, enabled: Boolean, onCast: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GridHorizontalPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "已选择 $count 个视频",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "将按列表顺序依次播放",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onCast,
                enabled = enabled,
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("投屏", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// 空状态 / 权限状态
// ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    outlined: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (outlined) {
            OutlinedButton(onClick = onAction, shape = MaterialTheme.shapes.small) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(actionLabel)
            }
        } else {
            Button(onClick = onAction, shape = MaterialTheme.shapes.small) {
                Text(actionLabel)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// 加载骨架屏
// ──────────────────────────────────────────────────────────────

@Composable
private fun ShimmerGrid() {
    val transition = rememberInfiniteTransition(label = "shimmerGrid")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = GridHorizontalPadding,
            end = GridHorizontalPadding,
            top = 8.dp,
            bottom = 24.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        verticalArrangement = Arrangement.spacedBy(GridSpacing),
        userScrollEnabled = false
    ) {
        items(6) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxWidth(0.7f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
                            )
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// 缩略图加载骨架 / 失败兜底
// ──────────────────────────────────────────────────────────────

@Composable
private fun ShimmerBackground() {
    val density = LocalDensity.current
    // 用 dp 换算滑光宽度，避免不同 DPI 下滑光速度不一致
    val travel = with(density) { 600.dp.toPx() }
    val transition = rememberInfiniteTransition(label = "shimmerThumb")
    val translateX by transition.animateFloat(
        initialValue = -travel,
        targetValue = travel * 2,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerSlide"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    startX = translateX,
                    endX = translateX + travel
                )
            )
    )
}

@Composable
private fun ErrorFallback() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.size(30.dp)
        )
    }
}

// ──────────────────────────────────────────────────────────────
// 视频卡片：图片在上，标题在下，信息层级清晰
// ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoGridItem(
    video: VideoItem,
    thumbnailUri: Uri?,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.medium
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(180),
        label = "cardBorder"
    )
    val titleColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(180),
        label = "titleColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                key(thumbnailUri) {
                    var failed by remember { mutableStateOf(false) }

                    if (!failed) ShimmerBackground()

                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(thumbnailUri ?: video.thumbnailUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = video.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { failed = true }
                    )

                    if (failed) ErrorFallback()
                }

                // 仅底部轻渐变，保证时长胶囊可读
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )

                // 时长胶囊
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.66f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = video.durationFormatted,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // 选中态：单层浅色蒙层 + 右上角对勾（不再叠加两层 overlay）
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "已选中",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // 标题移到图片下方，长文件名不再压在图上看不清
            // 单行 + 省略号：2 列布局下每列仅约 190dp，两行会把扩展名拆成孤立的一行
            Text(
                text = video.name,
                style = MaterialTheme.typography.bodySmall,
                color = titleColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 34.dp)
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────
// 调试日志弹窗
// ──────────────────────────────────────────────────────────────

@Composable
private fun DebugLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var logText by remember { mutableStateOf(LogBuffer.getLogText()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调试日志") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { logText = LogBuffer.getLogText() },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("刷新", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(
                                Context.CLIPBOARD_SERVICE
                            ) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("AirPlay Logs", logText)
                            )
                            Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("复制", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            LogBuffer.clear()
                            logText = ""
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("清除", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = logText.ifEmpty { "（暂无日志）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

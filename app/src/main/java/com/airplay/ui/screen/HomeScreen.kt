package com.airplay.ui.screen

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
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
import com.airplay.util.LogBuffer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(),
    onCast: (VideoItem, CastDevice) -> Unit,
    onCastMultiple: (List<VideoItem>, CastDevice) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val videos by viewModel.videos.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val playQueue by viewModel.playQueue.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val currentDevice by viewModel.currentDevice.collectAsState()

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

    // Debug dialog
    var showDebugDialog by remember { mutableStateOf(false) }

    // Determine required permission based on API level
    val videoPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) // API 33+
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Check if permission is already granted
    val hasPermission = remember {
        ContextCompat.checkSelfPermission(
            context, videoPermission
        ) == PackageManager.PERMISSION_GRANTED
    }

    var permissionRequested by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    // Permission request launcher
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

    // Grant permission and load on first composition if already granted
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
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectedVideos = emptySet()
                            isMultiSelectMode = false
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "取消选择",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            "本地视频",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.refreshDiscovery() },
                            enabled = !isScanning
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "重新搜索",
                                tint = if (isScanning) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        IconButton(onClick = { showDebugDialog = true }) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = "调试日志",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (isMultiSelectMode && selectedVideos.isNotEmpty()) {
                Surface(
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "已选择 ${selectedVideos.size} 个视频",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = {
                                if (devices.isNotEmpty()) {
                                    pendingMultiVideos = selectedVideos.toList()
                                    pendingSingleVideo = null
                                    showDeviceSheet = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("投屏选中 (${selectedVideos.size})")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Queue banner — between TopAppBar and grid
            if (isQueueActive && currentQueueVideo != null && currentDevice != null) {
                Card(
                    onClick = { /* TODO: navigate to player */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "正在播放",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                "${currentQueueVideo.name} → ${currentDevice!!.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "前往播放",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Main content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    // Permission denied
                    permissionDenied -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "需要访问视频文件的权限",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "请在设置中允许访问媒体文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { permissionLauncher.launch(videoPermission) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("授予权限")
                            }
                        }
                    }

                    // First-time loading shimmer
                    videos.isEmpty() && isScanning -> {
                        ShimmerGrid()
                    }

                    // No videos found after scan
                    videos.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "未找到视频文件",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "将视频文件放入设备存储中即可显示",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.loadVideos()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("重新扫描")
                            }
                        }
                    }

                    // Video grid
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = 8.dp,
                                bottom = 8.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(videos, key = { it.id }) { video ->
                                val isSelected = video in selectedVideos
                                VideoGridItem(
                                    video = video,
                                    isMultiSelectMode = isMultiSelectMode,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            // Toggle selection
                                            selectedVideos = if (isSelected) {
                                                selectedVideos - video
                                            } else {
                                                selectedVideos + video
                                            }
                                            if (selectedVideos.isEmpty()) {
                                                isMultiSelectMode = false
                                            }
                                        } else {
                                            // Single tap → show device sheet
                                            pendingSingleVideo = video
                                            pendingMultiVideos = null
                                            showDeviceSheet = true
                                        }
                                    },
                                    onLongClick = {
                                        if (isMultiSelectMode) {
                                            if (isSelected && selectedVideos.size == 1) {
                                                // Long press on the only selected item → exit multi-select, cast this item
                                                selectedVideos = emptySet()
                                                isMultiSelectMode = false
                                                pendingSingleVideo = video
                                                pendingMultiVideos = null
                                                showDeviceSheet = true
                                            }
                                            // If multiple selected, long-press does nothing
                                        } else {
                                            // Enter multi-select mode
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

    // ── Debug Log Dialog ──
    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = { Text("调试日志") },
            text = {
                var logText by remember { mutableStateOf(LogBuffer.getLogText()) }
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(onClick = {
                            logText = LogBuffer.getLogText()
                        }) {
                            Text("刷新")
                        }
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(
                                android.content.Context.CLIPBOARD_SERVICE
                            ) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("AirPlay Logs", logText)
                            )
                            Toast.makeText(
                                context, "日志已复制到剪贴板", Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Text("复制日志")
                        }
                        OutlinedButton(onClick = {
                            LogBuffer.clear()
                            logText = ""
                        }) {
                            Text("清除")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = logText.ifEmpty { "（暂无日志）" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDebugDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // ── Device Selection Bottom Sheet ──
    if (showDeviceSheet && (pendingSingleVideo != null || pendingMultiVideos != null)) {
        val subtitle = if (isMultiCast) {
            "已选择 ${pendingMultiVideos!!.size} 个视频，将依次投屏"
        } else {
            "正在播放: ${pendingSingleVideo!!.name}"
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
// Shimmer loading grid
// ──────────────────────────────────────────────────────────────

@Composable
private fun ShimmerGrid() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmerGrid")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(8) {
            Card(
                modifier = Modifier.aspectRatio(16f / 9f),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = alpha
                    )
                )
            ) { /* empty placeholder */ }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Video grid item card (Netflix-style)
// ──────────────────────────────────────────────────────────────

// ──────────────────────────────────────────────────────────────
// Shimmer loading placeholder for thumbnail
// ──────────────────────────────────────────────────────────────

@Composable
private fun ShimmerBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmerThumb")
    val translateX by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
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
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.surfaceVariant
                    ),
                    startX = translateX,
                    endX = translateX + 600f
                )
            )
    )
}

// ──────────────────────────────────────────────────────────────
// Error fallback for broken thumbnails
// ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorFallback() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(32.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoGridItem(
    video: VideoItem,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "selectedAlpha"
    )

    var thumbnailFailed by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.aspectRatio(16f / 9f)) {
            // Thumbnail with shimmer placeholder
            if (!thumbnailFailed) {
                ShimmerBackground()
            }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(video.thumbnailUri)
                    .crossfade(true)
                    .build(),
                contentDescription = video.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop,
                onError = { thumbnailFailed = true }
            )

            // Error fallback (shown when thumbnail can't be loaded)
            if (thumbnailFailed) {
                ErrorFallback()
            }

            // Gradient overlay at the bottom for text readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Multi-select overlay (animated)
            if (selectedAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f * selectedAlpha)
                        )
                        .clip(MaterialTheme.shapes.medium)
                )
            }

            // Selected state border overlay
            if (selectedAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f * selectedAlpha)
                                )
                            )
                        )
                )
            }

            // Video title — overlaid on gradient
            Text(
                text = video.name,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )

            // Duration pill — bottom-right corner (YouTube style)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp)
            ) {
                Text(
                    text = video.durationFormatted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Multi-select checkbox — top-left corner
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = Color.White.copy(alpha = 0.8f),
                        checkmarkColor = Color.White
                    )
                )
            }
        }
    }
}

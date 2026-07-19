package com.airplay.ui.screen

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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

    var showDeviceSheet by remember { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }

    // Multi-select state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedVideos by remember { mutableStateOf(setOf<VideoItem>()) }

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
    var showDebugDialog by remember { mutableStateOf(false) }

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
                    title = { Text("已选择 ${selectedVideos.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectedVideos = emptySet()
                            isMultiSelectMode = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "取消")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("AirPlay") },
                    actions = {
                        IconButton(
                            onClick = { viewModel.refreshDiscovery() },
                            enabled = !isScanning
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "重新搜索")
                        }
                        IconButton(onClick = { showDebugDialog = true }) {
                            Icon(Icons.Default.BugReport, contentDescription = "调试日志")
                        }
                    }
                )
            }
        },
        bottomBar = {
            Column {
                // Queue status banner
                if (isQueueActive && currentQueueVideo != null && currentDevice != null) {
                    Card(
                        onClick = { /* TODO: navigate to player */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "正在播放",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    "${currentQueueVideo.name} → ${currentDevice!!.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Default.ArrowForward, contentDescription = "前往播放")
                        }
                    }
                }

                // Multi-select bottom bar
                if (isMultiSelectMode && selectedVideos.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "已选择 ${selectedVideos.size} 个视频",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(onClick = {
                                if (devices.isNotEmpty()) {
                                    showDeviceSheet = true
                                }
                            }) {
                                Text("投屏选中 (${selectedVideos.size})")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Device section
            Text(
                "可投屏设备",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Text(
                    "正在搜索设备…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (devices.isEmpty() && !isScanning) {
                Text(
                    "未找到可投屏的设备",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Text(
                    "请确保：\n• 手机和电视连接同一 WiFi\n• 电视已开启 DLNA/投屏功能\n• 路由器未开启 AP 隔离（部分小米路由器默认开启）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.refreshDiscovery() },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重新搜索")
                }
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 120.dp)
            ) {
                items(devices) { device ->
                    DeviceItem(device)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Video list section
            Text(
                "本地视频",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (permissionDenied) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "需要访问视频文件的权限",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { permissionLauncher.launch(videoPermission) }) {
                            Text("授予权限")
                        }
                    }
                }
            } else if (videos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "未找到视频文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn {
                    items(videos, key = { it.id }) { video ->
                        val isSelected = video in selectedVideos
                        VideoListItem(
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
                                    selectedVideo = video
                                    if (devices.isNotEmpty()) {
                                        showDeviceSheet = true
                                    }
                                }
                            },
                            onLongClick = {
                                if (isMultiSelectMode && isSelected) {
                                    // Long press on already selected item → cast just this item
                                    selectedVideos = emptySet()
                                    isMultiSelectMode = false
                                    selectedVideo = video
                                    if (devices.isNotEmpty()) {
                                        showDeviceSheet = true
                                    }
                                } else {
                                    // Enter multi-select mode, select this item
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
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("AirPlay Logs", logText))
                            Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
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

    // Device selection bottom sheet
    val multiCastVideoList = if (isMultiSelectMode && selectedVideos.isNotEmpty()) {
        selectedVideos.toList()
    } else {
        emptyList()
    }
    val isMultiCast = multiCastVideoList.isNotEmpty()

    if (showDeviceSheet && (selectedVideo != null || isMultiCast)) {
        ModalBottomSheet(onDismissRequest = {
            showDeviceSheet = false
            if (!isMultiCast) {
                selectedVideo = null
            }
        }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "选择投屏设备",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (isMultiCast) {
                    Text(
                        "已选择 ${selectedVideos.size} 个视频, 将依次投屏",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else if (selectedVideo != null) {
                    Text(
                        "正在播放: ${selectedVideo!!.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                devices.forEach { device ->
                    DeviceSelectionItem(
                        device = device,
                        onClick = {
                            showDeviceSheet = false
                            if (isMultiCast) {
                                onCastMultiple(multiCastVideoList, device)
                                selectedVideos = emptySet()
                                isMultiSelectMode = false
                            } else if (selectedVideo != null) {
                                onCast(selectedVideo!!, device)
                                selectedVideo = null
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DeviceItem(device: CastDevice) {
    ListItem(
        headlineContent = { Text(device.name) },
        supportingContent = { Text(device.ipAddress, style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            Icon(Icons.Default.Cast, contentDescription = null)
        },
        modifier = Modifier.heightIn(min = 48.dp)
    )
}

@Composable
private fun DeviceSelectionItem(device: CastDevice, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(device.name) },
        supportingContent = { Text(device.ipAddress) },
        leadingContent = {
            Icon(Icons.Default.Cast, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoListItem(
    video: VideoItem,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                video.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(video.durationFormatted)
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                AsyncImage(
                    model = video.thumbnailUri,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    contentScale = ContentScale.Crop
                )
            }
        },
        trailingContent = {
            if (!isMultiSelectMode) {
                Icon(Icons.Default.Cast, contentDescription = "投屏")
            }
        },
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    )
}

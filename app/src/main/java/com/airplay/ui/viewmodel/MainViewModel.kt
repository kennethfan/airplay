package com.airplay.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airplay.AirPlayApp
import com.airplay.dlna.DeviceDiscoveryManager
import com.airplay.dlna.MediaRenderManager
import com.airplay.dlna.StreamServerManager
import com.airplay.model.CastDevice
import com.airplay.model.PlaybackState
import com.airplay.model.VideoItem
import com.airplay.util.LogBuffer
import com.airplay.util.VideoScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AirPlayApp
    private val discoveryManager: DeviceDiscoveryManager = app.getDiscoveryManager()
    val streamServer: StreamServerManager = app.getStreamServer()

    companion object {
        private const val PREFS_NAME = "airplay_queue"
        private const val POLL_INTERVAL_MS = 5000L
        private const val STUCK_THRESHOLD = 3 // 3 * 5s = 15s stuck before skip
    }

    // Video list
    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    // Cast devices
    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    // Discovery state
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Selected video for playback
    private val _selectedVideo = MutableStateFlow<VideoItem?>(null)
    val selectedVideo: StateFlow<VideoItem?> = _selectedVideo.asStateFlow()

    // Playback state
    private val _playbackState = MutableStateFlow(PlaybackState.STOPPED)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    // Currently casting to device
    private val _currentDevice = MutableStateFlow<CastDevice?>(null)
    val currentDevice: StateFlow<CastDevice?> = _currentDevice.asStateFlow()

    // ---- Queue state ----

    private val _playQueue = MutableStateFlow<List<VideoItem>>(emptyList())
    val playQueue: StateFlow<List<VideoItem>> = _playQueue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    val currentQueueVideo: VideoItem?
        get() {
            val idx = _currentIndex.value
            val queue = _playQueue.value
            return if (idx in queue.indices) queue[idx] else null
        }

    val isQueueActive: Boolean
        get() = _playQueue.value.isNotEmpty() && _currentIndex.value >= 0

    private var renderManager: MediaRenderManager? = null
    private var pollingJob: Job? = null
    private var lastPositionMs: Long = -1L
    private var stuckCount: Int = 0

    private val discoveryListener = object : DeviceDiscoveryManager.DiscoveryListener {
        override fun onDeviceFound(device: CastDevice) {
            _devices.value = _devices.value + device
            _isScanning.value = false
        }

        override fun onDeviceLost(device: CastDevice) {
            _devices.value = _devices.value - device
        }

        override fun onDiscoveryError(message: String) {
            LogBuffer.e("MainViewModel", "Discovery error: $message")
            _isScanning.value = false
        }
    }

    init {
        discoveryManager.addListener(discoveryListener)
    }

    fun loadVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            val results = VideoScanner.scanVideos(getApplication())
            _videos.value = results
            tryRestoreQueue()
        }
    }

    fun startDiscovery() {
        discoveryManager.startDiscovery()
        _isScanning.value = true
    }

    fun refreshDiscovery() {
        discoveryManager.searchNow()
        _isScanning.value = true
    }

    fun stopDiscovery() {
        discoveryManager.stopDiscovery()
        _isScanning.value = false
    }

    // ── Casting ──

    /** Cast a single video (legacy entry point). */
    fun castVideo(video: VideoItem, device: CastDevice) {
        castVideos(listOf(video), device)
    }

    /** Cast a list of videos and start playback from the first. */
    fun castVideos(videos: List<VideoItem>, device: CastDevice) {
        _playQueue.value = videos.toList()
        _currentIndex.value = 0
        _currentDevice.value = device
        playCurrent()
    }

    /** Play the item at _currentIndex. */
    private fun playCurrent() {
        pollingJob?.cancel()
        pollingJob = null

        val idx = _currentIndex.value
        val queue = _playQueue.value
        if (idx !in queue.indices) return
        val video = queue[idx]

        _selectedVideo.value = video
        val streamUrl = streamServer.buildStreamUrl(video.path)
        val metadata = buildDidlMetadata(video.name)
        LogBuffer.i("MainViewModel", "playCurrent[${idx + 1}/${queue.size}]: ${video.name} url=$streamUrl")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val upnpService = discoveryManager.getUpnpService()
                if (upnpService == null) {
                    LogBuffer.e("MainViewModel", "playCurrent failed: UPnP service not initialized")
                    _playbackState.value = PlaybackState.STOPPED
                    return@launch
                }
                val device = _currentDevice.value
                if (device == null) {
                    LogBuffer.e("MainViewModel", "playCurrent failed: no device")
                    _playbackState.value = PlaybackState.STOPPED
                    return@launch
                }
                val manager = MediaRenderManager(upnpService, device)
                renderManager = manager
                _playbackState.value = PlaybackState.BUFFERING
                manager.playUri(streamUrl, metadata)
                delay(3000)
                startPlaybackPolling()
            } catch (e: Exception) {
                LogBuffer.e("MainViewModel", "playCurrent failed", e)
                _playbackState.value = PlaybackState.STOPPED
            }
        }
    }

    // ── Queue control ──

    fun playNext() {
        stopInternal()
        val nextIdx = _currentIndex.value + 1
        if (nextIdx >= _playQueue.value.size) {
            LogBuffer.d("MainViewModel", "Queue finished")
            _playbackState.value = PlaybackState.STOPPED
            return
        }
        _currentIndex.value = nextIdx
        playCurrent()
    }

    fun playPrevious() {
        stopInternal()
        val prevIdx = _currentIndex.value - 1
        if (prevIdx < 0) return
        _currentIndex.value = prevIdx
        playCurrent()
    }

    fun skipTo(index: Int) {
        if (index !in _playQueue.value.indices) return
        stopInternal()
        _currentIndex.value = index
        playCurrent()
    }

    fun addToQueue(videos: List<VideoItem>) {
        val current = _playQueue.value.toMutableList()
        current.addAll(videos)
        _playQueue.value = current
        saveQueue()
    }

    fun removeFromQueue(index: Int) {
        val current = _playQueue.value.toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        val ci = _currentIndex.value
        when {
            current.isEmpty() -> {
                stopInternal()
                _playQueue.value = emptyList()
                _currentIndex.value = -1
                _selectedVideo.value = null
                saveQueue()
                return
            }
            index < ci -> _currentIndex.value = ci - 1
            index == ci -> {
                val newIdx = minOf(ci, current.size - 1)
                _currentIndex.value = newIdx
                _playQueue.value = current
                playCurrent()
                return
            }
        }
        _playQueue.value = current
        saveQueue()
    }

    fun moveInQueue(from: Int, to: Int) {
        val current = _playQueue.value.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        val item = current.removeAt(from)
        current.add(to, item)
        _playQueue.value = current
        val ci = _currentIndex.value
        _currentIndex.value = when {
            from == ci -> to
            from < ci && to >= ci -> ci - 1
            from > ci && to <= ci -> ci + 1
            else -> ci
        }
        saveQueue()
    }

    /** Toggle pause/play on the current renderer. */
    fun togglePlayPause() {
        when (_playbackState.value) {
            PlaybackState.PLAYING -> renderManager?.pause()
            PlaybackState.PAUSED -> renderManager?.play()
            else -> {}
        }
    }

    /** Stop playing but keep the queue intact. */
    fun stopCasting() {
        stopInternal()
        _playbackState.value = PlaybackState.STOPPED
        // keep _playQueue and _currentIndex so UI can show "stopped" state
    }

    /** Full stop: clear queue, reset everything. */
    fun stopAndClearQueue() {
        stopInternal()
        _playQueue.value = emptyList()
        _currentIndex.value = -1
        _selectedVideo.value = null
        _currentDevice.value = null
        renderManager = null
        _playbackState.value = PlaybackState.STOPPED
        saveQueue()
    }

    fun seekTo(positionMs: Long) {
        renderManager?.seek(positionMs)
    }

    private fun stopInternal() {
        pollingJob?.cancel()
        pollingJob = null
        renderManager?.stop()
        lastPositionMs = -1L
        stuckCount = 0
    }

    // ── Playback polling for auto-next ──

    private fun startPlaybackPolling() {
        pollingJob?.cancel()
        lastPositionMs = -1L
        stuckCount = 0
        var bufferingCount = 0
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(POLL_INTERVAL_MS)
                val rm = renderManager ?: break
                val state = _playbackState.value

                // If still BUFFERING, try to detect when playback actually starts
                if (state == PlaybackState.BUFFERING) {
                    bufferingCount++
                    if (bufferingCount > 12) { // 60s timeout on buffering
                        LogBuffer.w("MainViewModel", "Buffering timeout -> trying next")
                        playNext()
                        break
                    }
                    val testPos = rm.getPositionMs()
                    if (testPos >= 0) {
                        _playbackState.value = PlaybackState.PLAYING
                        lastPositionMs = testPos
                        LogBuffer.d("MainViewModel", "Playback detected at ${testPos}ms")
                    }
                    continue
                }

                if (state != PlaybackState.PLAYING) continue

                val pos = rm.getPositionMs()
                if (pos < 0) {
                    LogBuffer.d("MainViewModel", "getPositionMs returned -1, retrying")
                    continue
                }

                if (pos == lastPositionMs) {
                    stuckCount++
                    if (stuckCount >= STUCK_THRESHOLD) {
                        LogBuffer.d("MainViewModel", "Playback stuck at ${pos}ms after ${STUCK_THRESHOLD * POLL_INTERVAL_MS / 1000}s -> skip")
                        playNext()
                        break
                    }
                } else {
                    stuckCount = 0
                    val video = _playQueue.value.getOrNull(_currentIndex.value)
                    if (video != null && video.durationMs > 0 && pos + 3000 >= video.durationMs) {
                        LogBuffer.d("MainViewModel", "Near end (${pos}ms/${video.durationMs}ms), waiting 3s then next")
                        delay(3000)
                        playNext()
                        break
                    }
                }
                lastPositionMs = pos
            }
        }
    }

    override fun onCleared() {
        discoveryManager.removeListener(discoveryListener)
        stopDiscovery()
        stopCasting()
        saveQueue()
        super.onCleared()
    }

    // ── DIDL metadata ──

    private fun buildDidlMetadata(title: String): String {
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"><item id=\"0\" parentID=\"-1\" restricted=\"0\"><dc:title>$title</dc:title><upnp:class>object.item.videoItem</upnp:class></item></DIDL-Lite>"
    }

    // ── Queue persistence ──

    private fun saveQueue() {
        try {
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val paths = _playQueue.value.map { it.path }
            val json = JSONArray(paths).toString()
            prefs.edit().putString("queue_paths", json).putInt("queue_index", _currentIndex.value).apply()
        } catch (e: Exception) {
            LogBuffer.e("MainViewModel", "saveQueue failed", e)
        }
    }

    private fun tryRestoreQueue() {
        try {
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString("queue_paths", "[]") ?: "[]"
            val savedIndex = prefs.getInt("queue_index", -1)
            val savedPaths = mutableListOf<String>()
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                savedPaths.add(arr.getString(i))
            }
            if (savedPaths.isEmpty()) return
            val restored = savedPaths.mapNotNull { path -> _videos.value.find { it.path == path } }
            if (restored.isNotEmpty()) {
                _playQueue.value = restored
                _currentIndex.value = minOf(savedIndex, restored.size - 1)
                LogBuffer.d("MainViewModel", "Restored queue with ${restored.size} videos, index=${_currentIndex.value}")
            }
        } catch (e: Exception) {
            LogBuffer.e("MainViewModel", "tryRestoreQueue failed", e)
        }
    }
}

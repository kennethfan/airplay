[**English**](README.en.md) | [中文](README.md)

# AirPlay — Android DLNA/AirPlay Video Casting

Cast local videos from your phone to UPnP/DLNA media renderers (smart TVs, set-top boxes, etc.).

## Features

- **Local video scanning** — Automatically scan video files on device, display by title/duration
- **DLNA device discovery** — Auto-discover UPnP media renderers on the local network
- **Video casting** — Push local videos to TV with play/pause/seek/volume control
- **HTTP streaming** — Built-in NanoHTTPD server for real-time transcoding and streaming
- **Batch multi-select** — Select multiple videos and add them to the playback queue
- **Playback queue** — Queue management, auto-next, and progress persistence
- **Background playback** — Foreground service with notification controls, continues playing while locked

## Quick Start

```bash
git clone https://github.com/your-username/airplay.git
cd airplay
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Requirements

| Tool | Version |
|------|---------|
| Android Gradle Plugin | 8.7.0 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 34 |
| minSdk | 26 |
| Java | 17+ |

## Usage

1. Install the APK and grant file read permission
2. The app automatically scans local videos and displays them on the home screen
3. Tap the FAB (bottom-right) to refresh the device list, select your target TV
4. Tap a video card to start casting
5. Long-press a video card to enter multi-select mode for batch casting

> **Note**: Some TVs (e.g., Koomii on port 7300) may have compatibility issues; prefer renderers using port 38400.

## Tech Stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose + Material 3 |
| Playback | Activity + Foreground Service |
| DLNA/UPnP | jUPnP (AndroidUpnpServiceConfiguration) |
| HTTP Streaming | NanoHTTPD |
| Image Loading | Coil (coil-compose) |
| Video Thumbnails | MediaStore.Thumbnails + cached fallback |

## Architecture

```
AirPlayApp (Application)
  ├── DeviceDiscoveryManager — UPnP device discovery
  ├── MediaRenderManager — AVTransport commands
  ├── StreamServerManager — NanoHTTPD streaming server
  ├── MainViewModel — queue state + polling + persistence
  ├── VideoScanner — MediaStore video scanning
  └── CastForegroundService — background casting notification
```

## License

[Apache 2.0](LICENSE)

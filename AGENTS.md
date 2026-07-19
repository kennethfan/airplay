# PROJECT KNOWLEDGE BASE

**Generated:** 2026-07-19
**Commit:** ee44d94
**Branch:** master

## OVERVIEW

Android DLNA/AirPlay app that casts local videos to UPnP media renderers. Single-module, Java + Kotlin, Jetpack Compose UI, NanoHTTPD streaming server.

## STRUCTURE

```
airplay/
├── app/src/main/java/com/airplay/
│   ├── AirPlayApp.java         # Application class — starts discovery + HTTP server
│   ├── MainActivity.kt         # Single Activity, Compose NavHost (home → player)
│   ├── dlna/                   # UPnP device discovery + media rendering + streaming
│   ├── model/                  # Java POJOs: VideoItem, CastDevice, PlaybackState
│   ├── service/                # CastForegroundService — background playback
│   ├── ui/
│   │   ├── screen/             # HomeScreen.kt, PlayerScreen.kt (Compose)
│   │   ├── viewmodel/          # MainViewModel.kt — queue + polling + persistence
│   │   └── theme/              # AirPlayTheme.kt (Material3, blue palette)
│   └── util/                   # LogBuffer.kt, VideoScanner.java
├── docs/agents/                # Agent skill config (issue-tracker, triage-labels)
├── build.gradle / app/build.gradle  # Groovy DSL, AGP 8.7, Kotlin 2.0.21
└── settings.gradle
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| UPnP discovery + registry | `dlna/DeviceDiscoveryManager.java` | `getUpnpService()` exposed for rendering |
| Media rendering actions | `dlna/MediaRenderManager.java` | SetAVTransportURI, Play, Pause, Stop, Seek, SetVolume |
| HTTP video streaming | `dlna/StreamServerManager.java` | NanoHTTPD, range-request aware |
| Queue state + polling | `ui/viewmodel/MainViewModel.kt` | `_playQueue`, polling coroutine, persistence |
| UI screens | `ui/screen/` | HomeScreen (file list + multi-select), PlayerScreen (controls + queue) |
| Agent skill config | `docs/agents/` | issue-tracker, triage-labels, domain docs |

## CODE MAP (key symbols)

| Symbol | Type | Location | Refs | Role |
|--------|------|----------|------|------|
| AirPlayApp | class | AirPlayApp.java | — | App init, owns managers |
| MainActivity | class | MainActivity.kt | — | NavHost, device sheet, cast flow |
| DeviceDiscoveryManager | class | dlna/ | >10 | UPnP device discovery, shares UpnpService |
| MediaRenderManager | class | dlna/ | ~5 | AVTransport commands to renderer |
| StreamServerManager | class | dlna/ | ~3 | Local HTTP video streaming |
| MainViewModel | class | ui/viewmodel/ | 2 | Queue state, polling, persistence |
| VideoQueuePersistence | class | ui/viewmodel/ | 1 | SharedPrefs JSON persistence |
| LogBuffer | object | util/ | ~50 | In-memory debug log (JvmStatic) |
| CastForegroundService | class | service/ | 1 | Background casting + notification |

## CONVENTIONS

- **Language**: Java for infrastructure (DLNA, models, scanner), Kotlin for UI + ViewModel
- **DI**: None — manual `(application as AirPlayApp)` cast from Activity
- **Persisted queue**: SharedPreferences JSONArray (video paths + current index)
- **Models**: Java POJOs with getters (`VideoItem.java`, `CastDevice.java`)
- **Playback detection**: Poll GetPositionInfo every 5s (15s stuck timeout, 60s BUFFERING limit)
- **UPnP**: jUPnP library via AndroidUpnpServiceConfiguration
- **Build**: `./gradlew assembleDebug`
- **Strings**: Simplified Chinese (zh-CN)

## ANTI-PATTERNS (THIS PROJECT)

- `as Any?` cast in Kotlin: `application as AirPlayApp` — crash if unexpected type
- Force-unwrap: `currentDevice!!.name` — potential NPE
- Silent catch blocks: 4 empty/comment-only catches (DeviceDiscoveryManager.java:129,208,230; UpnpServiceHolder.java:27)
- No nullability annotations on Java side — crossing to Kotlin risks NPE
- No version catalog — hardcoded dependency versions in app/build.gradle
- No tests — test directory exists but empty
- No CI/CD — no GitHub Actions or equivalent

## COMMANDS

```bash
./gradlew assembleDebug                          # Build debug APK
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## NOTES

- Dead code: `UpnpServiceHolder.java` — kept as fallback but unused (DeviceDiscoveryManager now shares its UpnpService)
- 酷喵电视_海信(B9) (port 7300) rejects SetAVTransportURI with 500 — use 客厅电视 (port 38400) instead
- Auto-next may miss the end on some renderers; could switch to LastChange event subscription
- HomeScreen.kt (524 lines) and PlayerScreen.kt (515 lines) are oversized — consider splitting
- Bug: PlayerScreen AddToQueueSheet does O(N²) `item.path in queue.map { it.path }` — use a precomputed set

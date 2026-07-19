# dlna — UPnP Device Discovery, Media Rendering & Streaming

**Modified:** 2026-07-19

## OVERVIEW

UPnP protocol layer — discovers DLNA media renderers on the LAN, controls AVTransport on selected devices, and serves local video files via an embedded HTTP server.

## FILES

| File | LOC | Role |
|------|-----|------|
| `DeviceDiscoveryManager.java` | 255 | UPnP discovery via jUPnP, AndroidUpnpServiceConfig, periodic M-SEARCH |
| `MediaRenderManager.java` | 341 | AVTransport commands: SetAVTransportURI, Play, Pause, Stop, Seek, GetPositionInfo, SetVolume |
| `StreamServerManager.java` | 246 | NanoHTTPD on port 8899, range-request aware streaming for file:// and content:// URIs |
| `UpnpServiceHolder.java` | 31 | **Dead code** — kept as fallback but unused (see NOTES) |

## KEY PATTERNS

- `DeviceDiscoveryManager` creates and shares a single `UpnpService` via `getUpnpService()`
- `MediaRenderManager` finds the device in the shared registry by UDN, then invokes actions via `ActionInvocation` + `ActionCallback`
- All UPnP actions (SetAVTransportURI, Play, etc.) follow the same boilerplate: find service → create invocation → set inputs → execute callback
- `StreamServerManager` extends NanoHTTPD; `serve()` handles range headers for seeking, supports both file paths and content:// URIs (Android 10+ scoped storage)

## CONVENTIONS

- All `InstanceID` inputs use integer `0` (DLNA convention)
- Time format for Seek: `HH:MM:SS` (UPnP REL_TIME)
- `getPositionMs()` uses `CountDownLatch` with 3s timeout (synchronous wrapper around async UPnP)
- Volume range: 0-100 (RenderingControl:1 spec)
- Streaming MIME types in `getMimeType()` — 8 video formats mapped

## ANTI-PATTERNS

- 3 silent catch blocks (`DeviceDiscoveryManager.java:129,208,230`) — exception swallowed
- `findService()` iterates ALL remote devices on every call — could cache the matched service
- UPnP action boilerplate repeated 7 times in `MediaRenderManager` — candidate for helper/utility class
- `FileStream` and `ContentUriStream` are nearly identical — could be unified
- Range-request logic duplicated for content URI vs file path branches in `StreamServerManager.serve()`

## NOTES

- `UpnpServiceHolder` is dead code — previously used to hold a separate `UpnpService` instance, but `DeviceDiscoveryManager` now shares its own instance via `getUpnpService()`. Kept in case discovery needs to be bypassed.
- 酷喵电视_海信(B9) (port 7300) returns HTTP 500 on SetAVTransportURI — try 客厅电视 (port 38400)
- `MediaRenderManager` keeps its own `currentState` field that may drift from ViewModel's `_playbackState`

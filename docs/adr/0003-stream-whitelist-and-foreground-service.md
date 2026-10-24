# ADR 0003: 流服务路径白名单与前台服务按需启停

**状态**: 已接受  
**日期**: 2026-10-24  
**版本**: v0.2.0

## 背景

代码审查发现两个问题：

1. **`/stream` 无访问控制**。`StreamServerManager.serve()` 直接 `new File(path)` 返回文件，同网段任何设备都能构造 `http://手机IP:8899/stream?path=/data/data/com.airplay/shared_prefs/airplay_queue.xml` 读取 App 私有文件。
2. **前台服务无条件启动**。`AirPlayApp.onCreate()` 调用 `CastForegroundService.start(this)`，App 一打开就常驻通知栏并持有 4 小时 wake lock，即使根本没有投屏；且 targetSdk 34 下从 `Application` 启动 FGS 受限。

## 决策

### 1. 路径白名单（精确匹配）

只有通过 `buildStreamUrl(String)` 投屏过的路径才会进入 `allowedPaths`，`serve()` 只服务已登记路径，其余返回 **403 Forbidden**。

```java
private final Set<String> allowedPaths = Collections.newSetFromMap(
    new LinkedHashMap<String, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_ALLOWED_PATHS;   // 200
        }
    });
```

- `buildStreamUrl()` 内 `allowedPaths.add(videoPath)` 登记
- `serve()` 内 `allowedPaths.contains(path)` 校验
- LRU 上限 200 条，防止集合无限增长

**关键约束：只做精确匹配，绝不对 `path` 做二次 URL 解码。** 二次解码会让 `%252F` 这类双重编码绕过白名单——实现过程中曾加过 `Uri.decode(path)` 兼容分支，经识别属于自开后门，已撤销并在代码注释中说明原因。

### 2. 前台服务按需启停

| 时机 | 动作 |
|---|---|
| `castVideos()` 用户主动投屏 | `startCastService()` |
| `stopCasting()` / `stopAndClearQueue()` | `stopCastService()` |
| 队列播完（`playNext()` 越界） | `stopCastService()` |
| 自动连播 | **不调用**（服务已在运行，避免后台启动 FGS 异常） |

`ViewModel` 用 `isCastServiceRunning` 布尔量避免重复 start/stop。

`CastForegroundService.stop()` 从 `startService(ACTION_STOP)` 改为 `stopService()`——原写法在 App 处于后台时会抛 `IllegalStateException`，资源释放交由 `onDestroy()` 处理。同时删除已无用的 `ACTION_STOP` 分支。

## 考量

### 备选方案（路径控制）

| 方案 | 优点 | 缺点 |
|---|---|---|
| **白名单精确匹配（采用）** | 不改 URL 格式，对正常投屏零影响 | 进程重启后集合清空，需重新投屏才恢复 |
| 一次性 token 拼进 URL | 无需维护集合 | 进程重启后 token 失效，正在播放的视频会中断 |
| 限定路径前缀（如只允许 `/storage/`） | 实现最简单 | 仍可读取存储卡上任意文件，防护不足 |
| 不做校验 | 无改动 | 局域网任意设备可读私有文件 |

选择白名单的核心理由：**不影响正在播放的投屏会话**。token 方案虽更严格，但进程被回收后会让电视端拉流中断，用户体验代价过大。

### 备选方案（前台服务）

| 方案 | 缺点 |
|---|---|
| **按需启停（采用）** | 需在多个退出点补 stop 调用 |
| 保持 Application 无条件启动 | 常驻通知 + 无谓耗电，且违反 FGS 后台启动限制 |
| 完全不用前台服务 | 锁屏后进程易被回收，投屏中断 |

## 影响

- `StreamServerManager.java`：新增 `allowedPaths` 字段、`buildStreamUrl()` 登记、`serve()` 校验
- `AirPlayApp.java`：移除 `CastForegroundService.start()` 调用及相关 import
- `CastForegroundService.kt`：`stop()` 改用 `stopService()`，删除 `ACTION_STOP` 分支
- `MainViewModel.kt`：新增 `startCastService()` / `stopCastService()` / `isCastServiceRunning`，在 3 处退出点调用 stop

## 验证

云手机（API 31）实测：

| 用例 | 结果 |
|---|---|
| `/stream?path=/data/data/.../airplay_queue.xml` | HTTP 403 |
| URL 编码绕过 `%2Fdata%2Fdata%2F...` | HTTP 403 |
| `/stream?path=/data/local/tmp/t.mp4`（未登记） | HTTP 403 |
| 启动 App 后 `dumpsys activity services` 中 `CastForegroundService` 计数 | 0 |

**未验证**：真实投屏时白名单不误伤、投屏时服务正确启停——云手机在云端虚拟网段，发现不到局域网电视。若投屏出现 403，应优先检查 `StreamServer` 日志中的 `Rejected unregistered path`，说明登记值与 NanoHTTPD 解码值不一致。

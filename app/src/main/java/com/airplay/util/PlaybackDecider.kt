package com.airplay.util

/**
 * 投屏播放轮询的决策逻辑。
 *
 * 从 [MainViewModel.startPlaybackPolling] 中抽出的纯函数，不依赖 Android / UPnP，
 * 便于单元测试。输入是「本次轮询观察到的事实」，输出是「下一步该做什么」。
 *
 * 抽出的动机：原逻辑内嵌在协程里，依赖 MediaRenderManager（具体类、需 UPnP 构造），
 * 没有任何测试缝，导致连续播放的 bug 无法被回归测试锁住。
 */
object PlaybackDecider {

    /** 轮询间隔（毫秒） */
    const val POLL_INTERVAL_MS = 3000L

    /** 位置连续不变多少次判定为卡住：5 × 3s = 15s */
    const val STUCK_THRESHOLD = 5

    /** duration 未知时的卡住阈值：10 × 3s = 30s（更保守，避免误跳） */
    const val STUCK_THRESHOLD_UNKNOWN_DURATION = 10

    /** 缓冲超时：12 × 3s = 36s */
    const val BUFFERING_LIMIT = 12

    /** 位置连续返回 -1 多少次判定为失败：3 次 */
    const val NEGATIVE_THRESHOLD = 3

    /** 距片尾多久算「接近结束」，等待后切下一集 */
    const val NEAR_END_MARGIN_MS = 3000L

    /** 判定结果 */
    enum class Decision {
        /** 继续轮询，不做动作 */
        CONTINUE,

        /** 播放正常，应把状态置为 PLAYING */
        STARTED,

        /** 当前视频应跳过，切下一个 */
        NEXT,
    }

    /**
     * 缓冲阶段（state == BUFFERING）的决策。
     *
     * @param pos 本次 GetPositionInfo 返回的位置；-1 表示查询失败
     * @param durationMs 当前视频时长；0 表示未知
     * @param bufferingCount 已连续缓冲的轮询次数
     * @param baselinePos 切歌后记录的基准位置；用于识别 renderer 仍在报上一个视频的位置
     */
    fun decideBuffering(
        pos: Long,
        durationMs: Long,
        bufferingCount: Int,
        baselinePos: Long,
    ): Decision {
        // 缓冲超时
        if (bufferingCount > BUFFERING_LIMIT) return Decision.NEXT

        // 查询失败：缓冲阶段位置查不到是正常的，继续等
        if (pos < 0) return Decision.CONTINUE

        // 位置 >= 时长：可能是本视频已播完，也可能是 renderer 仍报上一个视频的位置。
        // 判据：位置相对基准位置「没有前进」，说明是旧值，继续等；
        //       若位置明显小于基准（说明新视频从 0 开始播了）则不算已播完。
        if (durationMs > 0 && pos >= durationMs) {
            // 只有位置确实落在本视频时长之内（含等于），才认为是「已播完」
            // 若 pos 远超 durationMs（例如旧视频 120s、新视频 20s），视为旧值
            val looksLikeStalePosition = pos > durationMs + POLL_INTERVAL_MS
            if (looksLikeStalePosition) return Decision.CONTINUE

            // 至少要观察 2 次才信任，避免 SetAVTransportURI 尚未生效
            if (bufferingCount < 2) return Decision.CONTINUE
            return Decision.NEXT
        }

        // 位置有效且在本视频范围内：认为已开始播放
        // 注意：pos == 0 是缓冲中的常见值，但此时 bufferingCount 会继续累加，
        // 若一直停在 0，会由 PLAYING 阶段的卡住判定处理（见 decidePlaying）
        return Decision.STARTED
    }

    /**
     * 播放阶段（state == PLAYING）的决策。
     *
     * @param pos 本次位置；-1 表示查询失败
     * @param lastPos 上次位置
     * @param durationMs 当前视频时长；0 表示未知
     * @param stuckCount 位置连续不变的次数
     * @param negativeCount 位置连续为 -1 的次数
     */
    fun decidePlaying(
        pos: Long,
        lastPos: Long,
        durationMs: Long,
        stuckCount: Int,
        negativeCount: Int,
    ): Decision {
        // 查询失败
        if (pos < 0) {
            return if (negativeCount >= NEGATIVE_THRESHOLD) Decision.NEXT else Decision.CONTINUE
        }

        val durationKnown = durationMs > 0

        // 接近片尾：立即切下一集
        if (durationKnown && pos + NEAR_END_MARGIN_MS >= durationMs) {
            return Decision.NEXT
        }

        // 位置未变 → 可能卡住
        if (pos == lastPos) {
            // 关键修复：位置为 0 通常是电视缓冲中（GetPositionInfo 返回 00:00:00），
            // 不能算作「卡住」，否则缓冲稍久的视频会在开头就被跳过。
            if (pos == 0L) return Decision.CONTINUE

            val threshold = if (durationKnown) STUCK_THRESHOLD else STUCK_THRESHOLD_UNKNOWN_DURATION
            // 极短视频（时长短于一个轮询间隔）用阈值 1，否则永远等不到位置变化
            val effective = if (durationKnown && durationMs < POLL_INTERVAL_MS) 1 else threshold
            return if (stuckCount >= effective) Decision.NEXT else Decision.CONTINUE
        }

        return Decision.CONTINUE
    }
}

package com.airplay.util

import com.airplay.util.PlaybackDecider.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [PlaybackDecider] 单元测试。
 *
 * 每个用例对应一个真实场景或一个已修复的 bug，防止回归。
 */
class PlaybackDeciderTest {

    private val SEC = 1000L

    // ──────────────────────────────────────────────────────────────
    // decideBuffering
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `buffering continues while position is unavailable`() {
        assertEquals(
            Decision.CONTINUE,
            PlaybackDecider.decideBuffering(pos = -1, durationMs = 60 * SEC, bufferingCount = 1, baselinePos = 0)
        )
    }

    @Test
    fun `buffering times out after limit`() {
        assertEquals(
            Decision.NEXT,
            PlaybackDecider.decideBuffering(
                pos = -1,
                durationMs = 60 * SEC,
                bufferingCount = PlaybackDecider.BUFFERING_LIMIT + 1,
                baselinePos = 0,
            )
        )
    }

    @Test
    fun `buffering starts playing when position is inside duration`() {
        assertEquals(
            Decision.STARTED,
            PlaybackDecider.decideBuffering(pos = 3 * SEC, durationMs = 60 * SEC, bufferingCount = 1, baselinePos = 0)
        )
    }

    /**
     * BUG 回归：上一个视频 120s，新视频 20s，renderer 仍在报旧位置 120000。
     * 旧逻辑会把 120000 >= 20000 当成「已播完」，把新视频整段跳过。
     */
    @Test
    fun `stale position from previous longer video does not skip new video`() {
        assertEquals(
            Decision.CONTINUE,
            PlaybackDecider.decideBuffering(
                pos = 120 * SEC,
                durationMs = 20 * SEC,
                bufferingCount = 5,
                baselinePos = 120 * SEC,
            )
        )
    }

    @Test
    fun `position at duration counts as ended only after two polls`() {
        // 第一次观察到已到末尾：不信任，继续等
        assertEquals(
            Decision.CONTINUE,
            PlaybackDecider.decideBuffering(pos = 10 * SEC, durationMs = 10 * SEC, bufferingCount = 1, baselinePos = 0)
        )
        // 第二次仍如此：确认已播完
        assertEquals(
            Decision.NEXT,
            PlaybackDecider.decideBuffering(pos = 10 * SEC, durationMs = 10 * SEC, bufferingCount = 2, baselinePos = 0)
        )
    }

    // ──────────────────────────────────────────────────────────────
    // decidePlaying
    // ──────────────────────────────────────────────────────────────

    /**
     * BUG 回归：电视缓冲时位置一直返回 0。
     * 旧逻辑判定为「卡住」（3 次 × 3s = 9s）并把视频跳过。
     */
    @Test
    fun `position zero is treated as buffering not stuck`() {
        for (count in 1..20) {
            assertEquals(
                "位置为 0 且连续 $count 次，不应判为卡住",
                Decision.CONTINUE,
                PlaybackDecider.decidePlaying(
                    pos = 0,
                    lastPos = 0,
                    durationMs = 60 * SEC,
                    stuckCount = count,
                    negativeCount = 0,
                )
            )
        }
    }

    @Test
    fun `stuck mid video triggers next after threshold`() {
        // 阈值内继续
        assertEquals(
            Decision.CONTINUE,
            PlaybackDecider.decidePlaying(
                pos = 12 * SEC,
                lastPos = 12 * SEC,
                durationMs = 60 * SEC,
                stuckCount = PlaybackDecider.STUCK_THRESHOLD - 1,
                negativeCount = 0,
            )
        )
        // 达到阈值切下一个
        assertEquals(
            Decision.NEXT,
            PlaybackDecider.decidePlaying(
                pos = 12 * SEC,
                lastPos = 12 * SEC,
                durationMs = 60 * SEC,
                stuckCount = PlaybackDecider.STUCK_THRESHOLD,
                negativeCount = 0,
            )
        )
    }

    @Test
    fun `stuck threshold is fifteen seconds`() {
        assertEquals(15_000L, PlaybackDecider.STUCK_THRESHOLD * PlaybackDecider.POLL_INTERVAL_MS)
    }

    @Test
    fun `near end triggers next immediately`() {
        assertEquals(
            Decision.NEXT,
            PlaybackDecider.decidePlaying(
                pos = 57 * SEC,
                lastPos = 54 * SEC,
                durationMs = 60 * SEC,
                stuckCount = 0,
                negativeCount = 0,
            )
        )
    }

    @Test
    fun `normal progress keeps polling`() {
        assertEquals(
            Decision.CONTINUE,
            PlaybackDecider.decidePlaying(
                pos = 30 * SEC,
                lastPos = 27 * SEC,
                durationMs = 60 * SEC,
                stuckCount = 0,
                negativeCount = 0,
            )
        )
    }

    @Test
    fun `repeated negative positions trigger next`() {
        assertEquals(
            Decision.CONTINUE,
            PlaybackDecider.decidePlaying(
                pos = -1,
                lastPos = 30 * SEC,
                durationMs = 60 * SEC,
                stuckCount = 0,
                negativeCount = PlaybackDecider.NEGATIVE_THRESHOLD - 1,
            )
        )
        assertEquals(
            Decision.NEXT,
            PlaybackDecider.decidePlaying(
                pos = -1,
                lastPos = 30 * SEC,
                durationMs = 60 * SEC,
                stuckCount = 0,
                negativeCount = PlaybackDecider.NEGATIVE_THRESHOLD,
            )
        )
    }

    /**
     * BUG 回归：duration 未知（扫描器取不到 DURATION）时，
     * 旧逻辑的 near-end 判断失效，视频播完不会自动切下一个。
     * 新逻辑用更保守的卡住阈值兜底。
     */
    @Test
    fun `unknown duration uses conservative stuck threshold`() {
        // 未达保守阈值：继续
        assertEquals(
            Decision.CONTINUE,
            PlaybackDecider.decidePlaying(
                pos = 30 * SEC,
                lastPos = 30 * SEC,
                durationMs = 0,
                stuckCount = PlaybackDecider.STUCK_THRESHOLD_UNKNOWN_DURATION - 1,
                negativeCount = 0,
            )
        )
        // 达到保守阈值：切下一个
        assertEquals(
            Decision.NEXT,
            PlaybackDecider.decidePlaying(
                pos = 30 * SEC,
                lastPos = 30 * SEC,
                durationMs = 0,
                stuckCount = PlaybackDecider.STUCK_THRESHOLD_UNKNOWN_DURATION,
                negativeCount = 0,
            )
        )
    }

    @Test
    fun `unknown duration does not trigger near end`() {
        // duration=0 时 near-end 分支不生效（不能靠它切集）
        assertEquals(
            Decision.CONTINUE,
            PlaybackDecider.decidePlaying(
                pos = 999_999 * SEC,
                lastPos = 1 * SEC,
                durationMs = 0,
                stuckCount = 0,
                negativeCount = 0,
            )
        )
    }

    @Test
    fun `very short video uses threshold one`() {
        // 时长 2s < 一个轮询间隔 3s
        assertEquals(
            Decision.NEXT,
            PlaybackDecider.decidePlaying(
                pos = 2 * SEC,
                lastPos = 2 * SEC,
                durationMs = 2 * SEC,
                stuckCount = 1,
                negativeCount = 0,
            )
        )
    }
}

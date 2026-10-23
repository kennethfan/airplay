package com.airplay.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** [VideoItem.durationFormatted] 单元测试 */
class VideoItemTest {

    private fun videoWithDuration(durationMs: Long) =
        VideoItem(1L, "v.mp4", "/videos/v.mp4", durationMs, 0L, null, 0L)

    @Test
    fun `zero duration formats as 0 colon 00`() {
        assertEquals("0:00", videoWithDuration(0L).durationFormatted)
    }

    @Test
    fun `under one minute omits leading zero minutes`() {
        assertEquals("0:59", videoWithDuration(59_000L).durationFormatted)
    }

    @Test
    fun `exactly one minute`() {
        assertEquals("1:00", videoWithDuration(60_000L).durationFormatted)
    }

    @Test
    fun `minutes and seconds`() {
        assertEquals("12:34", videoWithDuration(754_000L).durationFormatted)
    }

    @Test
    fun `just under one hour stays in mm colon ss`() {
        assertEquals("59:59", videoWithDuration(3_599_000L).durationFormatted)
    }

    @Test
    fun `exactly one hour switches to h colon mm colon ss`() {
        assertEquals("1:00:00", videoWithDuration(3_600_000L).durationFormatted)
    }

    @Test
    fun `over one hour keeps two digit minutes and seconds`() {
        assertEquals("2:05:09", videoWithDuration(7_509_000L).durationFormatted)
    }

    @Test
    fun `partial seconds are truncated`() {
        // 5.9 秒向下取整为 5 秒
        assertEquals("0:05", videoWithDuration(5_900L).durationFormatted)
    }
}

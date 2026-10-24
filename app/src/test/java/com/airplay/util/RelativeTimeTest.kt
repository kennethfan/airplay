package com.airplay.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** [RelativeTime.format] 单元测试 */
class RelativeTimeTest {

    private val now = 1_700_000_000_000L

    private fun ago(ms: Long) = RelativeTime.format(now - ms, now)

    @Test
    fun `just now for anything under a minute`() {
        assertEquals("刚刚", ago(0))
        assertEquals("刚刚", ago(59_999))
    }

    @Test
    fun `minutes for under an hour`() {
        assertEquals("1分钟前", ago(60_000))
        assertEquals("59分钟前", ago(59 * 60_000L))
    }

    @Test
    fun `hours for under a day`() {
        assertEquals("1小时前", ago(60 * 60_000L))
        assertEquals("23小时前", ago(23 * 60 * 60_000L))
    }

    @Test
    fun `yesterday between one and two days`() {
        assertEquals("昨天", ago(24 * 60 * 60_000L))
        assertEquals("昨天", ago(47 * 60 * 60_000L))
    }

    @Test
    fun `days for under a week`() {
        assertEquals("2天前", ago(48 * 60 * 60_000L))
        assertEquals("6天前", ago(6 * 24 * 60 * 60_000L))
    }

    @Test
    fun `older than a week falls back to month-day`() {
        val result = ago(30 * 24 * 60 * 60_000L)
        // MM-dd 格式，形如 09-30
        assertEquals(true, Regex("""^\d{2}-\d{2}$""").matches(result))
    }

    @Test
    fun `future timestamp does not produce negative values`() {
        // 设备时钟偏差导致的未来时间按“刚刚”处理
        assertEquals("刚刚", RelativeTime.format(now + 10_000L, now))
    }
}

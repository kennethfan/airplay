package com.airplay.util

import com.airplay.model.SortField
import com.airplay.model.SortOption
import com.airplay.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

/**
 * [VideoSorter] 单元测试。
 *
 * 用 id 序列表示排序结果，避免依赖 VideoItem 的 equals 实现。
 */
class VideoSorterTest {

    private fun video(
        id: Long,
        name: String,
        durationMs: Long = 0L,
        fileSize: Long = 0L,
        dateAdded: Long = 0L,
    ) = VideoItem(id, name, "/videos/$name", durationMs, fileSize, null, dateAdded)

    /** 三个视频，各维度取值互不相同，便于断言顺序 */
    private val a = video(1, "a.mp4", durationMs = 10_000, fileSize = 300, dateAdded = 3)
    private val b = video(2, "b.mp4", durationMs = 30_000, fileSize = 100, dateAdded = 1)
    private val c = video(3, "c.mp4", durationMs = 20_000, fileSize = 200, dateAdded = 2)
    private val input = listOf(a, b, c)

    private fun ids(list: List<VideoItem>) = list.map { it.id }

    // ── 按时间 ──

    @Test
    fun `date descending puts newest first`() {
        val result = VideoSorter.sort(input, SortOption(SortField.DATE, descending = true))
        assertEquals(listOf(1L, 3L, 2L), ids(result))
    }

    @Test
    fun `date ascending puts oldest first`() {
        val result = VideoSorter.sort(input, SortOption(SortField.DATE, descending = false))
        assertEquals(listOf(2L, 3L, 1L), ids(result))
    }

    // ── 按时长 ──

    @Test
    fun `duration descending puts longest first`() {
        val result = VideoSorter.sort(input, SortOption(SortField.DURATION, descending = true))
        assertEquals(listOf(2L, 3L, 1L), ids(result))
    }

    @Test
    fun `duration ascending puts shortest first`() {
        val result = VideoSorter.sort(input, SortOption(SortField.DURATION, descending = false))
        assertEquals(listOf(1L, 3L, 2L), ids(result))
    }

    // ── 按大小 ──

    @Test
    fun `size descending puts largest first`() {
        val result = VideoSorter.sort(input, SortOption(SortField.SIZE, descending = true))
        assertEquals(listOf(1L, 3L, 2L), ids(result))
    }

    @Test
    fun `size ascending puts smallest first`() {
        val result = VideoSorter.sort(input, SortOption(SortField.SIZE, descending = false))
        assertEquals(listOf(2L, 3L, 1L), ids(result))
    }

    // ── 次级排序：相同数值按文件名升序，且不随方向翻转 ──

    @Test
    fun `equal values tie-break by name ascending when descending`() {
        // 三个视频时长相同，名称故意乱序
        val x = video(10, "zebra.mp4", durationMs = 5000)
        val y = video(11, "apple.mp4", durationMs = 5000)
        val z = video(12, "mango.mp4", durationMs = 5000)

        val result = VideoSorter.sort(
            listOf(x, y, z),
            SortOption(SortField.DURATION, descending = true),
        )
        // 主键相同 → 次级键（名称）固定升序，不因 descending 而倒序
        assertEquals(listOf(11L, 12L, 10L), ids(result))
    }

    @Test
    fun `equal values tie-break by name ascending when ascending`() {
        val x = video(10, "zebra.mp4", durationMs = 5000)
        val y = video(11, "apple.mp4", durationMs = 5000)
        val z = video(12, "mango.mp4", durationMs = 5000)

        val result = VideoSorter.sort(
            listOf(x, y, z),
            SortOption(SortField.DURATION, descending = false),
        )
        // 升降序下次级排序结果必须一致
        assertEquals(listOf(11L, 12L, 10L), ids(result))
    }

    // ── 边界情况 ──

    @Test
    fun `empty list returns empty list`() {
        val result = VideoSorter.sort(emptyList(), SortOption(SortField.DATE, descending = true))
        assertEquals(emptyList<VideoItem>(), result)
    }

    @Test
    fun `single element list is unchanged`() {
        val result = VideoSorter.sort(listOf(a), SortOption(SortField.SIZE, descending = false))
        assertEquals(listOf(1L), ids(result))
    }

    @Test
    fun `already sorted list stays sorted`() {
        val sorted = VideoSorter.sort(input, SortOption(SortField.DURATION, descending = true))
        val again = VideoSorter.sort(sorted, SortOption(SortField.DURATION, descending = true))
        assertEquals(ids(sorted), ids(again))
    }

    @Test
    fun `original list is not modified`() {
        val original = listOf(a, b, c)
        val result = VideoSorter.sort(original, SortOption(SortField.DURATION, descending = true))
        // 入参顺序保持原样，返回的是新列表
        assertEquals(listOf(1L, 2L, 3L), ids(original))
        assertNotSame(original, result)
    }

    @Test
    fun `zero values do not break ordering`() {
        // duration/fileSize 缺失（0）时仍应稳定排序，不抛异常
        val zero1 = video(20, "a_zero.mp4")
        val zero2 = video(21, "b_zero.mp4")
        val normal = video(22, "c_normal.mp4", durationMs = 1000)

        val result = VideoSorter.sort(
            listOf(zero1, zero2, normal),
            SortOption(SortField.DURATION, descending = true),
        )
        assertEquals(listOf(22L, 20L, 21L), ids(result))
    }
}

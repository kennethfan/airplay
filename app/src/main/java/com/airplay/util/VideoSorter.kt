package com.airplay.util

import com.airplay.model.SortField
import com.airplay.model.SortOption
import com.airplay.model.VideoItem

/**
 * 视频列表排序。纯函数，不依赖 Android，便于单元测试。
 */
object VideoSorter {

    /**
     * 按 [option] 对 [list] 排序并返回新列表，不修改入参。
     *
     * 主键按 option 的方向排序；次级键（文件名）**固定升序**，
     * 保证数值相同时顺序稳定，且不会随升降序翻转。
     */
    fun sort(list: List<VideoItem>, option: SortOption): List<VideoItem> {
        val primary = when (option.field) {
            SortField.DATE -> compareBy<VideoItem> { it.dateAdded }
            SortField.DURATION -> compareBy { it.durationMs }
            SortField.SIZE -> compareBy { it.fileSize }
        }.let { if (option.descending) it.reversed() else it }

        return list.sortedWith(primary.thenBy { it.name })
    }
}

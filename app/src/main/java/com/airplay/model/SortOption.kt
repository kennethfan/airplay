package com.airplay.model

/** 视频列表可用的排序维度 */
enum class SortField(val label: String) {
    DATE("按时间"),
    DURATION("按时长"),
    SIZE("按大小"),
}

/** 排序方式：维度 + 方向 */
data class SortOption(
    val field: SortField,
    val descending: Boolean,
)

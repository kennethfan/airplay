package com.airplay.model

/**
 * 收藏记录。
 *
 * 与 [RecentPlay] 一致，只持久化必要字段，展示时用 [videoId] 回查当前扫描结果
 * 以拿到最新缩略图；文件被删除或被扫描器过滤后，该记录自动失效。
 */
data class Favorite(
    val videoId: Long,
    val path: String,
    val name: String,
    /** 收藏时间戳（毫秒），用于按收藏时间排序 */
    val addedAt: Long,
)

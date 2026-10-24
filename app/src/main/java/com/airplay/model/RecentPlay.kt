package com.airplay.model

/**
 * 最近播放记录。
 *
 * 只持久化必要字段，展示时用 [videoId] 回查当前扫描结果以拿到最新缩略图；
 * 若视频文件已被删除，回查会失败，该记录会被自动跳过。
 */
data class RecentPlay(
    val videoId: Long,
    val path: String,
    val name: String,
    /** 发起投屏的时间戳（毫秒） */
    val playedAt: Long,
    /** 投屏到的设备名，仅用于展示 */
    val deviceName: String,
)

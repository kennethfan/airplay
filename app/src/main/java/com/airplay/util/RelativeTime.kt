package com.airplay.util

/**
 * 相对时间格式化。纯函数，不依赖 Android，便于单元测试。
 */
object RelativeTime {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /**
     * 把 [timestampMs] 格式化为相对 [nowMs] 的中文描述。
     *
     * - 1 分钟内：刚刚
     * - 1 小时内：N分钟前
     * - 当天内：N小时前
     * - 昨天：昨天
     * - 7 天内：N天前
     * - 更早：MM-dd
     */
    fun format(timestampMs: Long, nowMs: Long): String {
        // 未来时间（设备时钟偏差等）按“刚刚”处理，避免出现负数
        val diff = nowMs - timestampMs
        if (diff < MINUTE) return "刚刚"
        if (diff < HOUR) return "${diff / MINUTE}分钟前"
        if (diff < DAY) return "${diff / HOUR}小时前"
        if (diff < 2 * DAY) return "昨天"
        if (diff < 7 * DAY) return "${diff / DAY}天前"
        return formatDate(timestampMs)
    }

    /** MM-dd 形式；不引入 java.time 以保持 API 26 兼容且便于测试 */
    private fun formatDate(timestampMs: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestampMs
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        return String.format(java.util.Locale.US, "%02d-%02d", month, day)
    }
}

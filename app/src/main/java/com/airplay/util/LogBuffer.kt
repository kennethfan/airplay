package com.airplay.util

import android.util.Log

object LogBuffer {
    private val buffer = mutableListOf<String>()
    private const val MAX_LINES = 300

    @JvmStatic fun d(tag: String, message: String) {
        Log.d(tag, message)
        append("D", tag, message)
    }

    @JvmStatic fun i(tag: String, message: String) {
        Log.i(tag, message)
        append("I", tag, message)
    }

    @JvmStatic @JvmOverloads fun w(tag: String, message: String, e: Throwable? = null) {
        Log.w(tag, message, e)
        append("W", tag, message + if (e != null) " | ${e.message}" else "")
    }

    @JvmStatic @JvmOverloads fun e(tag: String, message: String, e: Throwable? = null) {
        Log.e(tag, message, e)
        append("E", tag, message + if (e != null) " | ${e.message}" else "")
    }

    private fun append(level: String, tag: String, msg: String) {
        val line = "$level/$tag: $msg"
        synchronized(buffer) {
            buffer.add(line)
            if (buffer.size > MAX_LINES) {
                buffer.removeAt(0)
            }
        }
    }

    fun getLogs(): List<String> = synchronized(buffer) { buffer.toList() }
    fun getLogText(): String = synchronized(buffer) { buffer.joinToString("\n") }
    fun clear() { synchronized(buffer) { buffer.clear() } }
}

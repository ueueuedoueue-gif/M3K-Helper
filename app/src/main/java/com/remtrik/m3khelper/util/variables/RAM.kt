package com.remtrik.m3khelper.util.variables

import android.app.ActivityManager
import android.content.Context
import com.remtrik.m3khelper.M3KApp
import java.io.File

/**
 * Something with ram labels
 */
private val memoryRanges = listOf(
    14_000L..Long.MAX_VALUE to "16GB",
    10_000L..13_999L to "12GB",
    7_000L..9_999L to "8GB",
    5_000L..6_999L to "6GB",
    3_500L..4_999L to "4GB",
    2_500L..3_499L to "3GB",
    1_500L..2_499L to "2GB",
    0L..1_499L to "1GB"
)

/**
 * Returns a label for the device RAM capacity
 */
fun getMemory(context: Context = M3KApp): String {
    val totalMemMB = getTotalMemoryInMB(context)
    return if (totalMemMB <= 0) "Unknown" else {
        memoryRanges.firstOrNull { totalMemMB in it.first }?.second ?: "Unknown"
    }
}

/**
 * Reads total memory in MB via ActivityManager
 */
private fun getTotalMemoryInMB(context: Context): Long {
    val amTotalMem = runCatching {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        activityManager?.getMemoryInfo(memoryInfo)
        memoryInfo.totalMem / (1024 * 1024)
    }.getOrNull() ?: 0L

    return if (amTotalMem > 0) amTotalMem else readTotalMemFromProc()
}

/**
 * Reads MemTotal from meminfo as a fallback.
 */
private fun readTotalMemFromProc(): Long = runCatching {
    File("/proc/meminfo").useLines { lines ->
        lines.firstOrNull { it.startsWith("MemTotal:") }
            ?.split(Regex("\\s+"))
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?.div(1024)
    }
}.getOrNull() ?: 0L

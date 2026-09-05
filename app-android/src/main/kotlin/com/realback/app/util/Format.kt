package com.realback.app.util

import android.content.Context

/** Resolves a user-facing app label, falling back to the package name. */
fun appLabel(context: Context, packageName: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (e: android.content.pm.PackageManager.NameNotFoundException) {
    packageName
}

/** Human-readable duration in Chinese, e.g. "3小时25分钟". */
fun formatDurationMillis(millis: Long): String {
    val totalMinutes = millis / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分钟"
        else -> "${minutes}分钟"
    }
}

package com.kontrolcore.mobile

import java.text.DateFormat
import java.util.Date

object Format {
    /** "3 min ago", "2 h ago", or the date for older ones. */
    fun ago(seconds: Long, nowSeconds: Long = System.currentTimeMillis() / 1000): String {
        val d = nowSeconds - seconds
        return when {
            d < 60 -> "just now"
            d < 3600 -> "${d / 60} min ago"
            d < 86400 -> "${d / 3600} h ago"
            else -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(seconds * 1000))
        }
    }
}

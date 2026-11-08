package com.kontrolcore.mobile

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import androidx.core.content.ContextCompat
import com.kontrolcore.mobile.core.AlertKind
import com.kontrolcore.mobile.core.InboxItem

/** Colour means state, as in the extension: red failing, amber needs a look, blue gone quiet, grey acknowledged. */
object Look {
    fun colorOf(context: Context, i: InboxItem): Int = ContextCompat.getColor(context, when {
        i.acknowledged -> R.color.kc_ack
        i.kind == AlertKind.FAILING -> R.color.kc_red
        i.kind == AlertKind.QUIET -> R.color.kc_blue
        else -> R.color.kc_amber
    })

    fun wordFor(i: InboxItem): String = when {
        i.acknowledged -> "Acknowledged"
        i.kind == AlertKind.FAILING -> "Failing"
        i.kind == AlertKind.NEEDS_LOOK -> "Needs a look"
        i.kind == AlertKind.QUIET -> "Gone quiet"
        else -> "Needs you"
    }

    fun tintDot(v: View, color: Int) { v.backgroundTintList = ColorStateList.valueOf(color) }
}

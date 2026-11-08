package com.kontrolcore.mobile

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Draws the real layouts with the real theme and sample data, to look at them without a phone. Writes PNGs to build/screens. Not a test of behaviour. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi", application = Application::class)
class ScreenshotTest {
    private val ctx: Context get() = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_KontrolCore)
    private fun inflate(layout: Int): View = LayoutInflater.from(ctx).inflate(layout, FrameHolder(ctx), false)
    private class FrameHolder(c: Context) : android.widget.FrameLayout(c)

    private fun save(root: View, name: String, night: Boolean, toolbarTitle: String = "KontrolCore", back: Boolean = false) {
        val dm = ctx.resources.displayMetrics
        root.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)?.apply {
            title = toolbarTitle
            if (!back) logo = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.logo_inset)
            else navigationIcon = androidx.core.content.ContextCompat.getDrawable(context, androidx.appcompat.R.drawable.abc_ic_ab_back_material)?.mutate()?.also { it.setTint(0xFF888888.toInt()) }
        }
        val frame = android.widget.FrameLayout(ctx).apply { setBackgroundColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.kc_bg)); addView(root) }
        frame.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, dm.widthPixels, dm.heightPixels)
        val bmp = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888)
        frame.draw(Canvas(bmp))
        val dir = File("build/screens").apply { mkdirs() }
        FileOutputStream(File(dir, "$name${if (night) "-dark" else ""}.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun row(parent: ViewGroup, name: String, summary: String, meta: String, color: Int, dim: Boolean = false) {
        val v = LayoutInflater.from(ctx).inflate(R.layout.item_inbox, parent, false)
        v.findViewById<TextView>(R.id.name).text = name; v.findViewById<TextView>(R.id.summary).text = summary; v.findViewById<TextView>(R.id.meta).text = meta
        Look.tintDot(v.findViewById(R.id.dot), androidx.core.content.ContextCompat.getColor(ctx, color))
        if (dim) v.alpha = 0.6f
        parent.addView(v)
    }

    private fun inbox(): View {
        val v = inflate(R.layout.activity_main) as ViewGroup
        v.findViewById<TextView>(R.id.connection).apply { text = "Connected to your alert topic."; setCompoundDrawablesRelativeWithIntrinsicBounds(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.dot)?.mutate()?.also { d -> d.setTint(androidx.core.content.ContextCompat.getColor(context, R.color.kc_green)) }, null, null, null) }
        v.findViewById<View>(R.id.empty).visibility = View.GONE
        val list = v.findViewById<View>(R.id.list); val host = list.parent as ViewGroup
        host.removeView(list)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        row(col, "Checkout flow", "Click \"Pay now\"", "Failing · 4 min ago · 3 alerts", R.color.kc_red)
        row(col, "Login", "Page is asking for a sign-in", "Needs a look · 1 h ago · 1 alert", R.color.kc_amber)
        row(col, "Nightly regression", "Suite failed: 1 of 4", "Failing · 2 h ago · 2 alerts", R.color.kc_red)
        row(col, "Uptime check", "Has gone quiet", "Gone quiet · 3 h ago · 1 alert", R.color.kc_blue)
        row(col, "Search", "Has gone quiet", "Acknowledged · 5 h ago · 1 alert", R.color.kc_ack, dim = true)
        host.addView(col, 0)
        v.findViewById<TextView>(R.id.message).apply { visibility = View.VISIBLE; text = "Sent. Waiting for your computer (it looks about once a minute)…" }
        return v
    }

    private fun detail(): View {
        val v = inflate(R.layout.activity_detail)
        v.findViewById<TextView>(R.id.badge).apply { text = "Failing"; setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.kc_red)) }
        v.findViewById<TextView>(R.id.title).text = "Checkout flow"
        v.findViewById<TextView>(R.id.meta).text = "\"Checkout flow\" has failed 3 times in a row\nFirst alert 2 h ago · 3 alerts"
        v.findViewById<TextView>(R.id.step).text = "Click \"Pay now\""
        v.findViewById<TextView>(R.id.stepDetail).text = "Element not found after 10 s"
        v.findViewById<TextView>(R.id.url).text = "https://shop.example.com/cart"
        v.findViewById<TextView>(R.id.status).apply { visibility = View.VISIBLE; text = "Sent. Waiting for your computer (it looks about once a minute)…" }
        return v
    }

    private fun shot(name: String, night: Boolean, back: Boolean = false, build: () -> View) {
        RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight") // before inflating, so the views get the right theme
        save(build(), name, night, back = back)
    }

    @Test fun inboxLight() = shot("1-inbox", false) { inbox() }
    @Test fun inboxDark() = shot("1-inbox", true) { inbox() }
    @Test fun detailLight() = shot("2-failure_detail", false, true) { detail() }
    @Test fun detailDark() = shot("2-failure_detail", true, true) { detail() }
    @Test fun pairLight() = shot("3-pairing", false) { inflate(R.layout.activity_pair) }
    @Test fun pairDark() = shot("3-pairing", true) { inflate(R.layout.activity_pair) }
    @Test fun emptyLight() = shot("4-nothing_needs_you", false) { empty() }

    private fun empty(): View {
        val v = inflate(R.layout.activity_main)
        v.findViewById<TextView>(R.id.connection).apply { text = "Connected to your alert topic."; setCompoundDrawablesRelativeWithIntrinsicBounds(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.dot)?.mutate()?.also { d -> d.setTint(androidx.core.content.ContextCompat.getColor(context, R.color.kc_green)) }, null, null, null) }
        return v
    }
}

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

    private fun save(root: View, name: String, night: Boolean) {
        val dm = ctx.resources.displayMetrics
        val bar = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(ctx).apply { text = name.substringAfter('-').replace('_', ' ').replaceFirstChar { it.uppercase() }; textSize = 20f; setPadding(48, 96, 48, 36); setTextColor(if (night) 0xFFFFFFFF.toInt() else 0xFF1B1B1F.toInt()) }
        bar.addView(title); bar.addView(root, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        bar.setBackgroundColor(if (night) 0xFF1B1B1F.toInt() else 0xFFFEFBFF.toInt())
        bar.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        bar.layout(0, 0, dm.widthPixels, dm.heightPixels)
        val bmp = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888)
        bar.draw(Canvas(bmp))
        val dir = File("build/screens").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun row(parent: ViewGroup, name: String, summary: String, meta: String, dim: Boolean = false) {
        val v = LayoutInflater.from(ctx).inflate(R.layout.item_inbox, parent, false)
        v.findViewById<TextView>(R.id.name).text = name; v.findViewById<TextView>(R.id.summary).text = summary; v.findViewById<TextView>(R.id.meta).text = meta
        if (dim) v.alpha = 0.55f
        parent.addView(v)
    }

    private fun inbox(): View {
        val v = inflate(R.layout.activity_main) as ViewGroup
        v.findViewById<TextView>(R.id.connection).text = "Connected to your alert topic."
        v.findViewById<View>(R.id.empty).visibility = View.GONE
        val list = v.findViewById<View>(R.id.list); val host = list.parent as ViewGroup
        host.removeView(list)
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        row(col, "Checkout flow", "Click \"Pay now\"", "4 min ago · 3 alerts")
        row(col, "Login", "Needs a look", "1 h ago · 1 alert")
        row(col, "Nightly regression", "Failing", "2 h ago · 2 alerts")
        row(col, "Search", "Has gone quiet", "5 h ago · 1 alert · acknowledged", dim = true)
        host.addView(col, 0)
        v.findViewById<TextView>(R.id.message).apply { visibility = View.VISIBLE; text = "Sent. Waiting for your computer (it looks about once a minute)…" }
        return v
    }

    private fun detail(): View {
        val v = inflate(R.layout.activity_detail)
        v.findViewById<TextView>(R.id.title).text = "Checkout flow"
        v.findViewById<TextView>(R.id.meta).text = "\"Checkout flow\" has failed 3 times in a row\nFirst alert 2 h ago · 3 alerts"
        v.findViewById<TextView>(R.id.step).text = "Click \"Pay now\""
        v.findViewById<TextView>(R.id.stepDetail).text = "Element not found after 10 s"
        v.findViewById<TextView>(R.id.url).text = "https://shop.example.com/cart"
        v.findViewById<TextView>(R.id.status).apply { visibility = View.VISIBLE; text = "Sent. Waiting for your computer (it looks about once a minute)…" }
        return v
    }

    @Test fun lightInbox() = save(inbox(), "1-inbox", false)
    @Test fun lightDetail() = save(detail(), "2-failure_detail", false)
    @Test fun lightPair() = save(inflate(R.layout.activity_pair), "3-pairing", false)
    @Test fun lightEmpty() { val v = inflate(R.layout.activity_main); v.findViewById<TextView>(R.id.connection).text = "Connected to your alert topic."; save(v, "4-nothing_needs_you", false) }
}

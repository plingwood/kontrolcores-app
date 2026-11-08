package com.kontrolcore.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kontrolcore.mobile.core.InboxItem

/** One scenario that needs the person: what failed, and Acknowledge, Snooze, Run again, Stop and Share. */
class DetailActivity : AppCompatActivity() {
    private lateinit var name: String
    private val refresh: () -> Unit = { draw() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        name = intent.getStringExtra(EXTRA_NAME) ?: run { finish(); return }
        setContentView(R.layout.activity_detail)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<View>(R.id.ack).setOnClickListener { send("ack", 1) }
        findViewById<View>(R.id.snooze).setOnClickListener { send("snooze", 1) }
        findViewById<View>(R.id.run).setOnClickListener { confirmRun() }
        findViewById<View>(R.id.stop).setOnClickListener { send("stop", 1) }
        findViewById<View>(R.id.share).setOnClickListener {
            val text = Repo.itemNamed(name)?.shareText() ?: name
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, "KontrolCore: $name"), "Share summary"))
        }
        findViewById<View>(R.id.open).setOnClickListener {
            val url = Repo.itemNamed(name)?.url ?: return@setOnClickListener
            if (url.startsWith("https://") || url.startsWith("http://")) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        if (intent.getStringExtra(EXTRA_AUTO) == "run") confirmRun()
    }

    override fun onStart() { super.onStart(); Repo.listen(refresh); draw() }
    override fun onStop() { super.onStop(); Repo.unlisten(refresh) }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun draw() {
        val i: InboxItem? = Repo.itemNamed(name)
        findViewById<TextView>(R.id.title).text = name
        findViewById<TextView>(R.id.meta).text = if (i == null) "Not on your list now: it may have recovered." else
            "${i.title}\nFirst alert ${Format.ago(i.since)} · ${i.count} ${if (i.count == 1) "alert" else "alerts"}${if (i.acknowledged) " · acknowledged" else ""}"
        findViewById<TextView>(R.id.badge).apply {
            visibility = if (i == null) View.GONE else View.VISIBLE
            if (i != null) { text = Look.wordFor(i); setTextColor(Look.colorOf(context, i)) }
        }
        val hasStep = i != null && i.step.isNotEmpty()
        findViewById<View>(R.id.stepLabel).visibility = if (hasStep) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.step).apply { text = i?.step ?: ""; visibility = if (hasStep) View.VISIBLE else View.GONE }
        findViewById<TextView>(R.id.stepDetail).apply { text = i?.stepDetail ?: ""; visibility = if (i != null && i.stepDetail.isNotEmpty()) View.VISIBLE else View.GONE }
        findViewById<TextView>(R.id.url).apply { text = i?.url ?: ""; visibility = if (i != null && i.url.isNotEmpty()) View.VISIBLE else View.GONE }
        findViewById<View>(R.id.open).visibility = if (i != null && i.url.isNotEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.share).visibility = if (i != null) View.VISIBLE else View.GONE
    }

    private fun confirmRun() {
        AlertDialog.Builder(this).setTitle("Run \"$name\" again?").setMessage("Your computer will start it when it next looks, about a minute. Running from a phone needs KontrolCore Pro.")
            .setPositiveButton("Run") { _, _ -> send("run", 1) }.setNegativeButton("Cancel", null).show()
    }

    private fun send(command: String, hours: Int) {
        val status = findViewById<TextView>(R.id.status)
        Commands.send(this, command, name, hours) { s ->
            status.visibility = View.VISIBLE
            status.text = when (s) { is Commands.Status.Working -> s.text; is Commands.Status.Done -> s.text; is Commands.Status.Failed -> s.text }
        }
    }

    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_AUTO = "auto"
        fun open(context: Context, name: String) = context.startActivity(Intent(context, DetailActivity::class.java).putExtra(EXTRA_NAME, name))
    }
}

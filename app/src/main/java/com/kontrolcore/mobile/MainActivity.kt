package com.kontrolcore.mobile

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kontrolcore.mobile.core.InboxItem

/** The Needs-you inbox. It is behind the lock: nothing shows until the person has unlocked. */
class MainActivity : AppCompatActivity() {
    private val adapter = Adapter { DetailActivity.open(this, it.name) }
    private val refresh: () -> Unit = { draw() }
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setLogo(R.drawable.logo_inset)
        supportActionBar?.setDisplayUseLogoEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        findViewById<RecyclerView>(R.id.list).apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = this@MainActivity.adapter }
        findViewById<View>(R.id.statusButton).setOnClickListener { send("status", "") }
        findViewById<View>(R.id.runButton).setOnClickListener { askRunOrStop() }
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onStart() {
        super.onStart()
        if (Repo.paired() == null) { startActivity(Intent(this, PairActivity::class.java)); return }
        findViewById<View>(R.id.root).visibility = View.INVISIBLE
        Gate.require(this, "Unlock KontrolCore", onFail = { if (it.isNotEmpty()) toast(it); finish() }) {
            unlocked = true
            findViewById<View>(R.id.root).visibility = View.VISIBLE
            ListenService.start(applicationContext)
            Repo.listen(refresh)
            draw()
        }
    }

    override fun onStop() {
        super.onStop()
        Repo.unlisten(refresh)
        if (unlocked) { unlocked = false }
    }

    private fun draw() {
        val items = Repo.inbox()
        adapter.submit(items)
        findViewById<View>(R.id.empty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val needs = items.count { !it.acknowledged }
        title = if (needs == 0) getString(R.string.app_name) else "$needs need${if (needs == 1) "s" else ""} you"
        findViewById<TextView>(R.id.connection).apply {
            text = if (Repo.connected) "Connected to your alert topic." else (Repo.connectionNote.ifEmpty { "Connecting…" })
            setCompoundDrawablesRelativeWithIntrinsicBounds(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.dot), null, null, null)
            compoundDrawablesRelative[0]?.mutate()?.setTint(androidx.core.content.ContextCompat.getColor(context, if (Repo.connected) R.color.kc_green else R.color.kc_amber))
        }
    }

    private fun send(command: String, name: String) {
        val msg = findViewById<TextView>(R.id.message)
        Commands.send(this, command, name) { s ->
            msg.visibility = View.VISIBLE
            msg.text = when (s) { is Commands.Status.Working -> s.text; is Commands.Status.Done -> s.text; is Commands.Status.Failed -> s.text }
        }
    }

    private fun askRunOrStop() {
        val input = EditText(this).apply { hint = "Scenario or suite name"; setSingleLine() }
        AlertDialog.Builder(this).setTitle("Run or stop").setView(input)
            .setPositiveButton("Run") { _, _ -> input.text.toString().trim().takeIf { it.isNotEmpty() }?.let { send("run", it) } }
            .setNeutralButton("Stop") { _, _ -> input.text.toString().trim().takeIf { it.isNotEmpty() }?.let { send("stop", it) } }
            .setNegativeButton("Cancel", null).show()
    }

    private fun toast(t: String) = android.widget.Toast.makeText(this, t, android.widget.Toast.LENGTH_LONG).show()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Battery settings")
        menu.add(0, 2, 1, "Unpair this phone")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        1 -> {
            AlertDialog.Builder(this).setTitle("Alerts that arrive late")
                .setMessage("Some phones stop apps that run in the background. If alerts come late, set KontrolCore to \"Unrestricted\" (or turn off battery optimisation for it) in the phone's settings.")
                .setPositiveButton("Open settings") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }
                .setNegativeButton("Close", null).show(); true
        }
        2 -> {
            AlertDialog.Builder(this).setTitle("Unpair this phone?")
                .setMessage("The key is deleted from this phone. To use it again, scan a new code. To cut a lost phone off for good, change the setup key in KontrolCore, Alerts, Channels.")
                .setPositiveButton("Unpair") { _, _ ->
                    ListenService.stop(applicationContext); Vault.remove(applicationContext); Repo.unpair(); Gate.lock()
                    startActivity(Intent(this, PairActivity::class.java))
                }.setNegativeButton("Cancel", null).show(); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private class Adapter(val onClick: (InboxItem) -> Unit) : RecyclerView.Adapter<Adapter.Holder>() {
        private var items: List<InboxItem> = emptyList()
        fun submit(list: List<InboxItem>) { items = list; notifyDataSetChanged() }
        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val dot: View = v.findViewById(R.id.dot); val name: TextView = v.findViewById(R.id.name); val summary: TextView = v.findViewById(R.id.summary); val meta: TextView = v.findViewById(R.id.meta)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_inbox, parent, false))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: Holder, position: Int) {
            val i = items[position]
            h.name.text = i.name
            Look.tintDot(h.dot, Look.colorOf(h.itemView.context, i))
            h.summary.text = i.summary
            h.meta.text = buildString {
                append(Look.wordFor(i)).append(" · ").append(Format.ago(i.last))
                append(" · ").append(i.count).append(if (i.count == 1) " alert" else " alerts")
                            }
            h.itemView.alpha = if (i.acknowledged) 0.6f else 1f
            h.itemView.setOnClickListener { onClick(i) }
        }
    }
}

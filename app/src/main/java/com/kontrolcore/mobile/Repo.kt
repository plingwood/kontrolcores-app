package com.kontrolcore.mobile

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.kontrolcore.mobile.core.AlertKind
import com.kontrolcore.mobile.core.Alerts
import com.kontrolcore.mobile.core.Inbox
import com.kontrolcore.mobile.core.InboxItem
import com.kontrolcore.mobile.core.NtfyAddress
import com.kontrolcore.mobile.core.NtfyMessage
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** What the person paired with. The setup key is not here: it lives in the Keystore (see [Vault]). */
data class Paired(val topic: String, val server: String?) {
    val topicUrl: String get() = NtfyAddress.topicUrl(server ?: NtfyAddress.DEFAULT, topic)
}

/**
 * The app's memory: who it is paired with and the messages seen on the topic (kept in a private file, at most [MAX], so the inbox can be worked out again at
 * any time). Alerts are on the topic anyone could read, so this holds no secret; the setup key is the only secret and is not stored here.
 */
object Repo {
    private const val MAX = 300
    private lateinit var prefs: SharedPreferences
    private lateinit var file: File
    private val messages = ArrayList<NtfyMessage>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    @Volatile var connected: Boolean = false
        private set
    @Volatile var connectionNote: String = ""
        private set

    @Synchronized
    fun init(context: Context) {
        if (this::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("kontrolcore", Context.MODE_PRIVATE)
        file = File(context.applicationContext.filesDir, "messages.jsonl")
        messages.clear()
        if (file.exists()) file.readLines().mapNotNullTo(messages) { NtfyMessage.parse(it) }
    }

    fun paired(): Paired? {
        val topic = prefs.getString("topic", null) ?: return null
        return Paired(topic, prefs.getString("server", null))
    }

    fun savePaired(p: Paired) {
        prefs.edit().putString("topic", p.topic).putString("server", p.server).remove("lastId").apply()
        synchronized(this) { messages.clear(); file.delete() }
        changed()
    }

    fun unpair() {
        prefs.edit().clear().apply()
        synchronized(this) { messages.clear(); file.delete() }
        connected = false
        changed()
    }

    fun lastId(): String? = prefs.getString("lastId", null)

    /** Keeps a message from the stream. Returns true if it was new (a repeat after a reconnect is not). */
    @Synchronized
    fun add(m: NtfyMessage): Boolean {
        if (m.id.isNotEmpty() && messages.any { it.id == m.id }) return false
        messages.add(m)
        messages.sortBy { it.time }
        while (messages.size > MAX) messages.removeAt(0)
        if (m.id.isNotEmpty()) prefs.edit().putString("lastId", m.id).apply()
        runCatching { file.writeText(messages.map { it.raw }.filter { it.isNotBlank() }.joinToString("\n")) }
        changed()
        return true
    }

    @Synchronized
    fun inbox(): List<InboxItem> = Inbox.build(messages.toList(), System.currentTimeMillis() / 1000)

    @Synchronized
    fun itemNamed(name: String): InboxItem? = inbox().firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** The newest reply from the computer ("KontrolCore: Too old" and the like) sent after [sinceSeconds], as its title and text. */
    @Synchronized
    fun replySince(sinceSeconds: Long): Pair<String, String>? =
        messages.filter { it.time >= sinceSeconds }.mapNotNull { Alerts.classify(it) }.lastOrNull { it.kind == AlertKind.REPLY }?.let { it.step.ifEmpty { it.title } to it.body }

    fun setConnected(ok: Boolean, note: String = "") {
        connected = ok
        connectionNote = note
        changed()
    }

    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }
    private fun changed() { main.post { listeners.forEach { it() } } }
}

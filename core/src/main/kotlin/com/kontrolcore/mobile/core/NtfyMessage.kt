package com.kontrolcore.mobile.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** One button ntfy attaches to a message (the Actions header the extension writes). */
data class NtfyAction(val action: String, val label: String, val url: String, val method: String, val body: String, val clear: Boolean)

/** One line of ntfy's JSON stream, or of the topic's JSON history. */
data class NtfyMessage(
    val id: String,
    val time: Long,
    val event: String,
    val title: String,
    val message: String,
    val tags: List<String>,
    val priority: Int,
    val actions: List<NtfyAction>,
) {
    val isMessage: Boolean get() = event == "message"

    companion object {
        /** A stream line as a message, or null for a blank or damaged line. Unknown fields are ignored. */
        fun parse(line: String?): NtfyMessage? {
            val text = (line ?: "").trim()
            if (text.isEmpty() || text[0] != '{') return null
            val o = try { JsonParser.parseString(text).asJsonObject } catch (e: Exception) { return null }
            fun str(k: String): String = o.get(k)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
            val tags = o.get("tags")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString } ?: emptyList()
            val actions = o.get("actions")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { e -> if (e.isJsonObject) action(e.asJsonObject) else null } ?: emptyList()
            return NtfyMessage(
                id = str("id"),
                time = o.get("time")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() } ?: 0L,
                event = str("event"),
                title = str("title"),
                message = str("message"),
                tags = tags,
                priority = o.get("priority")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() } ?: 3,
                actions = actions,
            )
        }

        private fun action(o: JsonObject): NtfyAction {
            fun s(k: String) = o.get(k)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
            return NtfyAction(s("action"), s("label"), s("url"), s("method").ifEmpty { "POST" }.uppercase(), s("body"), o.get("clear")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false)
        }
    }
}

/**
 * Whether a button on a message may be shown and pressed. A message on the topic is text from outside, so a button is only ever the extension's own kind:
 * a POST of an Acknowledge or Snooze command with a ticket, to this paired topic and nowhere else, and not past its end.
 */
object SafeAction {
    data class Accepted(val label: String, val ticket: Ticket.Parsed)

    fun accept(a: NtfyAction, topicUrl: String, nowSeconds: Long): Accepted? {
        if (!a.action.equals("http", ignoreCase = true) || a.method != "POST") return null
        if (a.url.trimEnd('/') != topicUrl.trimEnd('/')) return null
        val t = Ticket.parseCommand(a.body) ?: return null
        if (t.expiry < nowSeconds) return null
        return Accepted(a.label.ifBlank { t.command.replaceFirstChar { it.uppercase() } }, t)
    }

    /** All the buttons of a message that may be shown. */
    fun accepted(m: NtfyMessage, topicUrl: String, nowSeconds: Long): List<Accepted> = m.actions.mapNotNull { accept(it, topicUrl, nowSeconds) }
}

package com.kontrolcore.mobile.core

/**
 * What a message on the topic means. The extension writes alerts as a title and a body (buildMessage in background/alerts.js) and answers commands with a
 * title that starts "KontrolCore:". This reads those, so the inbox can say which scenario needs the person and why.
 */
enum class AlertKind { FAILING, NEEDS_LOOK, QUIET, ATTENTION, RECOVERED, INFO, REPLY, COMMAND }

data class Alert(
    val kind: AlertKind,
    /** The scenario or suite the alert is about; empty for a daily summary or a reply. */
    val name: String,
    val title: String,
    /** The failed step and its detail, as the alert wrote them; empty if it named none. */
    val step: String,
    val stepDetail: String,
    val url: String,
    val body: String,
    val time: Long,
    val messageId: String,
) {
    /** Whether this alert puts a scenario on the Needs-you list. */
    val needsYou: Boolean get() = kind == AlertKind.FAILING || kind == AlertKind.NEEDS_LOOK || kind == AlertKind.QUIET || kind == AlertKind.ATTENTION
}

object Alerts {
    private val QUOTED = Regex("""^"(.+?)"""")
    private val STILL_FAILING = Regex("""^Still failing: "(.+)"$""")
    private val SUITE = Regex("""^Suite (passed|failed|stopped): (.+)$""")

    /** The meaning of a message, or null when it is not about the person's scenarios (an unreadable event, say). */
    fun classify(m: NtfyMessage): Alert? {
        if (!m.isMessage) return null
        val title = m.title.trim()
        val body = m.message
        // What the person or the app sent to the topic is a command, not an alert.
        if (PhoneCommand.parse(body, true)?.code != null || Ticket.parseCommand(body) != null) {
            val t = Ticket.parseCommand(body)
            val p = if (t == null) PhoneCommand.parse(body, true) else null
            return base(AlertKind.COMMAND, t?.name ?: p?.name ?: "", m, body, "", "", "").copy(title = (t?.command ?: p?.command ?: "").uppercase())
        }
        if (title.startsWith("KontrolCore:")) return base(AlertKind.REPLY, "", m, title.removePrefix("KontrolCore:").trim(), "", "", body)
        val (step, detail) = stepOf(body)
        val url = urlOf(body)
        val suite = SUITE.find(title)
        if (suite != null) {
            val failed = suite.groupValues[1] != "passed"
            return base(if (failed) AlertKind.FAILING else AlertKind.INFO, suite.groupValues[2], m, title, step, detail, url).copy(step = body.lineSequence().firstOrNull()?.trim() ?: "")
        }
        val still = STILL_FAILING.find(title)
        if (still != null) return base(AlertKind.FAILING, still.groupValues[1], m, title, step, detail, url)
        val name = QUOTED.find(title)?.groupValues?.get(1)
        if (name != null) {
            val rest = title.substring(name.length + 2)
            val kind = when {
                rest.startsWith(" started failing") || rest.startsWith(" has failed ") || rest.startsWith(": a step you watch failed") -> AlertKind.FAILING
                rest.startsWith(" needs a look") -> AlertKind.NEEDS_LOOK
                rest.startsWith(" has gone quiet") -> AlertKind.QUIET
                rest.startsWith(" missed its scheduled time") || rest.startsWith(" ran out of run data") -> AlertKind.ATTENTION
                rest.startsWith(" recovered") -> AlertKind.RECOVERED
                rest.startsWith(" failed") && ("x" in m.tags) -> AlertKind.FAILING
                else -> AlertKind.INFO
            }
            return base(kind, name, m, title, step, detail, url)
        }
        return base(AlertKind.INFO, "", m, title.ifEmpty { "Alert" }, step, detail, url)
    }

    private fun base(kind: AlertKind, name: String, m: NtfyMessage, title: String, step: String, detail: String, url: String, body: String = m.message) =
        Alert(kind, name, title, step, detail, url, body, m.time, m.id)

    /** The step the alert names and its detail: the text after "Step: " (description, then the lines that follow). */
    fun stepOf(body: String): Pair<String, String> {
        val at = body.indexOf("Step: ")
        if (at < 0) return "" to ""
        val lines = body.substring(at + 6).split('\n')
        return lines.first().trim() to lines.drop(1).joinToString("\n").trim()
    }

    /** The page address the alert carries, if it has one. */
    fun urlOf(body: String): String = body.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("http://") || it.startsWith("https://") } ?: ""
}

/** One thing in the Needs-you inbox. */
data class InboxItem(
    val name: String,
    val kind: AlertKind,
    val since: Long,
    val last: Long,
    /** Alerts since it started. */
    val count: Int,
    val title: String,
    val step: String,
    val stepDetail: String,
    val url: String,
    val body: String,
    /** The person has said "acknowledge" or "snooze"; [until] is when a snooze ends (0 for an acknowledgement). */
    val acknowledged: Boolean = false,
    val until: Long = 0L,
) {
    /** A short line that says what is wrong, for the list. */
    val summary: String get() = when (kind) {
        AlertKind.QUIET -> "Has gone quiet"
        AlertKind.NEEDS_LOOK -> "Needs a look"
        AlertKind.ATTENTION -> title.removePrefix("\"$name\"").trim().replaceFirstChar { it.uppercase() }
        else -> if (step.isNotEmpty()) step else "Failing"
    }

    /** A plain text summary, for Android's share sheet. */
    fun shareText(): String = buildString {
        append(name).append(": ").append(title).append('\n')
        if (step.isNotEmpty()) append("Step: ").append(step).append('\n')
        if (stepDetail.isNotEmpty()) append(stepDetail).append('\n')
        if (url.isNotEmpty()) append(url).append('\n')
        append(count).append(if (count == 1) " alert" else " alerts")
    }.trimEnd()
}

/**
 * The Needs-you list, worked out from the messages on the topic in the order they came. A scenario is on it from an alert that says it is failing, needs a look
 * or has gone quiet, until an alert says it recovered. ACK and SNOOZE commands seen on the topic (from this app or a button) mark it acknowledged; a snooze
 * ends at its own time, after which it counts again. Pure, so the same messages always give the same list.
 */
object Inbox {
    fun build(messages: List<NtfyMessage>, nowSeconds: Long): List<InboxItem> {
        val items = LinkedHashMap<String, InboxItem>()
        for (m in messages.sortedBy { it.time }) {
            val a = Alerts.classify(m) ?: continue
            val key = a.name.lowercase().trim()
            when {
                a.kind == AlertKind.COMMAND -> {
                    val cmd = a.title.lowercase()
                    val cur = items[key] ?: continue
                    if (cmd == "ack") items[key] = cur.copy(acknowledged = true, until = 0L)
                    if (cmd == "snooze") {
                        val hours = PhoneCommand.parse(m.message, true)?.hours ?: 1
                        items[key] = cur.copy(acknowledged = true, until = m.time + hours * 3600L)
                    }
                }
                a.kind == AlertKind.RECOVERED -> items.remove(key)
                a.needsYou && key.isNotEmpty() -> {
                    val cur = items[key]
                    items[key] = if (cur == null) InboxItem(a.name, a.kind, a.time, a.time, 1, a.title, a.step, a.stepDetail, a.url, a.body)
                    else cur.copy(
                        kind = a.kind, last = a.time, count = cur.count + 1, title = a.title,
                        step = a.step.ifEmpty { cur.step }, stepDetail = if (a.step.isEmpty()) cur.stepDetail else a.stepDetail,
                        url = a.url.ifEmpty { cur.url }, body = a.body,
                        // A new alert after an acknowledgement is news again, unless the snooze is still running.
                        acknowledged = cur.acknowledged && cur.until > a.time,
                    )
                }
            }
        }
        return items.values
            .map { if (it.acknowledged && it.until in 1..nowSeconds) it.copy(acknowledged = false, until = 0L) else it }
            .sortedWith(compareBy<InboxItem> { it.acknowledged }.thenByDescending { it.last })
    }
}

package com.kontrolcore.mobile.core

/**
 * The phone-command grammar the extension reads from its ntfy topic (parsePhoneCommand in alert-rules.js, background/remote-control.js):
 *
 *     RUN Checkout flow #482913        START | RUN | PAUSE | STOP | RESET | ROW | ACK | SNOOZE <name> #<code>
 *     SNOOZE 2h Checkout flow #482913  an optional "<n>h" (1 to 24, default 1) after SNOOZE
 *     STATUS #482913                   STATUS needs no name
 *
 * The command word is not case sensitive; the code is the six digits of a one-time code (Totp) after a space and a #. The extension refuses a message
 * whose code is missing, wrong or already used, so the app asks for a fresh code (waiting for the next step if the current one is nearly over).
 */
object PhoneCommand {
    val COMMANDS = listOf("start", "run", "pause", "stop", "reset", "row", "ack", "snooze", "status")

    data class Parsed(val command: String, val name: String, val hours: Int, val code: String?)

    private val CODE = Regex("""\s+#(\d{6})$""")
    private val WORD = Regex("""^(start|run|pause|stop|reset|row|ack|snooze|status)(?:\s+([\s\S]*))?$""", RegexOption.IGNORE_CASE)
    private val HOURS = Regex("""^(\d{1,2})\s*h\s+([\s\S]+)$""", RegexOption.IGNORE_CASE)

    /** What the extension would make of a message. Mirrors parsePhoneCommand; `hasCode` is whether one-time codes are set up on that side. */
    fun parse(message: String?, hasCode: Boolean): Parsed? {
        var text = (message ?: "").trim()
        var code: String? = null
        if (hasCode) {
            val m = CODE.find(text)
            if (m != null) {
                code = m.groupValues[1]
                text = text.substring(0, m.range.first).trim()
            }
        }
        val c = WORD.find(text) ?: return null
        val command = c.groupValues[1].lowercase()
        var rest = c.groupValues[2].trim()
        var hours = 1
        if (command == "snooze") {
            val h = HOURS.find(rest)
            if (h != null) {
                hours = h.groupValues[1].toInt().coerceIn(1, 24)
                rest = h.groupValues[2].trim()
            }
        }
        if (command != "status" && rest.isEmpty()) return null
        return Parsed(command, rest, hours, code)
    }

    /**
     * The message to send for a command, ending with a one-time code. `name` is the scenario, suite or "Project/Name" the command is about (empty only for
     * status); `hours` is used by snooze alone. The result is what [parse] reads back, which the tests check against the extension's own parser.
     */
    fun format(command: String, name: String, hours: Int, code: String): String {
        val cmd = command.lowercase()
        require(cmd in COMMANDS) { "not a phone command: $command" }
        require(Regex("""^\d{6}$""").matches(code)) { "a one-time code is six digits" }
        val target = name.trim()
        require(cmd == "status" || target.isNotEmpty()) { "$command needs a name" }
        val body = buildString {
            append(cmd.uppercase())
            if (cmd == "snooze" && hours != 1) append(" ").append(hours.coerceIn(1, 24)).append("h")
            if (target.isNotEmpty()) append(" ").append(target)
            append(" #").append(code)
        }
        return body
    }

    /** The same, making the code from the secret at a moment (unix seconds). */
    fun formatAt(command: String, name: String, hours: Int, secret32: String, timeSeconds: Long): String =
        format(command, name, hours, Totp.codeAt(secret32, timeSeconds))
}

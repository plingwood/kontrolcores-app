package com.kontrolcore.mobile.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The tickets on the Acknowledge and Snooze buttons of a phone alert (totp.js makeTicket / checkTicket, parseTicketCommand in alert-rules.js, KON-490):
 *
 *     ACK Checkout flow ~65f3a1b2.ABCDEFGHIJKLMNOP
 *
 * A button cannot hold a fresh one-time code, so it carries a ticket: an HMAC (made with the setup key) of the command, the scenario name and an expiry,
 * good for one command on one scenario, once, for six hours. Only the computer that made it can really check it (and keeps the "once"); the app can read
 * one, see when it ends, and, because it holds the same key, check that it is genuine.
 */
object Ticket {
    const val LIFE_SECONDS = 6 * 3600L

    data class Parsed(val command: String, val name: String, val ticket: String) {
        /** The text of the command, as the extension reads it. */
        val message: String get() = "${command.uppercase()} $name ~$ticket"
        /** When the ticket ends, unix seconds. */
        val expiry: Long get() = expiryOf(ticket) ?: 0L
    }

    private val COMMAND = Regex("""^(ack|snooze)\s+([\s\S]*?)\s+~([0-9a-f]{1,12}\.[A-Z2-7]{16})\s*$""", RegexOption.IGNORE_CASE)
    private val TICKET = Regex("""^([0-9a-f]{1,12})\.([A-Z2-7]{16})$""")
    private val SPACES = Regex("""\s+""")

    /** A button's command, or null when the message is not one. Mirrors parseTicketCommand. */
    fun parseCommand(message: String?): Parsed? {
        val m = COMMAND.find((message ?: "").trim()) ?: return null
        val name = m.groupValues[2].trim().replace(SPACES, " ")
        if (name.isEmpty()) return null
        val raw = m.groupValues[3]
        val ticket = raw.substringBefore('.').lowercase() + "." + raw.substringAfter('.').uppercase()
        return Parsed(m.groupValues[1].lowercase(), name, ticket)
    }

    /** When a ticket ends (unix seconds), from its own text; null if it is not shaped like a ticket. */
    fun expiryOf(ticket: String?): Long? {
        val m = TICKET.find(ticket ?: "") ?: return null
        return m.groupValues[1].toLongOrNull(16)
    }

    fun cleanName(name: String?): String = (name ?: "").trim().replace(SPACES, " ").lowercase()

    private fun mac(secret32: String, command: String, name: String, expiry: Long): String? {
        val key = Base32.decode(secret32) ?: return null
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(key, "HmacSHA256"))
        val data = "kontrol-ticket|${command.lowercase()}|${cleanName(name)}|$expiry".toByteArray(Charsets.UTF_8)
        return Base32.encode(hmac.doFinal(data).copyOf(10))
    }

    /** A ticket for one command on one name, good for six hours from [nowSeconds]. The extension makes these; the app does so only in its tests. */
    fun make(secret32: String, command: String, name: String, nowSeconds: Long): String? {
        val expiry = nowSeconds + LIFE_SECONDS
        return mac(secret32, command, name, expiry)?.let { "${expiry.toString(16)}.$it" }
    }

    /** Whether the ticket is genuine for this command and name and not past its end at [atSeconds]. Single use is the extension's job. */
    fun check(secret32: String, command: String, name: String, ticket: String?, atSeconds: Long): Boolean {
        val m = TICKET.find(ticket ?: "") ?: return false
        val expiry = m.groupValues[1].toLongOrNull(16) ?: return false
        if (expiry < atSeconds || expiry > atSeconds + LIFE_SECONDS + 60) return false
        val want = mac(secret32, command, name, expiry) ?: return false
        val got = m.groupValues[2]
        var diff = want.length xor got.length
        for (i in want.indices) diff = diff or (want[i].code xor (got.getOrNull(i)?.code ?: 0))
        return diff == 0
    }
}

package com.kontrolcore.mobile.core

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the person's ntfy topic: reads alerts as a stream, publishes commands. Plain HttpURLConnection so it runs on the JVM and on Android.
 * [base] is the person's own server or https://ntfy.sh. Nothing is sent anywhere else.
 */
class NtfyClient(private val topicUrl: String, private val userAgent: String = "KontrolCore-Mobile") {
    class HttpError(val code: Int, message: String) : Exception(message)

    /** The stream address: everything since a message id (or "all" / "30m" / a unix time), as JSON lines. */
    fun streamUrl(since: String): String = "$topicUrl/json?since=${NtfyAddress.encodeComponent(since)}"

    /**
     * Reads the stream until [shouldStop] says so, the server closes it, or a read fails (the caller reconnects). Each message line goes to [onMessage]
     * (open and keepalive events are not messages). Returns normally when the server ends the stream; throws on a network error or a refusal.
     */
    fun stream(since: String, shouldStop: () -> Boolean, onConnected: () -> Unit = {}, onMessage: (NtfyMessage) -> Unit) {
        val conn = URL(streamUrl(since)).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 95_000 // ntfy sends a keepalive every 30 seconds
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/x-ndjson")
            val code = conn.responseCode
            if (code != 200) throw HttpError(code, "ntfy answered $code")
            onConnected()
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { r ->
                while (!shouldStop()) {
                    val line = r.readLine() ?: break
                    val m = NtfyMessage.parse(line) ?: continue
                    if (m.isMessage) onMessage(m)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Messages since a point, read once (no stream): used to catch up and to look for a reply. */
    fun poll(since: String): List<NtfyMessage> {
        val conn = URL("$topicUrl/json?poll=1&since=${NtfyAddress.encodeComponent(since)}").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", userAgent)
            val code = conn.responseCode
            if (code != 200) throw HttpError(code, "ntfy answered $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.mapNotNull { NtfyMessage.parse(it) }.filter { it.isMessage }.toList() }
        } finally {
            conn.disconnect()
        }
    }

    /** Sends [text] to the topic. Throws [HttpError] if ntfy refuses it. */
    fun publish(text: String) {
        val conn = URL(topicUrl).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            conn.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) throw HttpError(code, "ntfy answered $code")
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Sends commands with a fresh one-time code. The extension accepts each code once and checks it against the time of the message, so this never sends a code
 * that is about to end, and never two commands in one 30-second step (the second would be refused as a reused code): it waits for the next step.
 */
class CommandSender(
    private val secret32: String,
    private val publish: (String) -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    /** Seconds left in a step below which the app waits for the next one. */
    private val margin: Long = 3,
    /** The step the last code was used in, if an earlier sender used one: the app makes a new sender after the key has been locked again. */
    lastCounter: Long = Long.MIN_VALUE,
) {
    /** The step of the last code sent (Long.MIN_VALUE before any). */
    var lastCounter: Long = lastCounter
        private set

    data class Sent(val message: String, val at: Long, val waitedSeconds: Long)

    @Synchronized
    fun send(command: String, name: String, hours: Int = 1): Sent {
        var now = clock()
        var waited = 0L
        if (Totp.secondsLeft(now) <= margin || Totp.counterAt(now) == lastCounter) {
            val wait = Totp.secondsLeft(now) + 1
            sleep(wait * 1000)
            waited = wait
            now = clock()
            // A clock that did not move (a test, a paused emulator) must not loop or reuse the step.
            if (Totp.counterAt(now) == lastCounter) now = (lastCounter + 1) * Totp.STEP_SECONDS
        }
        val message = PhoneCommand.formatAt(command, name, hours, secret32, now)
        publish(message)
        lastCounter = Totp.counterAt(now)
        return Sent(message, now, waited)
    }
}

package com.kontrolcore.mobile.core

import java.net.URI
import java.net.URLDecoder

/**
 * What the pairing QR code holds (pairingUri in alert-rules.js, KON-491):
 *
 *     kontrolcore://pair?v=1&topic=<topic>&secret=<base32 setup key>[&server=<https address of the person's own ntfy server>]
 *
 * The app reads it once and keeps the secret in the Keystore. [server] is null for the public ntfy.sh. Anything that is not exactly this is refused: a QR code
 * is an input from outside, so a wrong version, a missing part, a bad key or an address that is not https never pairs.
 */
data class PairingLink(val topic: String, val secret: String, val server: String?) {
    /** The ntfy base address to use: the person's own server, else the public one. */
    val base: String get() = server ?: NtfyAddress.DEFAULT

    /** The address of this topic: where alerts are read and commands are sent. */
    val topicUrl: String get() = NtfyAddress.topicUrl(base, topic)

    sealed class Result {
        data class Ok(val link: PairingLink) : Result()
        data class Refused(val why: String) : Result()
    }

    companion object {
        const val SCHEME = "kontrolcore"

        /** The link in [text], or null when it is refused. */
        fun parse(text: String?): PairingLink? = (check(text) as? Result.Ok)?.link

        fun check(text: String?): Result {
            val t = (text ?: "").trim()
            val prefix = "$SCHEME://pair?"
            if (!t.startsWith(prefix, ignoreCase = true)) return Result.Refused("That is not a KontrolCore pairing code.")
            val params = HashMap<String, String>()
            for (part in t.substring(prefix.length).split('&')) {
                if (part.isEmpty()) continue
                val k = part.substringBefore('=', "").lowercase()
                if (k.isEmpty()) continue
                val v = try { URLDecoder.decode(part.substringAfter('=', "").replace("+", "%2B"), "UTF-8") } catch (e: IllegalArgumentException) {
                    return Result.Refused("The pairing code is damaged.")
                }
                params[k] = v
            }
            if (params["v"] != "1") return Result.Refused("This pairing code is from a different version. Update the app or KontrolCore.")
            val topic = (params["topic"] ?: "").trim()
            if (topic.isEmpty() || topic.length > 200 || topic.any { it.isISOControl() }) return Result.Refused("The pairing code has no topic.")
            val secretText = (params["secret"] ?: "").filter { !it.isWhitespace() }.uppercase()
            if (secretText.isEmpty() || Base32.decode(secretText) == null) return Result.Refused("The pairing code has no valid setup key.")
            val serverText = (params["server"] ?: "").trim()
            var server: String? = null
            if (serverText.isNotEmpty()) {
                val base = NtfyAddress.checkServer(serverText) ?: return Result.Refused("The pairing code names a server that is not a plain https address.")
                if (base != NtfyAddress.DEFAULT) server = base
            }
            return Result.Ok(PairingLink(topic, secretText, server))
        }
    }
}

/** Addresses on an ntfy server. Mirrors checkNtfyServer in alert-rules.js: https only, no user name, no query, no trailing slash. */
object NtfyAddress {
    const val DEFAULT = "https://ntfy.sh"

    /** The base address for a server the person typed or a pairing code carried, or null when it is not acceptable. Empty means the public server. */
    fun checkServer(value: String?): String? {
        val text = (value ?: "").trim()
        if (text.isEmpty()) return DEFAULT
        val u = try { URI(text) } catch (e: Exception) { return null }
        if (!"https".equals(u.scheme, ignoreCase = true) || u.host.isNullOrEmpty()) return null
        if (u.rawUserInfo != null || u.rawQuery != null || u.rawFragment != null) return null
        val port = if (u.port >= 0) ":${u.port}" else ""
        val path = (u.rawPath ?: "").trimEnd('/')
        return "https://${u.host.lowercase()}$port$path"
    }

    /** How the extension writes a topic into the address (encodeURIComponent). */
    fun encodeComponent(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xff
            if ((c in 'A'.code..'Z'.code) || (c in 'a'.code..'z'.code) || (c in '0'.code..'9'.code) || "-_.!~*'()".indexOf(c.toChar()) >= 0) sb.append(c.toChar())
            else sb.append('%').append("%02X".format(c))
        }
        return sb.toString()
    }

    fun topicUrl(base: String, topic: String): String = "${base.trimEnd('/')}/${encodeComponent(topic.trim())}"
}

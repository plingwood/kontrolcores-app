package com.kontrolcore.mobile.core

import java.net.URLDecoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * One-time codes, RFC 6238 (TOTP) with SHA-1, 6 digits and a 30 second step: the codes an authenticator app shows and the extension accepts at the end of a
 * phone command (totp.js, KON-228). The phone holds the secret and adds a fresh code to every command, so nothing is typed.
 * The extension checks a code against the time of the message and accepts each step once; this side only has to make the code for a moment.
 */
object Totp {
    const val STEP_SECONDS = 30L
    const val DIGITS = 6

    /** The step a moment (unix seconds) falls in. */
    fun counterAt(timeSeconds: Long): Long = Math.floorDiv(timeSeconds, STEP_SECONDS)

    /** The code for one counter value (RFC 4226). */
    fun hotp(key: ByteArray, counter: Long): String {
        val msg = ByteArray(8)
        var c = counter
        for (i in 7 downTo 0) {
            msg[i] = (c and 0xff).toByte()
            c = c ushr 8
        }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val h = mac.doFinal(msg)
        val off = h[h.size - 1].toInt() and 15
        val bin = ((h[off].toInt() and 0x7f) shl 24) or
            ((h[off + 1].toInt() and 0xff) shl 16) or
            ((h[off + 2].toInt() and 0xff) shl 8) or
            (h[off + 3].toInt() and 0xff)
        var n = bin.toLong()
        var digits = 1L
        repeat(DIGITS) { digits *= 10 }
        n %= digits
        return n.toString().padStart(DIGITS, '0')
    }

    /** The code a person's authenticator shows at a moment (unix seconds), from a base32 secret. */
    fun codeAt(secret32: String, timeSeconds: Long): String {
        val key = Base32.decode(secret32) ?: throw IllegalArgumentException("not a valid setup key")
        return hotp(key, counterAt(timeSeconds))
    }

    /** Whole seconds left in the current step, so the app can wait for a fresh code instead of sending one that is about to end. */
    fun secondsLeft(timeSeconds: Long): Long = STEP_SECONDS - Math.floorMod(timeSeconds, STEP_SECONDS)

    /**
     * The secret out of the text a QR code carries, an otpauth:// address
     * (otpauth://totp/KontrolCore:Name?secret=ABC...&issuer=KontrolCore&algorithm=SHA1&digits=6&period=30), or null if it is not one for the codes this
     * app can make (TOTP, SHA-1, 6 digits, 30 seconds; anything the address leaves out takes those values).
     */
    fun secretFromUri(uri: String): String? {
        val text = uri.trim()
        if (!text.startsWith("otpauth://totp/", ignoreCase = true)) return null
        val query = text.substringAfter('?', "")
        if (query.isEmpty()) return null
        val params = query.split('&').mapNotNull {
            val k = it.substringBefore('=', "")
            if (k.isEmpty()) null else k.lowercase() to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }.toMap()
        val secret = params["secret"] ?: return null
        if (Base32.decode(secret) == null) return null
        val algorithm = (params["algorithm"] ?: "SHA1").uppercase()
        val digits = params["digits"]?.toIntOrNull() ?: DIGITS
        val period = params["period"]?.toLongOrNull() ?: STEP_SECONDS
        if (algorithm != "SHA1" || digits != DIGITS || period != STEP_SECONDS) return null
        return secret.filter { !it.isWhitespace() }.uppercase()
    }
}

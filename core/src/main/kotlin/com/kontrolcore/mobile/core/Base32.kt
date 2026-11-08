package com.kontrolcore.mobile.core

/** Base32 (RFC 4648), the form authenticator secrets are written in. Mirrors encode32 and decode32 in the extension's totp.js. */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Bytes to a base32 string, no padding. */
    fun encode(bytes: ByteArray): String {
        var bits = 0
        var value = 0
        val out = StringBuilder()
        for (b in bytes) {
            value = (value shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(value ushr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(value shl (5 - bits)) and 31])
        return out.toString()
    }

    /** A base32 string (case, spaces and = padding ignored) to bytes, or null if it is empty or not base32. */
    fun decode(text: String?): ByteArray? {
        val s = (text ?: "").filter { !it.isWhitespace() && it != '=' }.uppercase()
        if (s.isEmpty()) return null
        var bits = 0
        var value = 0
        val out = ArrayList<Byte>()
        for (ch in s) {
            val i = ALPHABET.indexOf(ch)
            if (i < 0) return null
            value = (value shl 5) or i
            bits += 5
            if (bits >= 8) {
                out.add(((value ushr (bits - 8)) and 255).toByte())
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}

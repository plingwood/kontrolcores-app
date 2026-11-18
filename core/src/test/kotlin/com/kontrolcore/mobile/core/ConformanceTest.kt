package com.kontrolcore.mobile.core

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The app against the extension: every case here was made by the extension's own code (tools/mobile-vectors.js in the KontrolCore extension repository). */
class ConformanceTest {
    private val vectors: JsonObject = ConformanceTest::class.java.getResourceAsStream("/vectors.json")!!
        .bufferedReader().use { JsonParser.parseString(it.readText()).asJsonObject }

    private fun list(name: String): List<JsonObject> = (vectors[name] as JsonArray).map { it.asJsonObject }
    private fun JsonElement.orNull(): JsonElement? = if (isJsonNull) null else this
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test
    fun oneTimeCodesMatchTheExtension() {
        val cases = list("totp")
        assertTrue(cases.size >= 50, "expected many cases, got ${cases.size}")
        for (c in cases) {
            val secret = c["secret"].asString
            val time = c["time"].asLong
            assertEquals(c["counter"].asLong, Totp.counterAt(time), "counter at $time")
            assertEquals(c["code"].asString, Totp.codeAt(secret, time), "code for '$secret' at $time")
        }
    }

    @Test
    fun base32DecodesLikeTheExtension() {
        for (c in list("decode")) {
            val text = c["text"].asString
            val want = c["hex"].orNull()?.asString
            val got = Base32.decode(text)?.let { hex(it) }
            assertEquals(want, got, "decode '$text'")
        }
    }

    @Test
    fun base32EncodesLikeTheExtension() {
        for (c in list("encode")) {
            val h = c["hex"].asString
            val bytes = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            assertEquals(c["text"].asString, Base32.encode(bytes), "encode $h")
        }
    }

    @Test
    fun commandsAreReadLikeTheExtensionReadsThem() {
        val cases = list("parse")
        assertTrue(cases.size >= 60, "expected many cases, got ${cases.size}")
        for (c in cases) {
            val message = c["message"].asString
            val hasCode = c["hasCode"].asBoolean
            val want = c["result"].orNull()?.asJsonObject
            val got = PhoneCommand.parse(message, hasCode)
            if (want == null) {
                assertNull(got, "'$message' (codes ${if (hasCode) "on" else "off"}) should not be a command")
            } else {
                assertNotNull(got, "'$message' (codes ${if (hasCode) "on" else "off"}) should be a command")
                assertEquals(want["command"].asString, got.command, message)
                assertEquals(want["name"].asString, got.name, message)
                assertEquals(want["hours"].asInt, got.hours, message)
                assertEquals(want["code"].orNull()?.asString, got.code, message)
            }
        }
    }

    @Test
    fun whatTheAppSendsIsWhatTheExtensionAccepts() {
        for (c in list("format")) {
            val message = PhoneCommand.format(c["command"].asString, c["name"].asString, c["hours"].asInt, c["code"].asString)
            assertEquals(c["message"].asString, message)
            val back = PhoneCommand.parse(message, hasCode = true)
            assertNotNull(back, message)
            assertEquals(c["command"].asString.lowercase(), back.command)
            assertEquals(c["name"].asString, back.name)
            assertEquals(c["code"].asString, back.code)
        }
    }
}

class UnitTest {
    @Test
    fun rfc6238SecretGivesTheDocumentedCodes() {
        // RFC 6238 appendix B, SHA-1, the last 6 of the 8 digit values (94287082 -> 287082, 07081804 -> 081804).
        val secret = Base32.encode("12345678901234567890".toByteArray())
        assertEquals("287082", Totp.codeAt(secret, 59))
        assertEquals("081804", Totp.codeAt(secret, 1111111109))
    }

    @Test
    fun secondsLeftCountsDownToTheNextStep() {
        assertEquals(30, Totp.secondsLeft(0))
        assertEquals(1, Totp.secondsLeft(29))
        assertEquals(30, Totp.secondsLeft(30))
        assertEquals(1, Totp.secondsLeft(59))
    }

    @Test
    fun theSecretIsReadOutOfAnOtpauthAddress() {
        val uri = "otpauth://totp/KontrolCore:Phone?secret=jbsw%20y3dp&issuer=KontrolCore&algorithm=SHA1&digits=6&period=30"
        assertEquals("JBSWY3DP", Totp.secretFromUri(uri))
        assertEquals("JBSWY3DP", Totp.secretFromUri("otpauth://totp/x?secret=JBSWY3DP"))
        assertNull(Totp.secretFromUri("otpauth://hotp/x?secret=JBSWY3DP"))
        assertNull(Totp.secretFromUri("otpauth://totp/x?secret=JBSWY3DP&digits=8"))
        assertNull(Totp.secretFromUri("otpauth://totp/x?secret=JBSWY3DP&algorithm=SHA256"))
        assertNull(Totp.secretFromUri("otpauth://totp/x?secret=NOT*BASE32"))
        assertNull(Totp.secretFromUri("otpauth://totp/x"))
        assertNull(Totp.secretFromUri("https://example.com/?secret=JBSWY3DP"))
    }

    @Test
    fun badInputIsRefusedClearly() {
        assertFailsWith<IllegalArgumentException> { Totp.codeAt("not base32!", 0) }
        assertFailsWith<IllegalArgumentException> { PhoneCommand.format("dance", "x", 1, "123456") }
        assertFailsWith<IllegalArgumentException> { PhoneCommand.format("run", "x", 1, "12345") }
        assertFailsWith<IllegalArgumentException> { PhoneCommand.format("run", "  ", 1, "123456") }
    }

    @Test
    fun statusNeedsNoNameButEveryOtherCommandDoes() {
        assertEquals("STATUS #123456", PhoneCommand.format("status", "", 1, "123456"))
        assertEquals("SNOOZE 3h Checkout #123456", PhoneCommand.format("snooze", "Checkout", 3, "123456"))
        assertEquals("SNOOZE Checkout #123456", PhoneCommand.format("snooze", "Checkout", 1, "123456"))
    }
}

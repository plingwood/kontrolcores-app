package com.kontrolcore.mobile.core

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pairing, tickets, alerts, the inbox and the ntfy client: the parts the app is made of that need no Android. */
class AppProtocolTest {
    private val vectors: JsonObject = AppProtocolTest::class.java.getResourceAsStream("/vectors.json")!!
        .bufferedReader().use { JsonParser.parseString(it.readText()).asJsonObject }
    private fun list(name: String): List<JsonObject> = (vectors[name] as JsonArray).map { it.asJsonObject }
    private fun JsonElement.orNull(): JsonElement? = if (isJsonNull) null else this

    @Test
    fun ticketsAreTheOnesTheExtensionMakes() {
        val cases = list("tickets")
        assertTrue(cases.size >= 10)
        for (c in cases) {
            val secret = c["secret"].asString; val command = c["command"].asString; val name = c["name"].asString; val ticket = c["ticket"].asString
            assertEquals(ticket, Ticket.make(secret, command, name, 1731024000), "ticket for $command $name")
            for (k in c["checks"].asJsonArray) {
                val at = k.asJsonObject["at"].asLong
                assertEquals(k.asJsonObject["ok"].asBoolean, Ticket.check(secret, command, name, ticket, at), "check at $at")
            }
            assertEquals(c["wrongName"].asBoolean, Ticket.check(secret, command, name + " x", ticket, 1731024000))
            assertEquals(c["wrongCommand"].asBoolean, Ticket.check(secret, if (command == "ack") "snooze" else "ack", name, ticket, 1731024000))
            assertEquals(c["wrongSecret"].asBoolean, Ticket.check("KRSXG5CTMVRXEZLU", command, name, ticket, 1731024000))
        }
    }

    @Test
    fun ticketCommandsAreReadLikeTheExtensionReadsThem() {
        for (c in list("ticketCommands")) {
            val want = c["result"].orNull()?.asJsonObject
            val got = Ticket.parseCommand(c["message"].asString)
            if (want == null) assertNull(got, "message '${c["message"].asString}'") else {
                assertNotNull(got, "message '${c["message"].asString}'")
                assertEquals(want["command"].asString, got.command)
                assertEquals(want["name"].asString, got.name)
                assertEquals(want["ticket"].asString, got.ticket)
            }
        }
    }

    @Test
    fun pairingLinksAreReadLikeTheExtensionWritesThem() {
        for (c in list("pairing")) {
            val uri = c["uri"].asString
            val got = PairingLink.parse(uri)
            if (uri.isEmpty()) { assertNull(got); continue }
            assertNotNull(got, uri)
            assertEquals(c["topic"].asString.trim(), got.topic)
            assertEquals(c["secret"].asString.filter { !it.isWhitespace() }.uppercase(), got.secret)
            // The extension drops the public server and a server that is not https, so those pair as the public one.
            val server = c["server"]?.asString
            val want = if (server != null && server.startsWith("https://") && server != "https://ntfy.sh") server.trimEnd('/') else null
            assertEquals(want, got.server, uri)
        }
    }

    @Test
    fun pairingRefusesWhatItShould() {
        val ok = "kontrolcore://pair?v=1&topic=t&secret=JBSWY3DPEHPK3PXP"
        assertNotNull(PairingLink.parse(ok))
        for (bad in listOf(
            "", "hello", "https://pair?v=1&topic=t&secret=JBSWY3DPEHPK3PXP", "kontrolcore://pair?v=2&topic=t&secret=JBSWY3DPEHPK3PXP",
            "kontrolcore://pair?topic=t&secret=JBSWY3DPEHPK3PXP", "kontrolcore://pair?v=1&secret=JBSWY3DPEHPK3PXP", "kontrolcore://pair?v=1&topic=t",
            "kontrolcore://pair?v=1&topic=t&secret=not%20base32!", "$ok&server=http%3A%2F%2Fx.example.com", "$ok&server=https%3A%2F%2Fu%3Ap%40x.example.com",
            "$ok&server=https%3A%2F%2Fx.example.com%2F%3Fa%3D1", "$ok&server=nonsense", "kontrolcore://pair?v=1&topic=%ZZ&secret=JBSWY3DPEHPK3PXP",
        )) assertNull(PairingLink.parse(bad), "should refuse: $bad")
        val p = PairingLink.parse("$ok&server=https%3A%2F%2Fntfy.example.com%2Fteam%2F")!!
        assertEquals("https://ntfy.example.com/team", p.base)
        assertEquals("https://ntfy.example.com/team/t", p.topicUrl)
        assertEquals("https://ntfy.sh/my%20topic%2Fx", PairingLink("my topic/x", "JBSWY3DPEHPK3PXP", null).topicUrl)
    }

    private fun msg(title: String, body: String, time: Long, id: String = "id$time", tags: List<String> = listOf("x"), actions: List<NtfyAction> = emptyList()) =
        NtfyMessage(id, time, "message", title, body, tags, 4, actions)

    @Test
    fun alertsAreReadFromTheirTitles() {
        val failing = Alerts.classify(msg("\"Checkout flow\" started failing", "\"Checkout flow\" failed on:\nhttps://shop.example.com/cart\n\nStep: Click \"Pay\"\nElement not found", 100))!!
        assertEquals(AlertKind.FAILING, failing.kind)
        assertEquals("Checkout flow", failing.name)
        assertEquals("Click \"Pay\"", failing.step)
        assertEquals("Element not found", failing.stepDetail)
        assertEquals("https://shop.example.com/cart", failing.url)
        assertTrue(failing.needsYou)
        assertEquals(AlertKind.FAILING, Alerts.classify(msg("\"Login\" has failed 3 times in a row", "x", 1))!!.kind)
        assertEquals(AlertKind.FAILING, Alerts.classify(msg("Still failing: \"Login\"", "x", 1))!!.kind)
        assertEquals("Login", Alerts.classify(msg("Still failing: \"Login\"", "x", 1))!!.name)
        assertEquals(AlertKind.NEEDS_LOOK, Alerts.classify(msg("\"Login\" needs a look", "x", 1))!!.kind)
        assertEquals(AlertKind.QUIET, Alerts.classify(msg("\"Login\" has gone quiet", "x", 1))!!.kind)
        assertEquals(AlertKind.ATTENTION, Alerts.classify(msg("\"Login\" missed its scheduled time", "x", 1))!!.kind)
        assertEquals(AlertKind.RECOVERED, Alerts.classify(msg("\"Login\" recovered", "x", 1))!!.kind)
        assertEquals(AlertKind.INFO, Alerts.classify(msg("Daily summary", "all fine", 1))!!.kind)
        val suite = Alerts.classify(msg("Suite failed: Nightly", "1 of 4 failed", 1))!!
        assertEquals(AlertKind.FAILING, suite.kind); assertEquals("Nightly", suite.name)
        assertEquals(AlertKind.INFO, Alerts.classify(msg("Suite passed: Nightly", "ok", 1))!!.kind)
        assertEquals(AlertKind.REPLY, Alerts.classify(msg("KontrolCore: Too old", "Send it again", 1))!!.kind)
        assertEquals(AlertKind.COMMAND, Alerts.classify(msg("", "RUN Login #123456", 1))!!.kind)
        assertEquals(AlertKind.COMMAND, Alerts.classify(msg("", "ACK Login ~65f3a1b2.ABCDEFGHIJKLMNOP", 1))!!.kind)
        assertNull(Alerts.classify(NtfyMessage("a", 1, "keepalive", "", "", emptyList(), 3, emptyList())))
    }

    @Test
    fun theInboxFollowsFailuresAcknowledgementsAndRecovery() {
        val m = listOf(
            msg("\"Login\" started failing", "\"Login\" failed on:\nhttps://a.example\n\nStep: Type password", 100),
            msg("\"Checkout\" needs a look", "x", 110),
            msg("\"Login\" has failed 2 times in a row", "Latest run failed on:\n\nStep: Type password\nTimed out", 200),
        )
        var inbox = Inbox.build(m, 300)
        assertEquals(listOf("Login", "Checkout"), inbox.map { it.name })
        assertEquals(2, inbox[0].count)
        assertEquals("Type password", inbox[0].step)
        assertEquals("Timed out", inbox[0].stepDetail)
        assertEquals(100, inbox[0].since)
        assertTrue(inbox[0].shareText().contains("Timed out"))

        inbox = Inbox.build(m + msg("", "ACK Login ~65f3a1b2.ABCDEFGHIJKLMNOP", 250), 300)
        assertEquals(listOf("Checkout", "Login"), inbox.map { it.name }, "acknowledged sorts last")
        assertTrue(inbox[1].acknowledged)

        val snoozed = m + msg("", "SNOOZE 2h Login #123456", 250)
        assertTrue(Inbox.build(snoozed, 300).first { it.name == "Login" }.acknowledged)
        assertFalse(Inbox.build(snoozed, 250 + 2 * 3600 + 1).first { it.name == "Login" }.acknowledged, "a snooze ends")

        val again = Inbox.build(m + msg("", "ACK Login ~65f3a1b2.ABCDEFGHIJKLMNOP", 250) + msg("\"Login\" has failed 3 times in a row", "x", 260), 300)
        assertFalse(again.first { it.name == "Login" }.acknowledged, "new news after an acknowledgement counts again")

        inbox = Inbox.build(m + msg("\"Login\" recovered", "It is passing again.", 400), 500)
        assertEquals(listOf("Checkout"), inbox.map { it.name })
    }

    @Test
    fun onlyTheExtensionsOwnButtonsAreOffered() {
        val topic = "https://ntfy.sh/alerts"
        val ticket = Ticket.make("JBSWY3DPEHPK3PXP", "ack", "Login", 1000)!!
        val ok = NtfyAction("http", "Acknowledge", topic, "POST", "ACK Login ~$ticket", true)
        assertNotNull(SafeAction.accept(ok, topic, 1000))
        assertNull(SafeAction.accept(ok.copy(url = "https://evil.example/alerts"), topic, 1000), "another address")
        assertNull(SafeAction.accept(ok.copy(method = "GET"), topic, 1000))
        assertNull(SafeAction.accept(ok.copy(action = "view"), topic, 1000))
        assertNull(SafeAction.accept(ok.copy(body = "RUN Login #123456"), topic, 1000), "not a ticket command")
        assertNull(SafeAction.accept(ok, topic, 1000 + Ticket.LIFE_SECONDS + 1), "past its end")
        assertEquals("Acknowledge", SafeAction.accept(ok, "$topic/", 1000)!!.label)
    }

    @Test
    fun messagesAreReadFromTheStream() {
        val line = """{"id":"abc","time":1731024000,"event":"message","topic":"t","title":"T","message":"M","tags":["x","warning"],"priority":4,"actions":[{"id":"1","action":"http","label":"Acknowledge","url":"https://ntfy.sh/t","method":"POST","body":"ACK a ~1.AAAAAAAAAAAAAAAA","clear":true}]}"""
        val m = NtfyMessage.parse(line)!!
        assertEquals("abc", m.id); assertEquals(1731024000, m.time); assertEquals(listOf("x", "warning"), m.tags)
        assertEquals(1, m.actions.size); assertEquals("POST", m.actions[0].method); assertTrue(m.actions[0].clear)
        assertNull(NtfyMessage.parse("")); assertNull(NtfyMessage.parse("not json")); assertNull(NtfyMessage.parse("[1]"))
        assertFalse(NtfyMessage.parse("""{"id":"k","event":"keepalive","time":1}""")!!.isMessage)
    }

    @Test
    fun commandsGetAFreshCodeAndNeverShareAStep() {
        val secret = "JBSWY3DPEHPK3PXP"
        val sent = ArrayList<String>(); val slept = ArrayList<Long>()
        var now = 1731024000L + 5 // 5 seconds into a step
        val sender = CommandSender(secret, { sent.add(it) }, { now }, { slept.add(it); now += it / 1000 })
        val a = sender.send("run", "Checkout")
        assertEquals("RUN Checkout #${Totp.codeAt(secret, 1731024005)}", a.message)
        assertTrue(slept.isEmpty())
        val b = sender.send("stop", "Checkout") // same step: must wait for the next one
        assertEquals(1, slept.size)
        assertEquals("STOP Checkout #${Totp.codeAt(secret, now)}", b.message)
        assertTrue(Totp.counterAt(b.at) > Totp.counterAt(a.at))
        now = 1731024000L + 59 // one second left in a step: wait for the next
        val c = sender.send("ack", "Checkout")
        assertTrue(Totp.counterAt(c.at) > Totp.counterAt(b.at))
        assertTrue(Totp.secondsLeft(c.at) > 3)
        for (s in sent) assertNotNull(PhoneCommand.parse(s, true)?.code, s)
    }

    @Test
    fun clientStreamsPollsAndPublishes() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val posted = CopyOnWriteArrayList<String>()
        val urls = CopyOnWriteArrayList<String>()
        val line = """{"id":"m1","time":5,"event":"message","topic":"alerts","title":"\"A\" started failing","message":"body"}"""
        server.createContext("/alerts") { ex ->
            urls.add(ex.requestURI.toString())
            if (ex.requestMethod == "POST") {
                posted.add(ex.requestBody.readBytes().toString(Charsets.UTF_8))
                ex.sendResponseHeaders(200, 0); ex.responseBody.use { it.write("{}".toByteArray()) }
            } else {
                val out = ("""{"id":"o","time":1,"event":"open"}""" + "\n" + line + "\n" + """{"id":"k","time":6,"event":"keepalive"}""" + "\n").toByteArray()
                ex.sendResponseHeaders(200, out.size.toLong()); ex.responseBody.use { it.write(out) }
            }
        }
        server.start()
        try {
            val client = NtfyClient("http://127.0.0.1:${server.address.port}/alerts")
            val got = ArrayList<NtfyMessage>(); var connected = false
            client.stream("all", { false }, { connected = true }) { got.add(it) }
            assertTrue(connected); assertEquals(listOf("m1"), got.map { it.id })
            assertEquals(1, client.poll("m1").size)
            client.publish("RUN A #123456")
            assertEquals(listOf("RUN A #123456"), posted.toList())
            assertTrue(urls.any { it == "/alerts/json?since=all" }, urls.toString())
            assertTrue(urls.any { it == "/alerts/json?poll=1&since=m1" }, urls.toString())
        } finally { server.stop(0) }
    }
}

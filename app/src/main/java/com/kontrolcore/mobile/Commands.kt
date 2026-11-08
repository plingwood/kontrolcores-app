package com.kontrolcore.mobile

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import com.kontrolcore.mobile.core.CommandSender
import com.kontrolcore.mobile.core.NtfyClient
import java.util.concurrent.Executors

/**
 * Sends a command to the person's computer with a fresh one-time code: unlock, read the setup key from the Keystore, wait for a code that will last, post it
 * to the topic. The computer reads the topic about once a minute, so the answer is not instant: [Status] says "sent", then the reply (or its absence).
 */
object Commands {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var sender: CommandSender? = null
    private var senderAt = 0L
    private var lastCounter = Long.MIN_VALUE
    private const val REPLY_WAIT_MS = 100_000L

    sealed class Status {
        data class Working(val text: String) : Status()
        data class Done(val text: String) : Status()
        data class Failed(val text: String) : Status()
    }

    private fun senderFor(secret: String, paired: Paired): CommandSender {
        val now = System.currentTimeMillis()
        // The sender holds the key in memory; it is let go as soon as the unlock would have run out.
        if (sender == null || now - senderAt > Vault.AUTH_SECONDS * 1000L) {
            val client = NtfyClient(paired.topicUrl)
            sender = CommandSender(secret, { client.publish(it) }, lastCounter = lastCounter)
        }
        senderAt = now
        return sender!!
    }

    fun send(activity: FragmentActivity, command: String, name: String, hours: Int = 1, report: (Status) -> Unit) {
        val paired = Repo.paired()
        if (paired == null) { report(Status.Failed("This phone is not paired yet.")); return }
        val app: Context = activity.applicationContext
        fun attempt(forceUnlock: Boolean) {
            Gate.require(activity, "Send ${command.uppercase()} to KontrolCore", force = forceUnlock, onFail = { report(Status.Failed(it.ifEmpty { "Cancelled." })) }) {
                report(Status.Working("Sending…"))
                io.execute {
                    try {
                        val secret = Vault.secret(app)
                        val s = senderFor(secret, paired)
                        val sent = s.send(command, name, hours)
                        lastCounter = s.lastCounter
                        main.post { report(Status.Working("Sent. Waiting for your computer (it looks about once a minute)…")) }
                        waitForReply(sent.at, report)
                    } catch (e: Vault.NeedsUnlock) {
                        sender = null
                        main.post { if (!forceUnlock) attempt(true) else report(Status.Failed("Could not unlock. Try again.")) }
                    } catch (e: Vault.Lost) {
                        main.post { report(Status.Failed("The key on this phone can no longer be read. Pair again.")) }
                    } catch (e: NtfyClient.HttpError) {
                        main.post { report(Status.Failed("The alert server refused it (${e.code}).")) }
                    } catch (e: Exception) {
                        main.post { report(Status.Failed("Could not send: ${e.message ?: e.javaClass.simpleName}. Check the connection.")) }
                    }
                }
            }
        }
        attempt(false)
    }

    private fun waitForReply(sentAt: Long, report: (Status) -> Unit) {
        val deadline = System.currentTimeMillis() + REPLY_WAIT_MS
        val tick = object : Runnable {
            override fun run() {
                val reply = Repo.replySince(sentAt)
                when {
                    reply != null -> report(Status.Done("${reply.first}: ${reply.second}"))
                    System.currentTimeMillis() > deadline ->
                        report(Status.Done("Sent. No answer yet: the computer may be asleep or the browser closed. It will act when it next looks, unless the command is more than five minutes old."))
                    else -> main.postDelayed(this, 2000)
                }
            }
        }
        main.postDelayed(tick, 2000)
    }
}

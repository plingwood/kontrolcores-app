package com.kontrolcore.mobile

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.kontrolcore.mobile.core.PairingLink

/**
 * Pairing: scan the QR code KontrolCore shows (kontrolcore://pair?v=1&topic=...&secret=...[&server=...]) or paste its link. The person confirms what they are
 * pairing with, unlocks, and the setup key goes into the Keystore. The key is never shown again.
 */
class PairActivity : AppCompatActivity() {
    private val scanner = registerForActivityResult(ScanContract()) { result -> result.contents?.let { handle(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_pair)
        findViewById<Button>(R.id.scan).setOnClickListener {
            scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Point the camera at the code in KontrolCore").setBeepEnabled(false).setOrientationLocked(true))
        }
        findViewById<Button>(R.id.pasteGo).setOnClickListener { handle(findViewById<EditText>(R.id.link).text.toString()) }
    }

    private fun showError(text: String) = findViewById<TextView>(R.id.error).apply { this.text = text; visibility = if (text.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE }

    private fun handle(text: String) {
        when (val r = PairingLink.check(text)) {
            is PairingLink.Result.Refused -> showError(r.why)
            is PairingLink.Result.Ok -> {
                showError("")
                val link = r.link
                val where = link.server?.removePrefix("https://") ?: "ntfy.sh (the public server)"
                AlertDialog.Builder(this)
                    .setTitle("Pair this phone?")
                    .setMessage("It will read alerts from the topic \"${link.topic}\" on $where and be able to send commands to your computer. Only continue if you made this code yourself in KontrolCore.")
                    .setPositiveButton("Pair") { _, _ -> pair(link) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun pair(link: PairingLink) {
        Gate.require(this, "Unlock to keep the key safe", force = true, onFail = { if (it.isNotEmpty()) showError(it) }) {
            try {
                Vault.save(applicationContext, link.secret)
                Repo.savePaired(Paired(link.topic, link.server))
                ListenService.start(applicationContext)
                finish()
            } catch (e: Vault.NeedsUnlock) {
                showError("Unlock again to finish pairing.")
            } catch (e: Exception) {
                Vault.remove(applicationContext)
                showError("This phone could not keep the key safe: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}

package com.kontrolcore.mobile

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** The lock in front of the app and of the setup key: a fingerprint, face or screen lock. */
object Gate {
    private const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    private var unlockedAt = 0L

    /** Whether the person unlocked in the last [Vault.AUTH_SECONDS] seconds, so the key is usable without asking again. */
    fun fresh(): Boolean = System.currentTimeMillis() - unlockedAt < Vault.AUTH_SECONDS * 1000L - 2000L

    /** True if the phone has a screen lock or biometric the app can use. Without one the setup key could not be protected, so pairing is refused. */
    fun available(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun lock() { unlockedAt = 0L }

    /** Asks the person to unlock, unless they just did and [force] is off. [onFail] gets a plain-words reason, or "" when they simply backed out. */
    fun require(activity: FragmentActivity, title: String, force: Boolean = false, onFail: (String) -> Unit = {}, onOk: () -> Unit) {
        if (!force && fresh()) { onOk(); return }
        if (!available(activity)) { onFail("Set a screen lock (a PIN, pattern, password or fingerprint) on this phone first. KontrolCore keeps its key behind it."); return }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlockedAt = System.currentTimeMillis(); onOk() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val backedOut = errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_CANCELED
                onFail(if (backedOut) "" else errString.toString())
            }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(AUTHENTICATORS).build())
    }
}

package com.kontrolcore.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the setup key (the secret behind the one-time codes) in the Android Keystore. The key that encrypts it is made inside the Keystore, never leaves it,
 * and works only for [AUTH_SECONDS] after the person unlocks with a fingerprint, face or screen lock (setUserAuthenticationParameters), so a phone that is
 * found unlocked-in-a-drawer or copied cannot send a command. The encrypted text sits in the app's private preferences.
 */
object Vault {
    const val AUTH_SECONDS = 60
    private const val ALIAS = "kontrolcore-setup-key"
    private const val PREFS = "kontrolcore-vault"

    class NeedsUnlock : Exception("Unlock to use the setup key")
    class Lost : Exception("The setup key can no longer be read; pair again")

    private fun store(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey? = (store().getKey(ALIAS, null) as? SecretKey)

    private fun makeKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(AUTH_SECONDS, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
            .setUnlockedDeviceRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }

    fun has(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains("blob") && runCatching { key() != null }.getOrDefault(false)

    /** Stores the secret, replacing any earlier one. The person must have unlocked within [AUTH_SECONDS]; otherwise [NeedsUnlock]. Throws if the phone has no screen lock. */
    fun save(context: Context, secret32: String) {
        remove(context)
        val k = makeKey()
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, k) }
            val enc = c.doFinal(secret32.toByteArray(Charsets.UTF_8))
            val blob = Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("blob", blob).apply()
        } catch (e: UserNotAuthenticatedException) {
            remove(context)
            throw NeedsUnlock()
        }
    }

    /** The secret. Throws [NeedsUnlock] when the unlock has run out, [Lost] when it is gone (a changed screen lock can invalidate the key). */
    fun secret(context: Context): String {
        val blob = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("blob", null) ?: throw Lost()
        val k = try { key() } catch (e: Exception) { null } ?: throw Lost()
        val (ivText, encText) = blob.split(":").let { if (it.size != 2) throw Lost() else it[0] to it[1] }
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP))) }
            return String(c.doFinal(Base64.decode(encText, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: UserNotAuthenticatedException) {
            throw NeedsUnlock()
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw Lost()
        } catch (e: java.security.GeneralSecurityException) {
            // Android reports an expired unlock from inside the cipher on some versions.
            if (e.cause is UserNotAuthenticatedException || e.message?.contains("authenticat", ignoreCase = true) == true) throw NeedsUnlock()
            throw Lost()
        }
    }

    fun remove(context: Context) {
        runCatching { store().deleteEntry(ALIAS) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

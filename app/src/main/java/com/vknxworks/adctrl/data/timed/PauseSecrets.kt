package com.vknxworks.adctrl.data.timed

import android.content.Context
import android.util.Base64
import com.vknxworks.adctrl.data.crypto.KeystoreAes
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * A copy of a server's password used ONLY by the job that ends client pauses ([TimedWorker], "resume").
 *
 * With the app lock on and background checks off, the normal passwords are sealed with the PIN, so a job
 * that runs while AdCtrl is locked can't sign in, and a client pause would stay on (that device unfiltered)
 * until someone opened AdCtrl. So while a client pause is active, this keeps the server's password encrypted
 * with its own Keystore key (no PIN, like the passwords without the app lock), in its own settings file.
 *
 * Written when a client pause is set, deleted as soon as no pause is left on that server (the job ended
 * the last one, or it was ended by hand), when the server is removed and with "Forgot PIN?". Nothing else
 * reads it, and it is never sent anywhere but to that server's sign-in by the pause-end job.
 */
object PauseSecrets {
    private const val PREFS = "pause_end_secrets"
    private const val KEY_ALIAS = "adctrl_pause_end"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun keyName(serverId: String) = "password_$serverId"

    /** Keeps [password] for the pause-end job of [serverId]. Call off the main thread (Keystore). */
    fun save(context: Context, serverId: String, password: String) {
        if (password.isEmpty()) {
            clear(context, serverId)
            return
        }
        val sealed = runCatching {
            val c = Cipher.getInstance(KeystoreAes.TRANSFORMATION)
            c.init(Cipher.ENCRYPT_MODE, KeystoreAes.key(KEY_ALIAS))
            Base64.encodeToString(c.iv + c.doFinal(password.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        }.getOrNull() ?: return
        prefs(context).edit().putString(keyName(serverId), sealed).commit()
    }

    /** The kept password of [serverId], or null when there is none or it can't be read. Off the main thread. */
    fun read(context: Context, serverId: String): String? {
        val stored = prefs(context).getString(keyName(serverId), null) ?: return null
        return runCatching {
            // Never make a new key to decrypt: a key that's gone means the copy is gone.
            val key = KeystoreAes.existing(KEY_ALIAS) ?: return null
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val c = Cipher.getInstance(KeystoreAes.TRANSFORMATION)
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun has(context: Context, serverId: String): Boolean = prefs(context).contains(keyName(serverId))

    fun clear(context: Context, serverId: String) {
        prefs(context).edit().remove(keyName(serverId)).commit()
    }

    /** "Forgot PIN?": every kept copy goes. */
    fun clearAll(context: Context) {
        prefs(context).edit().clear().commit()
    }

    /**
     * After the client pauses of [serverId] changed: with none left ([nextEnd] null) the copy is deleted.
     */
    fun afterPausesChanged(context: Context, serverId: String, nextEnd: Long?) {
        if (nextEnd == null) clear(context, serverId)
    }
}

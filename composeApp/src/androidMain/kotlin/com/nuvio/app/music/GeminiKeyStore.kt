package com.nuvio.app.music

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.sync.ProviderCredentialExternal

/**
 * The Gemini API key for each profile, encrypted with the Android Keystore.
 *
 * The key rides the same per-profile provider-credential sync as the other integration keys, so
 * a listener does not have to type it again on another device. It is never shown again after
 * saving and never written to logs.
 */
internal object GeminiKeyStore {
    private const val SECURE_PREFS = "nuvio_music_gemini_secure"
    private const val PLAIN_PREFS = "nuvio_music_gemini_plain"

    private var prefs: SharedPreferences? = null
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        prefs = MusicSecurePreferences.open(
            context.applicationContext,
            SECURE_PREFS,
            PLAIN_PREFS,
        )
        ProviderCredentialExternal.geminiKeyProvider = { read(it) }
        ProviderCredentialExternal.geminiKeyWriter = { profileId, value ->
            write(profileId, value, notify = false)
        }
        ProviderCredentialExternal.geminiProfileRemover = { profileId ->
            write(profileId, null, notify = false)
        }
        initialized = true
    }

    fun read(profileId: Int): String? =
        prefs?.getString(key(profileId), null)?.trim()?.takeIf { it.isNotBlank() }

    fun write(profileId: Int, key: String?, notify: Boolean = true) {
        val store = prefs ?: return
        val value = key?.trim()?.takeIf { it.isNotBlank() }
        store.edit().apply {
            if (value == null) remove(key(profileId)) else putString(key(profileId), value)
        }.apply()
        if (notify) ProviderCredentialExternal.notifyChanged()
    }

    private fun key(profileId: Int): String = "key_$profileId"
}

package com.nuvio.app.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Credentials that live in an Android-only secure store but still ride the shared per-profile
 * provider-credential sync. The Music section registers these hooks when it starts; until then
 * the Gemini row is simply omitted from snapshots.
 */
object ProviderCredentialExternal {
    @Volatile
    var geminiKeyProvider: ((Int) -> String?)? = null
    @Volatile
    var geminiKeyWriter: ((Int, String) -> Unit)? = null
    @Volatile
    var geminiProfileRemover: ((Int) -> Unit)? = null

    private val _revision = MutableStateFlow(0L)

    /** Bumped whenever a Gemini key is edited locally, so the sync pushes a new snapshot. */
    val revision: StateFlow<Long> = _revision

    fun notifyChanged() {
        _revision.value += 1
    }

    fun geminiKey(profileId: Int): String =
        geminiKeyProvider?.invoke(profileId).orEmpty()

    fun writeGeminiKey(profileId: Int, value: String) {
        geminiKeyWriter?.invoke(profileId, value)
    }

    fun removeProfile(profileId: Int) {
        geminiProfileRemover?.invoke(profileId)
    }
}

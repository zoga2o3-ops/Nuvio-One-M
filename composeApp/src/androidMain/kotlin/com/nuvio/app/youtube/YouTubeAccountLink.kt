package com.nuvio.app.youtube

import android.content.Context
import com.liskovsoft.mediaserviceinterfaces.oauth.Account
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The YouTube account this Nuvio One profile is linked to. */
@Serializable
data class LinkedYouTubeAccount(
    val id: String,
    val name: String,
    val email: String? = null,
    val avatar: String? = null,
)

/**
 * Remembers which YouTube account is linked to the active Nuvio One profile. The record is part of
 * the profile's synced settings, so the link follows the Nuvio One profile to another device; the
 * YouTube sign-in itself stays on the device that completed it.
 */
internal object YouTubeAccountLink {
    private const val PREFS = "youtube_account_link"
    private const val KEY = "linked_youtube_account"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(context: Context): LinkedYouTubeAccount? =
        readRaw(context)?.let { raw ->
            runCatching { json.decodeFromString<LinkedYouTubeAccount>(raw) }.getOrNull()
        }

    fun save(context: Context, account: Account): LinkedYouTubeAccount {
        val linked = LinkedYouTubeAccount(
            id = account.id.toString(),
            name = account.name?.takeIf(String::isNotBlank)
                ?: account.email?.takeIf(String::isNotBlank)
                ?: "YouTube account",
            email = account.email?.takeIf(String::isNotBlank),
            avatar = account.avatarImageUrl?.takeIf(String::isNotBlank),
        )
        writeRaw(context, json.encodeToString(linked))
        return linked
    }

    fun clear(context: Context) {
        preferences(context).edit().remove(ProfileScopedKey.of(KEY)).apply()
    }

    /** Drops the links for every profile, for when the Nuvio One account itself signs out. */
    fun clearAll(context: Context) {
        preferences(context).edit().clear().apply()
    }

    /** The stored record as JSON, for syncing with the other devices on this Nuvio One account. */
    fun exportRaw(context: Context): String? = readRaw(context)

    /** Takes a linked account from another device, when it is a valid record. */
    fun applyRaw(context: Context, raw: String) {
        runCatching { json.decodeFromString<LinkedYouTubeAccount>(raw) }
            .onSuccess { writeRaw(context, raw) }
    }

    private fun readRaw(context: Context): String? =
        preferences(context).getString(ProfileScopedKey.of(KEY), null)

    private fun writeRaw(context: Context, raw: String) {
        preferences(context).edit().putString(ProfileScopedKey.of(KEY), raw).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

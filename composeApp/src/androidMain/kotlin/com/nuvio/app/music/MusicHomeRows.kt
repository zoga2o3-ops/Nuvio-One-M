package com.nuvio.app.music

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The personal rows the mobile Music Home can show above the service feed. */
enum class MusicHomeRow(val label: String, val id: String) {
    RECENTS("Recently played", "recents"),
    PLAYLISTS("Your playlists", "playlists"),
    GEMINI_TRACKS("Gemini picks", "gemini_tracks"),
    GEMINI_ALBUMS("Albums for you", "gemini_albums");

    companion object {
        fun fromId(id: String): MusicHomeRow? = entries.firstOrNull { it.id == id }
    }
}

data class MusicHomeRowConfig(
    val order: List<MusicHomeRow> = MusicHomeRow.entries.toList(),
    val hidden: Set<MusicHomeRow> = emptySet(),
    /** Keep the listener's own playlists at the top, whatever the stored order says. */
    val pinLibrary: Boolean = false,
) {
    fun visibleRows(): List<MusicHomeRow> {
        val visible = order.filterNot { it in hidden }
        if (!pinLibrary) return visible
        val library = MusicHomeRow.PLAYLISTS
        if (library !in visible) return visible
        return listOf(library) + visible.filterNot { it == library }
    }
}

/**
 * Row order, visibility and the playlist pin, kept per profile. The rows themselves already come
 * from the active profile's history, playlists and Gemini cache; this only decides which ones are
 * shown and where.
 */
internal object MusicHomeRows {
    private const val PREFS_NAME = "nuvio_music_home_rows"
    private const val ORDER = "order"
    private const val HIDDEN = "hidden"
    private const val PIN_LIBRARY = "pin_library"

    private var prefs: SharedPreferences? = null
    private var profileId: Int = -1

    private val _config = MutableStateFlow(MusicHomeRowConfig())
    val config: StateFlow<MusicHomeRowConfig> = _config.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        useProfile(ProfileRepository.activeProfileId)
    }

    @Synchronized
    fun useProfile(id: Int) {
        if (id == profileId) return
        profileId = id
        val store = prefs ?: return
        val key = { name: String -> "$id.$name" }
        val savedOrder = store.getString(key(ORDER), "").orEmpty()
            .split(',')
            .mapNotNull(MusicHomeRow::fromId)
        val order = (savedOrder + MusicHomeRow.entries)
            .distinct()
            .toList()
        val hidden = store.getStringSet(key(HIDDEN), emptySet()).orEmpty()
            .mapNotNull(MusicHomeRow::fromId)
            .toSet()
        _config.value = MusicHomeRowConfig(
            order = order,
            hidden = hidden,
            pinLibrary = store.getBoolean(key(PIN_LIBRARY), false),
        )
    }

    fun move(row: MusicHomeRow, delta: Int): MusicHomeRowConfig {
        val current = _config.value
        val index = current.order.indexOf(row)
        if (index < 0) return current
        val target = (index + delta).coerceIn(0, current.order.lastIndex)
        if (target == index) return current
        val order = current.order.toMutableList()
        order.removeAt(index)
        order.add(target, row)
        val next = current.copy(order = order)
        _config.value = next
        write { id -> putString("$id.$ORDER", order.joinToString(",") { it.id }) }
        return next
    }

    fun toggleHidden(row: MusicHomeRow): MusicHomeRowConfig {
        val current = _config.value
        val hidden = if (row in current.hidden) current.hidden - row else current.hidden + row
        val next = current.copy(hidden = hidden)
        _config.value = next
        write { id -> putStringSet("$id.$HIDDEN", hidden.mapTo(HashSet()) { it.id }) }
        return next
    }

    fun setPinLibrary(enabled: Boolean): MusicHomeRowConfig {
        val next = _config.value.copy(pinLibrary = enabled)
        _config.value = next
        write { id -> putBoolean("$id.$PIN_LIBRARY", enabled) }
        return next
    }

    fun reset(): MusicHomeRowConfig {
        val next = MusicHomeRowConfig()
        _config.value = next
        write { id ->
            remove("$id.$ORDER")
            remove("$id.$HIDDEN")
            remove("$id.$PIN_LIBRARY")
        }
        return next
    }

    private inline fun write(block: SharedPreferences.Editor.(Int) -> Unit) {
        val store = prefs ?: return
        if (profileId <= 0) return
        val editor = store.edit()
        editor.block(profileId)
        editor.apply()
    }
}

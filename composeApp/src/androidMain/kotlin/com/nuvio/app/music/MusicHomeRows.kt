package com.nuvio.app.music

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.music.engine.data.model.HomeShelf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nuvio.composeapp.generated.resources.*
import org.json.JSONArray
import org.jetbrains.compose.resources.StringResource

/** The personal rows the mobile Music Home can show above the service feed. */
enum class MusicHomeRow(val labelRes: StringResource, val id: String) {
    RECENTS(Res.string.music_home_row_recents, "recents"),
    PLAYLISTS(Res.string.music_home_your_playlists, "playlists"),
    GEMINI_TRACKS(Res.string.music_home_gemini_picks, "gemini_tracks"),
    GEMINI_ALBUMS(Res.string.music_home_albums_for_you, "gemini_albums"),
    QUICK_PICKS(Res.string.music_home_quick_picks, "quick_picks"),
    DAILY_MIXES(Res.string.music_home_daily_mixes, "daily_mixes"),
    MUSIC_VIDEOS(Res.string.music_ui_music_video, "music_videos"),
    ON_REPEAT(Res.string.music_home_on_repeat, "on_repeat"),
    BECAUSE(Res.string.music_home_because_you_listened, "because"),
    FORGOTTEN(Res.string.music_home_forgotten_favorites, "forgotten");

    companion object {
        fun fromId(id: String): MusicHomeRow? = entries.firstOrNull { it.id == id }
    }
}

/** A service-provided Home shelf, shown by its localized title in Home-row settings. */
data class MusicServiceHomeRow(val id: String, val title: String)

/** Stable per-title key shared by feed rendering and row preferences. */
internal fun serviceHomeRowId(shelf: HomeShelf): String =
    "service:${shelf.title.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')}"

data class MusicHomeRowConfig(
    val order: List<MusicHomeRow> = MusicHomeRow.entries.toList(),
    val hidden: Set<MusicHomeRow> = emptySet(),
    val serviceShelves: List<MusicServiceHomeRow> = emptyList(),
    val serviceShelfOrder: List<String> = emptyList(),
    val hiddenServiceShelves: Set<String> = emptySet(),
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

    fun orderedServiceShelves(): List<MusicServiceHomeRow> {
        val byId = serviceShelves.associateBy { it.id }
        return serviceShelfOrder.mapNotNull(byId::get)
    }

    fun visibleServiceShelves(): List<MusicServiceHomeRow> =
        orderedServiceShelves().filterNot { it.id in hiddenServiceShelves }
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
    private const val SERVICE_ORDER = "service_order"
    private const val SERVICE_HIDDEN = "service_hidden"

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
        val serviceShelfOrder = readStringList(store.getString(key(SERVICE_ORDER), null))
        val hiddenServiceShelves = readStringList(store.getString(key(SERVICE_HIDDEN), null)).toSet()
        _config.value = MusicHomeRowConfig(
            order = order,
            hidden = hidden,
            serviceShelfOrder = serviceShelfOrder,
            hiddenServiceShelves = hiddenServiceShelves,
            pinLibrary = store.getBoolean(key(PIN_LIBRARY), false),
        )
    }

    /** Refresh available service shelves without disturbing the listener's saved order/visibility. */
    @Synchronized
    fun syncServiceShelves(shelves: List<HomeShelf>) {
        val rows = shelves.asSequence()
            .filter { it.items.isNotEmpty() }
            .map { MusicServiceHomeRow(serviceHomeRowId(it), it.title) }
            .distinctBy { it.id }
            .toList()
        val current = _config.value
        val ids = rows.map { it.id }
        val order = (current.serviceShelfOrder.filter { it in ids } + ids).distinct()
        _config.value = current.copy(serviceShelves = rows, serviceShelfOrder = order)
        if (order != current.serviceShelfOrder) {
            write { id -> putString("$id.$SERVICE_ORDER", JSONArray(order).toString()) }
        }
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

    fun moveServiceShelf(rowId: String, delta: Int): MusicHomeRowConfig {
        val current = _config.value
        val index = current.serviceShelfOrder.indexOf(rowId)
        if (index < 0) return current
        val target = (index + delta).coerceIn(0, current.serviceShelfOrder.lastIndex)
        if (target == index) return current
        val order = current.serviceShelfOrder.toMutableList()
        order.removeAt(index)
        order.add(target, rowId)
        val next = current.copy(serviceShelfOrder = order)
        _config.value = next
        write { id -> putString("$id.$SERVICE_ORDER", JSONArray(order).toString()) }
        return next
    }

    fun toggleHiddenServiceShelf(rowId: String): MusicHomeRowConfig {
        val current = _config.value
        val hidden = if (rowId in current.hiddenServiceShelves) {
            current.hiddenServiceShelves - rowId
        } else {
            current.hiddenServiceShelves + rowId
        }
        val next = current.copy(hiddenServiceShelves = hidden)
        _config.value = next
        write { id -> putString("$id.$SERVICE_HIDDEN", JSONArray(hidden.sorted()).toString()) }
        return next
    }

    fun setPinLibrary(enabled: Boolean): MusicHomeRowConfig {
        val next = _config.value.copy(pinLibrary = enabled)
        _config.value = next
        write { id -> putBoolean("$id.$PIN_LIBRARY", enabled) }
        return next
    }

    fun reset(): MusicHomeRowConfig {
        val current = _config.value
        val next = MusicHomeRowConfig(
            serviceShelves = current.serviceShelves,
            serviceShelfOrder = current.serviceShelves.map { it.id },
        )
        _config.value = next
        write { id ->
            remove("$id.$ORDER")
            remove("$id.$HIDDEN")
            remove("$id.$PIN_LIBRARY")
            remove("$id.$SERVICE_ORDER")
            remove("$id.$SERVICE_HIDDEN")
        }
        return next
    }

    private fun readStringList(value: String?): List<String> = runCatching {
        val array = JSONArray(value ?: "[]")
        List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private inline fun write(block: SharedPreferences.Editor.(Int) -> Unit) {
        val store = prefs ?: return
        if (profileId <= 0) return
        val editor = store.edit()
        editor.block(profileId)
        editor.apply()
    }
}

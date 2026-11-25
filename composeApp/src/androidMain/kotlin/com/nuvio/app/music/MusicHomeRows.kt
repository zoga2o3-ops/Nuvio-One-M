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
data class MusicServiceHomeRow(
    val id: String,
    val title: String,
    val containsUserPlaylists: Boolean = false,
)

sealed interface MusicHomeSettingsRow {
    val id: String

    data class Personal(val row: MusicHomeRow) : MusicHomeSettingsRow {
        override val id: String get() = row.id
    }

    data class Service(val shelf: MusicServiceHomeRow) : MusicHomeSettingsRow {
        override val id: String get() = shelf.id
    }
}

/** Stable per-title key shared by feed rendering and row preferences. */
internal fun serviceHomeRowId(shelf: HomeShelf): String =
    "service:${shelf.title.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')}"

internal fun moveMusicHomeRowId(
    fullOrder: List<String>,
    displayedIds: List<String>,
    rowId: String,
    delta: Int,
): List<String> {
    val index = displayedIds.indexOf(rowId)
    if (index < 0 || displayedIds.isEmpty()) return fullOrder
    val target = (index + delta).coerceIn(0, displayedIds.lastIndex)
    if (target == index) return fullOrder

    val reorderedIds = displayedIds.toMutableList().apply {
        removeAt(index)
        add(target, rowId)
    }
    val knownIds = displayedIds.toSet()
    val nextVisibleIds = reorderedIds.iterator()
    val result = fullOrder.map { id -> if (id in knownIds) nextVisibleIds.next() else id }.toMutableList()
    while (nextVisibleIds.hasNext()) result += nextVisibleIds.next()
    return result
}

data class MusicHomeRowConfig(
    val order: List<MusicHomeRow> = MusicHomeRow.entries.toList(),
    val hidden: Set<MusicHomeRow> = emptySet(),
    val serviceShelves: List<MusicServiceHomeRow> = emptyList(),
    val serviceShelfOrder: List<String> = emptyList(),
    val hiddenServiceShelves: Set<String> = emptySet(),
    /** Keep the listener's own playlists at the top, whatever the stored order says. */
    val pinLibrary: Boolean = false,
    /** One user-controlled order shared by personal rows and service shelves. */
    val rowOrder: List<String> = emptyList(),
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

    fun orderedSettingsRows(): List<MusicHomeSettingsRow> {
        val rows = buildList {
            MusicHomeRow.entries.forEach { add(MusicHomeSettingsRow.Personal(it)) }
            serviceShelves.forEach { add(MusicHomeSettingsRow.Service(it)) }
        }.associateBy { it.id }
        return normalizedRowOrder().mapNotNull(rows::get)
    }

    fun visibleSettingsRows(): List<MusicHomeSettingsRow> = orderedSettingsRows().filter { row ->
        when (row) {
            is MusicHomeSettingsRow.Personal -> row.row !in hidden
            is MusicHomeSettingsRow.Service -> row.shelf.id !in hiddenServiceShelves
        }
    }

    internal fun normalizedRowOrder(): List<String> =
        (rowOrder + order.map { it.id } + serviceShelfOrder + serviceShelves.map { it.id }).distinct()

    fun visibleServiceShelves(): List<MusicServiceHomeRow> =
        orderedServiceShelves().filterNot { it.id in hiddenServiceShelves }

    fun pinnedServiceShelves(): List<MusicServiceHomeRow> =
        if (pinLibrary) visibleServiceShelves().filter(MusicServiceHomeRow::containsUserPlaylists)
        else emptyList()

    fun unpinnedServiceShelves(): List<MusicServiceHomeRow> {
        val pinnedIds = pinnedServiceShelves().mapTo(HashSet(), MusicServiceHomeRow::id)
        return visibleServiceShelves().filterNot { it.id in pinnedIds }
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
    private const val SERVICE_ORDER = "service_order"
    private const val SERVICE_HIDDEN = "service_hidden"
    private const val ROW_ORDER = "row_order"

    private var prefs: SharedPreferences? = null
    private var profileId: Int = -1
    private var loadedProfileId: Int? = null

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
        if (id == loadedProfileId) {
            profileId = id
            return
        }
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
        val serviceShelves = _config.value.serviceShelves
        val availableServiceIds = serviceShelves.map(MusicServiceHomeRow::id)
        val availableServiceIdSet = availableServiceIds.toSet()
        val savedServiceOrder = readStringList(store.getString(key(SERVICE_ORDER), null))
        val serviceShelfOrder = if (availableServiceIds.isEmpty()) {
            savedServiceOrder
        } else {
            (savedServiceOrder.filter { it in availableServiceIdSet } + availableServiceIds).distinct()
        }
        val savedRowOrder = readStringList(store.getString(key(ROW_ORDER), null))
        val rowOrder = if (savedRowOrder.isNotEmpty()) {
            (savedRowOrder + order.map { it.id } + serviceShelfOrder + availableServiceIds).distinct()
        } else {
            val services = serviceShelfOrder.ifEmpty { availableServiceIds }
            (services.take(1) + order.map { it.id } + services.drop(1)).distinct()
        }
        val hiddenServiceShelves = readStringList(store.getString(key(SERVICE_HIDDEN), null)).toSet()
        _config.value = MusicHomeRowConfig(
            order = order,
            hidden = hidden,
            serviceShelves = serviceShelves,
            serviceShelfOrder = serviceShelfOrder,
            hiddenServiceShelves = hiddenServiceShelves,
            pinLibrary = store.getBoolean(key(PIN_LIBRARY), false),
            rowOrder = rowOrder,
        )
        loadedProfileId = id
    }

    /** Refresh available service shelves without disturbing the listener's saved order/visibility. */
    @Synchronized
    fun syncServiceShelves(shelves: List<HomeShelf>) {
        val rows = shelves.asSequence()
            .filter { it.items.isNotEmpty() }
            .map { shelf ->
                MusicServiceHomeRow(
                    id = serviceHomeRowId(shelf),
                    title = shelf.title,
                    containsUserPlaylists = shelf.items.any {
                        it.browseId?.startsWith("account:playlist:") == true
                    },
                )
            }
            .distinctBy { it.id }
            .toList()
        val current = _config.value
        val ids = rows.map { it.id }
        val rowOrder = (current.normalizedRowOrder() + ids).distinct()
        val serviceOrder = rowOrder.filter { it.startsWith("service:") }
        _config.value = current.copy(
            serviceShelves = rows,
            serviceShelfOrder = serviceOrder,
            rowOrder = rowOrder,
        )
        if (serviceOrder != current.serviceShelfOrder || rowOrder != current.rowOrder) {
            write { id ->
                putString("$id.$SERVICE_ORDER", JSONArray(serviceOrder).toString())
                putString("$id.$ROW_ORDER", JSONArray(rowOrder).toString())
            }
        }
    }

    fun moveSettingsRow(rowId: String, delta: Int): MusicHomeRowConfig {
        val current = _config.value
        val displayedIds = current.orderedSettingsRows().map { it.id }
        val fullOrder = current.normalizedRowOrder()
        val rowOrder = moveMusicHomeRowId(fullOrder, displayedIds, rowId, delta)
        if (rowOrder == fullOrder) return current
        val personalOrder = rowOrder.mapNotNull(MusicHomeRow::fromId).distinct()
        val serviceOrder = rowOrder.filter { it.startsWith("service:") }
        val next = current.copy(order = personalOrder, serviceShelfOrder = serviceOrder, rowOrder = rowOrder)
        _config.value = next
        write { id ->
            putString("$id.$ORDER", personalOrder.joinToString(",") { it.id })
            putString("$id.$SERVICE_ORDER", JSONArray(serviceOrder).toString())
            putString("$id.$ROW_ORDER", JSONArray(rowOrder).toString())
        }
        return next
    }

    fun move(row: MusicHomeRow, delta: Int): MusicHomeRowConfig {
        return moveSettingsRow(row.id, delta)
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
        return moveSettingsRow(rowId, delta)
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
        val serviceOrder = current.serviceShelves.map { it.id }
        val next = MusicHomeRowConfig(
            serviceShelves = current.serviceShelves,
            serviceShelfOrder = serviceOrder,
            rowOrder = serviceOrder.take(1) + MusicHomeRow.entries.map { it.id } + serviceOrder.drop(1),
        )
        _config.value = next
        write { id ->
            remove("$id.$ORDER")
            remove("$id.$HIDDEN")
            remove("$id.$PIN_LIBRARY")
            remove("$id.$SERVICE_ORDER")
            remove("$id.$SERVICE_HIDDEN")
            putString("$id.$ROW_ORDER", JSONArray(next.rowOrder).toString())
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

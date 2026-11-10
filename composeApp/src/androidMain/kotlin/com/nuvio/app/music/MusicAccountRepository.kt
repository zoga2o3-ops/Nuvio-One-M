package com.nuvio.app.music

import android.content.Context
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import com.liskovsoft.youtubeapi.service.data.YouTubeMediaItem
import com.nuvio.app.music.LegacyAccountScope
import com.nuvio.app.music.LegacyAccountScopes
import com.nuvio.app.music.engine.data.model.HomeShelf
import com.nuvio.app.music.engine.data.model.ShelfItem
import com.nuvio.app.music.engine.data.model.ShelfLayout
import com.nuvio.app.music.engine.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** A playlist that lives on the signed-in YouTube Music account. */
data class MusicAccountPlaylist(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val count: Int? = null
)

/**
 * The signed-in half of the Music section.
 *
 * The catalogue browsing still comes from BitChord's InnerTube client. This repository only
 * bridges the account data the app's YouTube engine already fetches with the signed-in OAuth
 * token: the personalised Music home, Liked Music and the account's playlists. Sign-in itself
 * is the same TV device-code flow as the YouTube section.
 */
object MusicAccountRepository {
    private const val TAG = "MusicAccount"
    private const val CACHE_MS = 60_000L
    private const val MAX_PAGES = 300

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val groupItems = ConcurrentHashMap<String, MediaItem>()

    @Volatile
    private var cachedGroups: List<MediaGroup> = emptyList()

    @Volatile
    private var cachedAt = 0L

    data class Library(
        val liked: List<Song>,
        val playlists: List<MusicAccountPlaylist>
    )

    @Volatile
    private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        GlobalPreferences.instance(context.applicationContext)
        YouTubeServiceManager.instance()
    }

    fun isSignedIn(): Boolean =
        runCatching { service().signInService.isSigned }
            .getOrDefault(false)

    /** The account's personalised Music home, without the Liked Music row (Library shows it). */
    fun home(): Result<List<HomeShelf>> = runCatching {
        loadGroups()
            .filterNot { it.isLikedGroup() }
            .mapNotNull { it.toHomeShelf() }
            .filter { it.items.isNotEmpty() }
    }

    /** The account's Liked Music and playlists. */
    fun library(): Result<Library> = runCatching {
        val groups = loadGroups()
        val likedGroup = groups.firstOrNull { it.isLikedGroup() }
        // The first Music row only carries the first page of liked songs. Read the rest too,
        // otherwise likes added on another device never appear after the first connection.
        val liked = loadAllGroupItems(likedGroup)
            .mapNotNull { it.toSong() }
        // A playlist-list failure shouldn't hide the liked songs that did load.
        val playlists = runCatching { loadPlaylists() }.getOrDefault(emptyList())
        Library(liked = liked, playlists = playlists)
    }

    fun playlistSongs(playlistId: String): Result<List<Song>> = runCatching {
        val group = service().contentService.getPlaylist(playlistId)
            ?: error("This playlist couldn't be opened.")
        loadAllGroupItems(group).mapNotNull { it.toSong() }
    }

    /** Opens a shelf card that was stored while the account home was loaded. */
    fun groupSongs(key: String): Result<List<Song>> = runCatching {
        val source = groupItems[key]
            ?: error("This shelf has expired. Reload Home and try again.")
        val group = service().contentService.getGroup(source)
            ?: error("This shelf couldn't be opened.")
        loadAllGroupItems(group).mapNotNull { it.toSong() }
    }

    /** Every item in [first], following the service's continuation pages. */
    private fun loadAllGroupItems(first: MediaGroup?): List<MediaItem> {
        if (first == null) return emptyList()
        val items = mutableListOf<MediaItem>()
        var group: MediaGroup? = first
        var pages = 0
        while (true) {
            val current = group ?: break
            items += current.mediaItems.orEmpty()
            if (current.nextPageKey.isNullOrBlank() || pages++ >= MAX_PAGES) break
            group = runCatching { service().contentService.continueGroup(current) }.getOrNull()
        }
        return items
    }

    /** Best-effort sync of the heart to the account's Liked Music. The local like is immediate. */
    fun syncLike(song: Song, liked: Boolean) {
        if (song.videoId.isBlank() || song.localUri != null || !isSignedIn()) return
        scope.launch {
            runCatching {
                val item = YouTubeMediaItem.fromIds(song.videoId, song.artistId)
                val call = if (liked) {
                    service().mediaItemService.setLikeObserve(item)
                } else {
                    service().mediaItemService.removeLikeObserve(item)
                }
                call.ignoreElements().blockingAwait()
            }.onSuccess {
                invalidate()
            }.onFailure {
                Log.w(TAG, "Could not sync like for ${song.videoId}", it)
            }
        }
    }

    fun invalidate() {
        cachedGroups = emptyList()
        cachedAt = 0L
        // The shelf map stays: its keys are still valid for this screen session, and clearing
        // them here would break an account detail page opened after the next like.
        runCatching { service().invalidateCache() }
    }

    /** The YouTube engine acting as the profile Music has chosen (OneTube keeps its own). */
    private fun service(): com.liskovsoft.mediaserviceinterfaces.ServiceManager {
        val context = checkNotNull(appContext) { "MusicAccountRepository.init() has not been called" }
        return LegacyAccountScopes.engine(context, LegacyAccountScope.MUSIC)
    }

    private fun loadGroups(): List<MediaGroup> {
        val now = System.currentTimeMillis()
        if (cachedGroups.isNotEmpty() && now - cachedAt < CACHE_MS) return cachedGroups
        check(isSignedIn()) { "Not signed in to Music" }
        val batches = service().contentService.getMusicObserve()
            .timeout(25, TimeUnit.SECONDS)
            .toList()
            .blockingGet()
        val groups = batches.flatten()
        cachedGroups = groups
        cachedAt = now
        return groups
    }

    private fun loadPlaylists(): List<MusicAccountPlaylist> {
        var group = service().contentService.getPlaylistsObserve()
            .timeout(20, TimeUnit.SECONDS)
            .blockingFirst()
        val items = mutableListOf<MediaItem>()
        var pages = 0
        while (group != null) {
            items += group.mediaItems.orEmpty()
            if (group.nextPageKey.isNullOrBlank() || pages++ >= MAX_PAGES) break
            group = runCatching { service().contentService.continueGroup(group) }.getOrNull()
        }
        return items
            .mapNotNull { item ->
                val id = item.playlistId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (item.title.orEmpty().contains("liked", ignoreCase = true)) {
                    return@mapNotNull null
                }
                MusicAccountPlaylist(
                    id = id,
                    title = item.title.orEmpty().ifBlank { "Playlist" },
                    thumbnailUrl = item.cardImageUrl ?: item.backgroundImageUrl
                )
            }
            .distinctBy { it.id }
    }

    private fun MediaGroup.isLikedGroup(): Boolean =
        title.orEmpty().contains("liked", ignoreCase = true)

    private fun MediaGroup.toHomeShelf(): HomeShelf? {
        val items = mediaItems.orEmpty().mapNotNull { it.toShelfItem() }
        if (items.isEmpty()) return null
        val tracks = items.count { it.videoId != null }
        val videos = items.count { it.isVideo }
        val layout = when {
            tracks * 2 > items.size -> ShelfLayout.LIST
            videos * 2 > items.size -> ShelfLayout.VIDEO
            else -> ShelfLayout.CARDS
        }
        return HomeShelf(
            title = title.orEmpty().ifBlank { "Music" },
            items = items,
            layout = layout
        )
    }

    private fun MediaItem.toShelfItem(): ShelfItem? {
        val label = title?.takeIf { it.isNotBlank() } ?: return null
        val id = videoId?.takeIf { it.isNotBlank() }
        val browseKey = if (id != null) {
            null
        } else {
            val playlist = playlistId?.takeIf { it.isNotBlank() }
            val key = if (playlist != null) {
                "account:playlist:$playlist"
            } else {
                "account:group:${reloadPageKey ?: channelId ?: label}"
            }
            groupItems[key] = this
            key
        }
        return ShelfItem(
            title = label,
            subtitle = author.orEmpty().ifBlank { secondTitle?.toString().orEmpty() },
            thumbnailUrl = cardImageUrl ?: backgroundImageUrl,
            videoId = id,
            browseId = browseKey,
            isVideo = type == MediaItem.TYPE_VIDEO
        )
    }

    private fun MediaItem.toSong(): Song? {
        val id = videoId?.takeIf { it.isNotBlank() } ?: return null
        val label = title?.takeIf { it.isNotBlank() } ?: return null
        val byline = author?.takeIf { it.isNotBlank() }
            ?: secondTitle?.toString()?.substringBefore(" • ")?.trim().orEmpty()
        return Song(
            videoId = id,
            title = label,
            artist = byline,
            thumbnailUrl = cardImageUrl ?: backgroundImageUrl,
            durationText = formatDuration(durationMs),
            artistId = channelId,
            isVideo = type == MediaItem.TYPE_VIDEO
        )
    }

    private fun formatDuration(milliseconds: Long): String? {
        if (milliseconds <= 0L) return null
        val totalSeconds = milliseconds / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}

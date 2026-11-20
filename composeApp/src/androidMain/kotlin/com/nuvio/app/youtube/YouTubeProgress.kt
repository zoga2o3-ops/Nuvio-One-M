package com.nuvio.app.youtube

import android.content.Context
import android.util.Log
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Watch progress for YouTube videos.
 *
 * Nuvio's player calls [record] as a video plays. That keeps a local copy (for the bar on each
 * card, signed in or not) and, when signed in, tells YouTube too, so the video shows up in your
 * YouTube history and carries its watched bar on your other devices.
 */
object YouTubeProgress {
    private const val TAG = "YouTubeProgress"
    private const val PREFS = "youtube_progress"
    private const val META_PREFS = "youtube_progress_meta"
    private const val META_PREFIX = "meta:"
    private const val AT_PREFIX = "at:"
    private const val MAX_CONTINUE_ITEMS = 12
    const val ID_PREFIX = "youtube:"

    /** Each report to YouTube is a few requests; once every this often is plenty. */
    private const val REPORT_EVERY_MS = 20_000L

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "yt-progress").apply { isDaemon = true } }
    private val lastReported = ConcurrentHashMap<String, Long>()
    private val _changes = MutableStateFlow(0L)
    @Volatile private var lastChangeAt = 0L

    /** Bumped when local progress or the saved video details change. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun metaPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)

    private fun changed(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastChangeAt < 5_000L) return
        lastChangeAt = now
        _changes.value = System.currentTimeMillis()
    }

    /** A video that has been started but not finished, with enough details to draw a card. */
    data class YouTubeContinueEntry(
        val videoId: String,
        val title: String,
        val poster: String?,
        val positionMs: Long,
        val durationMs: Long,
        val updatedAtMs: Long,
    )

    /**
     * Remember the video's title and thumbnail, so Home can show it after a restart. The player
     * still supplies the position; this only covers the details.
     */
    fun remember(context: Context, videoId: String?, title: String?, poster: String?) {
        val id = videoId?.takeIf { it.isNotBlank() } ?: return
        val name = title?.trim()?.takeIf { it.isNotBlank() } ?: return
        val meta = JSONObject().put("title", name).put("poster", poster.orEmpty()).toString()
        metaPrefs(context).edit()
            .putString(META_PREFIX + id, meta)
            .putLong(AT_PREFIX + id, System.currentTimeMillis())
            .apply()
        changed(force = true)
    }

    fun isYouTubeId(contentId: String?) = contentId?.startsWith(ID_PREFIX) == true

    /** Called by the player with where it's got to. Safe to call often. */
    fun record(context: Context, contentId: String?, positionMs: Long, durationMs: Long) {
        val videoId = contentId?.removePrefix(ID_PREFIX)?.takeIf { it.isNotBlank() } ?: return
        if (positionMs < 1000) return
        val app = context.applicationContext
        runCatching {
            prefs(app).edit()
                .putString(videoId, "$positionMs,$durationMs").apply()
            metaPrefs(app).edit().putLong(AT_PREFIX + videoId, System.currentTimeMillis()).apply()
        }
        changed()

        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastReported[videoId] ?: 0L
        // Always report the end of a video; otherwise keep to the interval.
        val finished = durationMs > 0 && positionMs >= durationMs - 5_000L
        if (!finished && now - last < REPORT_EVERY_MS) return
        lastReported[videoId] = now

        worker.execute {
            runCatching {
                GlobalPreferences.instance(app)
                val service = YouTubeServiceManager.instance()
                if (!service.signInService.isSigned) return@execute
                service.mediaItemService.updateHistoryPosition(videoId, positionMs / 1000f)
                Log.i(TAG, "reported $videoId at ${positionMs / 1000}s")
            }.onFailure { Log.w(TAG, "couldn't report $videoId", it) }
        }
    }

    /** Where this device got to in a video, and how long the video is, in milliseconds; null if it hasn't played it. */
    fun localState(context: Context, videoId: String?): Pair<Long, Long>? {
        if (videoId == null) return null
        val v = runCatching { prefs(context).getString(videoId, null) }.getOrNull() ?: return null
        val parts = v.split(',')
        val pos = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val dur = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return if (dur > 0) pos to dur else null
    }

    /** How much of the video you've watched, 0 to 100, from this device. */
    fun localPercent(context: Context, videoId: String?): Int {
        if (videoId == null) return 0
        val v = runCatching { prefs(context).getString(videoId, null) }.getOrNull() ?: return 0
        val parts = v.split(',')
        val pos = parts.getOrNull(0)?.toLongOrNull() ?: return 0
        val dur = parts.getOrNull(1)?.toLongOrNull() ?: return 0
        if (dur <= 0) return 0
        return (pos * 100 / dur).toInt().coerceIn(0, 100)
    }

    /** Videos started but not finished, newest first, with the details needed for a Home card. */
    fun localEntries(context: Context): List<YouTubeContinueEntry> {
        val app = context.applicationContext
        val progress = runCatching { prefs(app).all }.getOrDefault(emptyMap())
        val meta = runCatching { metaPrefs(app).all }.getOrDefault(emptyMap())
        val out = ArrayList<YouTubeContinueEntry>()
        progress.forEach { (videoId, value) ->
            val raw = value as? String ?: return@forEach
            val parts = raw.split(',')
            val pos = parts.getOrNull(0)?.toLongOrNull() ?: return@forEach
            val dur = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
            if (dur <= 0 || pos < 1_000L || pos >= dur - 5_000L) return@forEach
            val metaRaw = meta[META_PREFIX + videoId] as? String ?: return@forEach
            val obj = runCatching { JSONObject(metaRaw) }.getOrNull() ?: return@forEach
            val title = obj.optString("title").trim().takeIf { it.isNotBlank() } ?: return@forEach
            val poster = obj.optString("poster").trim().takeIf { it.isNotBlank() }
            val at = (meta[AT_PREFIX + videoId] as? Long)
                ?: (meta[AT_PREFIX + videoId] as? Int)?.toLong()
                ?: 0L
            out += YouTubeContinueEntry(
                videoId = videoId,
                title = title,
                poster = poster,
                positionMs = pos,
                durationMs = dur,
                updatedAtMs = at,
            )
        }
        return out.sortedByDescending { it.updatedAtMs }.take(MAX_CONTINUE_ITEMS)
    }

    /** Forget a video's local progress and details. */
    fun clear(context: Context, videoId: String) {
        prefs(context).edit().remove(videoId).apply()
        metaPrefs(context).edit().remove(META_PREFIX + videoId).remove(AT_PREFIX + videoId).apply()
        changed(force = true)
    }
}

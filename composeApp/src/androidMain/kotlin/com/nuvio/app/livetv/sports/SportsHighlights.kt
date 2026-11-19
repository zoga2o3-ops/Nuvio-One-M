package com.nuvio.app.livetv.sports

import android.content.Context
import android.util.Log
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.sharedutils.prefs.GlobalPreferences
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** A replay, condensed game, recap or highlight for a finished game. */
data class SportsVideo(
    val id: String,
    val title: String,
    val kind: Kind,
    val source: String,
    val seconds: Int?,
    val url: String,
) {
    enum class Kind { CONDENSED, RECAP, HIGHLIGHTS, PLAY }

    val youtubeId: String? get() = YouTubeId.find(url)?.groupValues?.get(1)
    val needsNhlResolve: Boolean get() = url.startsWith(NHL_SCHEME)

    companion object {
        private val YouTubeId = Regex("""(?:youtube\.com/(?:watch\?(?:.*&)?v=|embed/|shorts/)|youtu\.be/)([A-Za-z0-9_-]{11})""")
        internal const val NHL_SCHEME = "nhlvideo:"
    }
}

/**
 * Builds a finished game's official videos and YouTube highlights, then keeps them in a small
 * on-device JSON file so opening the game again doesn't search for them again.
 *
 * ESPN comes from the game summary the page already reads. NHL and MLB use their public APIs.
 * YouTube uses the app's own engine (the same one OneTube and Music use), so no API key or
 * extra data service is needed.
 */
internal object SportsHighlights {
    private const val TAG = "SportsHighlights"
    private const val TTL_MS = 6L * 60 * 60 * 1000
    private const val MAX_CACHE_FILES = 200
    private const val NHL_LEAGUE = "hockey/nhl"
    private const val MLB_LEAGUE = "baseball/mlb"
    private const val NHL_ACCOUNT = "6415718365001"
    private const val NHL_PLAYER = "D3UCGynRWU"
    private const val NHL_POLICY_KEY = "BCpkADawqM3l37Vq8trLJ95vVwxubXYZXYglAopEZXQTHTWX3YdalyF9xmkuknxjBgiMYwt8VZ_OZ1jAjYxz_yzuNh_cjC3uOaMspVTD-hZfNUHtNnBnhVD0Gmsih8TBF8QlQFXiCQM3W_u4ydJ1qK2Rx8ZutCUg3PHb7Q"
    private val POLICY_KEY = Regex("policyKey:\"([^\"]+)\"")
    private val NHL_CLIP_ID = Regex("""(\d{10,})""")
    private val SAME_GAME_MS = 5 * 60 * 60 * 1000L
    private val SOURCE_ORDER = listOf("NHL", "MLB", "ESPN", "YouTube")

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    @Volatile private var nhlPolicyKey = NHL_POLICY_KEY

    suspend fun load(
        context: Context,
        ev: SportsEvent,
        espnSummary: JSONObject?,
    ): List<SportsVideo> = withContext(Dispatchers.IO) {
        val file = cacheFile(context, ev)
        readCache(file)?.takeIf { System.currentTimeMillis() - it.first < TTL_MS }?.let { return@withContext it.second }

        val videos = ArrayList<SportsVideo>()
        if (espnSummary != null) videos += fromEspn(espnSummary)
        when (ev.leagueId) {
            NHL_LEAGUE -> videos += fromNhl(ev)
            MLB_LEAGUE -> videos += fromMlb(ev)
        }
        videos += fromYouTube(context, ev)

        val seen = HashSet<String>()
        val cleaned = videos
            .filter { it.url.isNotBlank() && seen.add(it.url) }
            .sortedWith(compareBy({ it.kind.ordinal }, { sourceRank(it.source) }))
        // A game can list dozens of single plays; a dozen is plenty at the end.
        val result = cleaned.filter { it.kind != SportsVideo.Kind.PLAY }.take(30) +
            cleaned.filter { it.kind == SportsVideo.Kind.PLAY }.take(12)
        if (result.isNotEmpty()) writeCache(file, result)
        result
    }

    /** NHL's Brightcove links are worked out when one is picked; the other links play as they are. */
    suspend fun playableUrl(video: SportsVideo): String? = withContext(Dispatchers.IO) {
        if (!video.needsNhlResolve) return@withContext video.url
        val id = video.url.removePrefix(SportsVideo.NHL_SCHEME)
        runCatching { brightcove(id) }.getOrNull()
    }

    // ------------------------------------------------------------------ ESPN

    private fun fromEspn(root: JSONObject): List<SportsVideo> {
        val array = root.optJSONArray("videos") ?: return emptyList()
        val out = ArrayList<SportsVideo>()
        for (i in 0 until array.length()) {
            val v = array.optJSONObject(i) ?: continue
            val title = v.optString("headline").trim()
            if (title.isBlank()) continue
            val links = v.optJSONObject("links")?.optJSONObject("source")
            val url = links?.optJSONObject("HLS")?.optString("href")?.takeIf { it.startsWith("https://") }
                ?: links?.optJSONObject("HD")?.optString("href")?.takeIf { it.startsWith("https://") }
                ?: links?.optString("href")?.takeIf { it.startsWith("https://") }
                ?: continue
            val coverage = v.optJSONObject("tracking")?.optString("coverageType").orEmpty()
            val kind = when {
                title.contains("condensed", true) -> SportsVideo.Kind.CONDENSED
                title.contains("recap", true) -> SportsVideo.Kind.RECAP
                coverage.contains("Game Highlight", true) || title.contains("Game Highlights", true) ->
                    SportsVideo.Kind.HIGHLIGHTS
                else -> SportsVideo.Kind.PLAY
            }
            out += SportsVideo(
                id = "espn:" + v.optString("id", url),
                title = title,
                kind = kind,
                source = "ESPN",
                seconds = v.optInt("duration").takeIf { it > 0 },
                url = url
            )
        }
        return out
    }

    // ------------------------------------------------------------------ NHL

    private fun fromNhl(ev: SportsEvent): List<SportsVideo> {
        val game = findNhlGame(ev) ?: return emptyList()
        val title = gameTitle(ev)
        val out = ArrayList<SportsVideo>()
        fun add(path: String?, kind: SportsVideo.Kind, name: String) {
            val id = path?.let { NHL_CLIP_ID.find(it)?.groupValues?.get(1) ?: it.takeIf { it.all(Char::isDigit) } } ?: return
            out += SportsVideo("nhl:$id", name, kind, "NHL", null, SportsVideo.NHL_SCHEME + id)
        }
        add(game.optString("condensedGame"), SportsVideo.Kind.CONDENSED, "$title · Condensed game")
        add(game.optString("threeMinRecap"), SportsVideo.Kind.RECAP, "$title · Recap")
        val goals = game.optJSONArray("goals") ?: JSONArray()
        for (i in 0 until goals.length()) {
            val goal = goals.optJSONObject(i) ?: continue
            val clip = goal.optLong("highlightClip").takeIf { it > 0 } ?: continue
            val who = goal.optJSONObject("name")?.optString("default").orEmpty()
            val period = goal.optJSONObject("periodDescriptor")?.let { p ->
                when (p.optString("periodType")) {
                    "OT" -> "OT"
                    "SO" -> "Shootout"
                    else -> "P" + p.optInt("number")
                }
            }.orEmpty()
            val name = listOf("Goal", who.takeIf { it.isNotBlank() }.orEmpty(), "$period ${goal.optString("timeInPeriod")}".trim())
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            out += SportsVideo("nhl:$clip", name, SportsVideo.Kind.PLAY, "NHL", null, SportsVideo.NHL_SCHEME + clip)
        }
        return out
    }

    private fun findNhlGame(ev: SportsEvent): JSONObject? {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/New_York")
        }
        for (shift in listOf(0L, -1L, 1L)) {
            val raw = get("https://api-web.nhle.com/v1/score/" + date.format(Date(ev.startMs + shift * 86_400_000L)))
                ?: continue
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: continue
            val games = root.optJSONArray("games") ?: continue
            for (i in 0 until games.length()) {
                val game = games.optJSONObject(i) ?: continue
                val start = parseIso(game.optString("startTimeUTC"))
                if (start != null && kotlin.math.abs(start - ev.startMs) > SAME_GAME_MS) continue
                val away = game.optJSONObject("awayTeam")
                val home = game.optJSONObject("homeTeam")
                if (sameTeam(ev.away, away) && sameTeam(ev.home, home)) return game
            }
        }
        return null
    }

    private fun sameTeam(side: Side?, team: JSONObject?): Boolean {
        team ?: return false
        val our = side?.team ?: return false
        val abbr = team.optString("abbrev")
        if (abbr.isNotBlank() && abbr.equals(our.abbr, true)) return true
        val name = team.optJSONObject("name")?.optString("default").orEmpty()
        if (name.isBlank()) return false
        val a = normalize(name)
        return listOf(our.name, our.fullName).map(::normalize).any { it.isNotEmpty() && (it == a || it.contains(a) || a.contains(it)) }
    }

    private suspend fun brightcove(id: String): String? {
        suspend fun ask(): String? {
            val request = Request.Builder()
                .url("https://edge.api.brightcove.com/playback/v1/accounts/$NHL_ACCOUNT/videos/$id")
                .header("Accept", "application/json;pk=$nhlPolicyKey")
                .build()
            val response = http.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) return null
                val root = JSONObject(it.body?.string().orEmpty())
                val sources = root.optJSONArray("sources") ?: return null
                val list = (0 until sources.length()).mapNotNull { i -> sources.optJSONObject(i) }
                    .filter { s -> s.optString("src").startsWith("https://") && s.optJSONObject("key_systems") == null }
                return list.firstOrNull { s -> s.optString("type").contains("mpegurl", true) }?.optString("src")
                    ?: list.filter { s -> s.optString("container").equals("MP4", true) }
                        .maxByOrNull { s -> s.optInt("height") }
                        ?.optString("src")
            }
        }
        ask()?.let { return it }
        // The key NHL's own player uses can change; read the current one and try once more.
        val js = get("https://players.brightcove.net/$NHL_ACCOUNT/${NHL_PLAYER}_default/index.min.js") ?: return null
        val key = POLICY_KEY.find(js)?.groupValues?.get(1) ?: return null
        nhlPolicyKey = key
        return ask()
    }

    // ------------------------------------------------------------------ MLB

    private fun fromMlb(ev: SportsEvent): List<SportsVideo> {
        val pk = findMlbGame(ev) ?: return emptyList()
        val raw = get("https://statsapi.mlb.com/api/v1/game/$pk/content") ?: return emptyList()
        val items = runCatching {
            JSONObject(raw).optJSONObject("highlights")
                ?.optJSONObject("highlights")
                ?.optJSONArray("items")
        }.getOrNull() ?: return emptyList()
        val out = ArrayList<SportsVideo>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val title = item.optString("title").ifBlank { item.optString("headline") }.trim()
            if (title.isBlank()) continue
            val keywords = item.optJSONArray("keywordsAll")?.let { k ->
                (0 until k.length()).map { n -> k.optJSONObject(n)?.optString("value").orEmpty().lowercase() }
            }.orEmpty()
            val kind = when {
                keywords.any { "condensed" in it } || title.startsWith("Condensed Game", true) -> SportsVideo.Kind.CONDENSED
                keywords.any { it == "game-recap" || it == "mlbcom_game_recap" } -> SportsVideo.Kind.RECAP
                else -> SportsVideo.Kind.PLAY
            }
            val url = mlbPlayback(item.optJSONArray("playbacks")) ?: continue
            out += SportsVideo(
                id = "mlb:" + item.optString("id", url),
                title = title,
                kind = kind,
                source = "MLB",
                seconds = parseClock(item.optString("duration")),
                url = url
            )
        }
        return out
    }

    private fun findMlbGame(ev: SportsEvent): Long? {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/New_York")
        }
        for (shift in listOf(0L, -1L, 1L)) {
            val raw = get("https://statsapi.mlb.com/api/v1/schedule?sportId=1&date=" + date.format(Date(ev.startMs + shift * 86_400_000L)))
                ?: continue
            val dates = runCatching { JSONObject(raw).optJSONArray("dates") }.getOrNull() ?: continue
            for (d in 0 until dates.length()) {
                val games = dates.optJSONObject(d)?.optJSONArray("games") ?: continue
                for (i in 0 until games.length()) {
                    val game = games.optJSONObject(i) ?: continue
                    val start = parseIso(game.optString("gameDate"))
                    if (start != null && kotlin.math.abs(start - ev.startMs) > SAME_GAME_MS) continue
                    val teams = game.optJSONObject("teams")
                    val away = teams?.optJSONObject("away")?.optJSONObject("team")?.optString("name").orEmpty()
                    val home = teams?.optJSONObject("home")?.optJSONObject("team")?.optString("name").orEmpty()
                    if (teamMatches(ev.away, away) && teamMatches(ev.home, home)) {
                        return game.optLong("gamePk").takeIf { it > 0 }
                    }
                }
            }
        }
        return null
    }

    private fun teamMatches(side: Side?, name: String): Boolean {
        if (name.isBlank()) return false
        val team = side?.team ?: return false
        val n = normalize(name)
        return listOf(team.name, team.fullName).map(::normalize)
            .any { it.isNotEmpty() && (it == n || it.contains(n) || n.contains(it)) }
    }

    private fun mlbPlayback(array: JSONArray?): String? {
        array ?: return null
        val urls = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            .map { it.optString("name") to it.optString("url") }
            .filter { it.second.startsWith("https://") }
        return urls.firstOrNull { it.second.contains(".m3u8") }?.second
            ?: urls.firstOrNull { it.first.equals("mp4Avc", true) }?.second
            ?: urls.firstOrNull { it.second.contains(".mp4") }?.second
    }

    // ------------------------------------------------------------------ YouTube

    private fun fromYouTube(context: Context, ev: SportsEvent): List<SportsVideo> {
        val away = ev.away?.team
        val home = ev.home?.team
        val query = if (away != null && home != null) {
            "${away.name} vs ${home.name} highlights"
        } else {
            "${ev.title} highlights"
        }
        return runCatching {
            GlobalPreferences.instance(context)
            val groups = YouTubeServiceManager.instance().contentService
                .getSearchObserve(query)
                .blockingFirst()
            val items = groups.flatMap { it.mediaItems.orEmpty() }
                .filter { it.type == MediaItem.TYPE_VIDEO && it.videoId != null && !it.isLive && !it.isUpcoming }
                .filter { item ->
                    val duration = item.durationMs
                    (duration <= 0 || duration in 90_000L..1_800_000L) && titleFits(ev, item.title.orEmpty())
                }
                .take(3)
            items.map { item ->
                val id = item.videoId!!
                SportsVideo(
                    id = "yt:$id",
                    title = item.title.orEmpty(),
                    kind = SportsVideo.Kind.HIGHLIGHTS,
                    source = "YouTube",
                    seconds = (item.durationMs / 1000L).toInt().takeIf { it > 0 },
                    url = "https://www.youtube.com/watch?v=$id"
                )
            }
        }.onFailure { Log.w(TAG, "YouTube highlights failed for ${ev.id}", it) }.getOrDefault(emptyList())
    }

    private fun titleFits(ev: SportsEvent, raw: String): Boolean {
        val title = normalize(raw)
        if (title.isEmpty()) return false
        val away = ev.away?.team
        val home = ev.home?.team
        if (away != null && home != null) {
            fun inTitle(t: TeamInfo) = listOf(t.name, t.abbr)
                .map(::normalize)
                .filter { it.length >= 3 }
                .any { it in title }
            return inTitle(away) && inTitle(home)
        }
        val words = ev.title.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in setOf("the", "and", "vs", "final", "race", "fight", "night", "card") }
        if (words.isEmpty()) return false
        return words.count { normalize(it) in title } * 10 >= words.size * 7
    }

    // ------------------------------------------------------------------ cache

    private fun cacheFile(context: Context, ev: SportsEvent): File {
        val dir = File(context.filesDir, "sports/highlights").apply { mkdirs() }
        return File(dir, ev.id.hashCode().toUInt().toString(16) + ".json")
    }

    private fun readCache(file: File): Pair<Long, List<SportsVideo>>? = runCatching {
        if (!file.exists()) return null
        val root = JSONObject(file.readText())
        val saved = root.optLong("savedAt")
        val array = root.optJSONArray("videos") ?: JSONArray()
        val videos = ArrayList<SportsVideo>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val kind = runCatching { SportsVideo.Kind.valueOf(o.optString("kind")) }.getOrDefault(SportsVideo.Kind.HIGHLIGHTS)
            videos += SportsVideo(
                id = o.optString("id"),
                title = o.optString("title"),
                kind = kind,
                source = o.optString("source"),
                seconds = o.optInt("seconds").takeIf { it > 0 },
                url = o.optString("url")
            )
        }
        saved to videos
    }.getOrNull()

    private fun writeCache(file: File, videos: List<SportsVideo>) = runCatching {
        val array = JSONArray()
        videos.forEach { v ->
            array.put(JSONObject().apply {
                put("id", v.id)
                put("title", v.title)
                put("kind", v.kind.name)
                put("source", v.source)
                put("seconds", v.seconds ?: 0)
                put("url", v.url)
            })
        }
        file.writeText(JSONObject().put("savedAt", System.currentTimeMillis()).put("videos", array).toString())
        pruneCache(file.parentFile)
    }

    /** A game's list is a few KB; keep the folder bounded so browsing old games can't grow it forever. */
    private fun pruneCache(dir: File?) {
        val files = dir?.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return
        if (files.size <= MAX_CACHE_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CACHE_FILES)
            .forEach { runCatching { it.delete() } }
    }

    // ------------------------------------------------------------------ helpers

    private fun get(url: String): String? = runCatching {
        val response = http.newCall(Request.Builder().url(url).build()).execute()
        response.use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()

    private fun sourceRank(source: String): Int = SOURCE_ORDER.indexOf(source).let { if (it < 0) 99 else it }

    private fun gameTitle(ev: SportsEvent): String {
        val away = ev.away?.team?.abbr
        val home = ev.home?.team?.abbr
        return if (!away.isNullOrBlank() && !home.isNullOrBlank()) "$away at $home" else ev.title
    }

    private fun parseIso(raw: String): Long? = runCatching {
        java.time.Instant.parse(raw).toEpochMilli()
    }.getOrNull()

    private fun parseClock(raw: String): Int? {
        val parts = raw.split(':').mapNotNull { it.trim().toIntOrNull() }
        if (parts.isEmpty()) return null
        return parts.fold(0) { acc, n -> acc * 60 + n }.takeIf { it > 0 }
    }

    private fun normalize(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }
}

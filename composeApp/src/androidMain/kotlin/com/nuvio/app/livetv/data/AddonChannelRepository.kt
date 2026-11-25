package com.nuvio.app.livetv.data

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.fetchAddonResponseText
import com.nuvio.app.livetv.model.AddonChannelRef
import com.nuvio.app.livetv.model.AddonChannelSource
import com.nuvio.app.livetv.model.LiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import android.util.Log
import org.json.JSONObject

/** Imports selected `tv` catalogs from installed add-ons as Live TV channels. */
internal object AddonChannelRepository {
    data class Loaded(
        val channels: List<LiveChannel>,
        val counts: Map<String, Int>,
        val errors: Map<String, String>,
    )

    private const val PAGE_SIZE = 100
    private const val MAX_PAGES = 5
    private const val CACHE_MS = 10 * 60_000L
    private var cacheKey: String? = null
    private var cacheAt = 0L
    private var cached = Loaded(emptyList(), emptyMap(), emptyMap())

    suspend fun load(sources: List<AddonChannelSource>, force: Boolean = false): Loaded = withContext(Dispatchers.IO) {
        // Add-on manifests are refreshed asynchronously during app startup. Wait until that
        // initial load settles so selected catalogs aren't mistaken for missing add-ons.
        AddonRepository.initialize()
        val enabled = sources.filter { it.enabled && it.id.isNotBlank() }
        if (enabled.isEmpty()) return@withContext Loaded(emptyList(), emptyMap(), emptyMap())
        val selectedManifestUrls = enabled.mapTo(HashSet()) { it.manifestUrl }
        AddonRepository.uiState.first { state ->
            selectedManifestUrls.all { url ->
                state.addons.firstOrNull { it.manifestUrl == url }
                    ?.let { it.manifest != null || !it.isRefreshing }
                    ?: true
            }
        }

        val key = enabled.joinToString("|") { it.id }
        val now = System.currentTimeMillis()
        if (!force && cached.errors.isEmpty() && key == cacheKey && now - cacheAt < CACHE_MS) return@withContext cached

        val installed = AddonRepository.uiState.value.addons
            .filter { it.enabled && it.manifest != null }
            .associateBy { it.manifestUrl }
        val channels = ArrayList<LiveChannel>()
        val counts = HashMap<String, Int>()
        val errors = HashMap<String, String>()
        val keys = HashSet<String>()

        enabled.forEach { source ->
            val addon = installed[source.manifestUrl]
            val manifest = addon?.manifest
            if (addon == null || manifest == null) {
                errors[source.id] = "This add-on is no longer installed or enabled."
                counts[source.id] = 0
                Log.w("LiveTvAddons", "Selected add-on is missing or disabled: ${source.addonName}")
                return@forEach
            }
            val catalog = manifest.catalogs.firstOrNull { it.type.equals("tv", ignoreCase = true) && it.id == source.catalogId }
            if (catalog == null) {
                errors[source.id] = "This Live TV catalog is no longer in the add-on."
                counts[source.id] = 0
                Log.w("LiveTvAddons", "Selected catalog is no longer in ${source.addonName}: ${source.catalogName}")
                return@forEach
            }

            val before = channels.size
            runCatching { readCatalog(addon, catalog, source, channels, keys) }
                .onFailure {
                    errors[source.id] = it.message ?: "The add-on catalog couldn't be loaded."
                    Log.w("LiveTvAddons", "Catalog ${source.addonName}/${source.catalogName} failed: ${it.message}", it)
                }
            counts[source.id] = channels.size - before
            Log.i("LiveTvAddons", "Catalog ${source.addonName}/${source.catalogName}: ${counts[source.id]} channels")
        }

        Loaded(channels, counts, errors).also { loaded ->
            cached = loaded
            cacheKey = key.takeIf { errors.isEmpty() }
            cacheAt = if (errors.isEmpty()) now else 0L
        }
    }

    fun invalidate() {
        cacheKey = null
        cacheAt = 0L
        cached = Loaded(emptyList(), emptyMap(), emptyMap())
    }

    private suspend fun readCatalog(
        addon: ManagedAddon,
        catalog: com.nuvio.app.features.addons.AddonCatalog,
        source: AddonChannelSource,
        out: MutableList<LiveChannel>,
        usedKeys: MutableSet<String>,
    ) {
        val manifestUrl = addon.manifest?.transportUrl ?: return
        val seen = HashSet<String>()
        repeat(MAX_PAGES) { page ->
            val extra = if (page == 0) null else "skip=${page * PAGE_SIZE}"
            val url = buildAddonResourceUrl(manifestUrl, "catalog", "tv", catalog.id, extra)
            val raw = fetchAddonResponseText(url, forceRefresh = page > 0)
            val metas = JSONObject(raw).optJSONArray("metas")
                ?: error("Catalog response did not include channel items.")
            var fresh = 0
            for (i in 0 until metas.length()) {
                val item = metas.optJSONObject(i) ?: continue
                val id = item.optString("id").trim().takeIf(String::isNotBlank) ?: continue
                val name = item.optString("name").trim().takeIf(String::isNotBlank) ?: continue
                if (!seen.add(id)) continue
                val sourceId = addonSourceId(addon.manifestUrl)
                val baseKey = "$sourceId|${catalog.id}|$id"
                var channelKey = baseKey
                var suffix = 2
                while (!usedKeys.add(channelKey)) channelKey = "$baseKey#${suffix++}"
                out += LiveChannel(
                    key = channelKey,
                    sourceId = sourceId,
                    sourceName = addon.displayTitle,
                    name = name,
                    tvgId = id,
                    tvgName = name,
                    logo = item.optString("logo").ifBlank { null } ?: item.optString("poster").ifBlank { null },
                    groupId = "$sourceId::${catalog.id}",
                    group = "${addon.displayTitle} · ${catalog.name}",
                    number = 0,
                    url = "",
                    addon = AddonChannelRef(
                        manifestUrl = manifestUrl,
                        addonName = addon.displayTitle,
                        addonLogo = addon.manifest.logoUrl,
                        channelId = id,
                    ),
                )
                fresh++
            }
            if (fresh == 0 || metas.length() < PAGE_SIZE) return
        }
    }

    private fun addonSourceId(manifestUrl: String): String =
        "addon_${manifestUrl.trimEnd('/').hashCode()}_${manifestUrl.length}"
}

package com.nuvio.app.music

import android.content.Context
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.music.engine.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.*

data class MusicGeminiUiState(
    val profileId: Int = -1,
    val configured: Boolean = false,
    val enabled: Boolean = true,
    val model: String = GeminiSettings.DEFAULT_MODEL,
    val availableModels: List<GeminiAvailableModel> = emptyList(),
    val loadingModels: Boolean = false,
    val modelLoadError: String? = null,
    val shareTopArtists: Boolean = true,
    val shareLiked: Boolean = true,
    val shareRecents: Boolean = true,
    val sharePlaylists: Boolean = true,
    val generating: Boolean = false,
    val error: MusicText? = null,
    val notice: MusicText? = null,
    val tracks: List<Song> = emptyList(),
    val albums: List<GeminiAlbum> = emptyList(),
    val lastGeneratedAtMs: Long = 0L,
)

/**
 * Builds and caches optional Gemini suggestions for the active profile. The API key stays in
 * [GeminiKeyStore]; this controller only needs the current profile, the taste signals the
 * listener allowed, and the verified rows.
 */
internal object MusicGeminiController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(MusicGeminiUiState())
    val state: StateFlow<MusicGeminiUiState> = _state.asStateFlow()

    private var initialized = false
    private var generateJob: Job? = null
    private var modelsJob: Job? = null

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        MusicLibrary.init(app)
        MusicGeminiSettings.init(app)
        initialized = true
        scope.launch {
            ProfileRepository.state
                .map { ProfileRepository.activeProfileId }
                .distinctUntilChanged()
                .collect { load(app, it) }
        }
    }

    private fun load(context: Context, profileId: Int) {
        if (profileId <= 0) return
        if (_state.value.profileId != profileId) {
            modelsJob?.cancel()
            modelsJob = null
        }
        val settings = MusicGeminiSettings.load(profileId)
        val cached = MusicGeminiCache.load(context, profileId)
        _state.value = MusicGeminiUiState(
            profileId = profileId,
            configured = GeminiKeyStore.read(profileId) != null,
            enabled = settings.enabled,
            model = settings.model,
            availableModels = if (_state.value.profileId == profileId) _state.value.availableModels else emptyList(),
            shareTopArtists = settings.shareTopArtists,
            shareLiked = settings.shareLiked,
            shareRecents = settings.shareRecents,
            sharePlaylists = settings.sharePlaylists,
            tracks = cached?.tracks.orEmpty(),
            albums = cached?.albums.orEmpty(),
            lastGeneratedAtMs = settings.lastGeneratedAtMs,
        )
    }

    fun saveKey(context: Context, key: String) {
        val profileId = currentProfileId() ?: return
        if (key.isBlank()) {
            _state.value = _state.value.copy(
                error = MusicText.Localized(Res.string.music_gemini_error_paste_key)
            )
            return
        }
        GeminiKeyStore.write(profileId, key)
        _state.value = _state.value.copy(
            configured = true,
            error = null,
            notice = MusicText.Localized(Res.string.music_gemini_notice_key_saved),
            availableModels = emptyList(),
            modelLoadError = null,
            loadingModels = false,
        )
        modelsJob?.cancel()
        refreshModels()
    }

    fun removeKey(context: Context) {
        val profileId = currentProfileId() ?: return
        modelsJob?.cancel()
        modelsJob = null
        GeminiKeyStore.write(profileId, null)
        MusicGeminiCache.clear(context, profileId)
        _state.value = _state.value.copy(
            configured = false,
            availableModels = emptyList(),
            loadingModels = false,
            modelLoadError = null,
            tracks = emptyList(),
            albums = emptyList(),
            error = null,
            notice = MusicText.Localized(Res.string.music_gemini_notice_key_removed),
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val profileId = currentProfileId() ?: return
        MusicGeminiSettings.update(profileId) { it.copy(enabled = enabled) }
        _state.value = _state.value.copy(enabled = enabled)
        if (enabled && _state.value.tracks.isEmpty() && _state.value.albums.isEmpty() &&
            _state.value.configured
        ) {
            generate(context)
        }
    }

    fun setModel(model: String) {
        val profileId = currentProfileId() ?: return
        MusicGeminiSettings.update(profileId) { it.copy(model = model) }
        _state.value = _state.value.copy(model = model)
    }

    fun refreshModels() {
        val profileId = currentProfileId() ?: return
        val key = GeminiKeyStore.read(profileId)
        if (key.isNullOrBlank()) {
            _state.value = _state.value.copy(
                availableModels = emptyList(),
                modelLoadError = "Save a Gemini API key to load available models.",
            )
            return
        }
        if (_state.value.loadingModels || modelsJob?.isActive == true) return
        modelsJob?.cancel()
        _state.value = _state.value.copy(loadingModels = true, modelLoadError = null)
        modelsJob = scope.launch {
            val result = withContext(Dispatchers.IO) { MusicGeminiClient.listModels(key) }
            result.fold(
                onSuccess = { models ->
                    _state.value = _state.value.copy(
                        loadingModels = false,
                        availableModels = models,
                        modelLoadError = if (models.isEmpty()) {
                            "No models supporting generateContent were found for this key."
                        } else null,
                    )
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        loadingModels = false,
                        modelLoadError = error.message?.takeIf { it.isNotBlank() }
                            ?: "Couldn't load Gemini models. Try again.",
                    )
                },
            )
        }
    }

    fun setShareTopArtists(value: Boolean) = updateShare { it.copy(shareTopArtists = value) }
    fun setShareLiked(value: Boolean) = updateShare { it.copy(shareLiked = value) }
    fun setShareRecents(value: Boolean) = updateShare { it.copy(shareRecents = value) }
    fun setSharePlaylists(value: Boolean) = updateShare { it.copy(sharePlaylists = value) }

    private fun updateShare(change: (GeminiSettings) -> GeminiSettings) {
        val profileId = currentProfileId() ?: return
        val next = MusicGeminiSettings.update(profileId, change)
        _state.value = _state.value.copy(
            shareTopArtists = next.shareTopArtists,
            shareLiked = next.shareLiked,
            shareRecents = next.shareRecents,
            sharePlaylists = next.sharePlaylists,
        )
    }

    fun generate(context: Context) {
        if (_state.value.generating) return
        val app = context.applicationContext
        val profileId = currentProfileId() ?: return
        val key = GeminiKeyStore.read(profileId)
        if (key.isNullOrBlank()) {
            _state.value = _state.value.copy(
                error = MusicText.Localized(Res.string.music_gemini_error_add_key)
            )
            return
        }
        val settings = MusicGeminiSettings.load(profileId)
        generateJob?.cancel()
        generateJob = scope.launch {
            _state.value = _state.value.copy(generating = true, error = null, notice = null)
            val result = withContext(Dispatchers.IO) {
                MusicGeminiRepository.generate(key, settings)
            }
            result.fold(
                onSuccess = { picks ->
                    MusicGeminiCache.save(app, profileId, picks)
                    MusicGeminiSettings.markGenerated(profileId, "")
                    _state.value = _state.value.copy(
                        generating = false,
                        tracks = picks.tracks,
                        albums = picks.albums,
                        lastGeneratedAtMs = System.currentTimeMillis(),
                        notice = when {
                            picks.tracks.isEmpty() && picks.albums.isEmpty() ->
                                MusicText.Localized(Res.string.music_gemini_notice_nothing_playable)
                            picks.unmatched > 0 -> MusicText.Localized(
                                Res.string.music_gemini_notice_partial,
                                listOf(picks.tracks.size + picks.albums.size, picks.unmatched),
                            )
                            else -> MusicText.Localized(Res.string.music_gemini_notice_updated)
                        },
                    )
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        generating = false,
                        error = error.musicText(Res.string.music_gemini_error_failed),
                    )
                },
            )
        }
    }

    fun clearSuggestions(context: Context) {
        val profileId = currentProfileId() ?: return
        MusicGeminiCache.clear(context, profileId)
        _state.value = _state.value.copy(
            tracks = emptyList(),
            albums = emptyList(),
            notice = MusicText.Localized(Res.string.music_gemini_notice_cleared),
        )
    }

    private fun currentProfileId(): Int? =
        ProfileRepository.activeProfileId.takeIf { it > 0 }
}

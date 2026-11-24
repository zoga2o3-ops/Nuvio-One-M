package com.nuvio.app.music

import com.nuvio.app.music.engine.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the floating now-playing pill should show when a music video is open. While a video is
 * minimized, the pill represents the video (not the paused audio track), so its play/pause,
 * previous/next and tap actions stay meaningful.
 */
internal object MusicVideoHost {
    data class State(
        val active: Boolean = false,
        val minimized: Boolean = false,
        val video: Song? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    var onTogglePlay: (() -> Unit)? = null
    @Volatile
    var onNext: (() -> Unit)? = null
    @Volatile
    var onPrevious: (() -> Unit)? = null
    @Volatile
    var onOpen: (() -> Unit)? = null
    @Volatile
    var onClose: (() -> Unit)? = null

    fun publish(state: State) {
        _state.value = state
    }

    fun togglePlay() {
        onTogglePlay?.invoke()
    }

    fun next() {
        onNext?.invoke()
    }

    fun previous() {
        onPrevious?.invoke()
    }

    fun open() {
        onOpen?.invoke()
    }

    fun close() {
        onClose?.invoke()
    }

    fun clear() {
        _state.value = State()
        onTogglePlay = null
        onNext = null
        onPrevious = null
        onOpen = null
        onClose = null
    }
}

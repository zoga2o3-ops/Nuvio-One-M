package com.nuvio.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.settings.ThemeSettingsRepository

/** Which optional main sections the user wants in the navigation bars. */
internal data class SectionVisibility(
    val music: Boolean,
    val youtube: Boolean,
    val liveTv: Boolean,
)

@Composable
internal fun rememberSectionVisibility(): SectionVisibility {
    val music by ThemeSettingsRepository.musicTabEnabled.collectAsStateWithLifecycle()
    val youtube by ThemeSettingsRepository.youtubeTabEnabled.collectAsStateWithLifecycle()
    val liveTv by ThemeSettingsRepository.liveTvTabEnabled.collectAsStateWithLifecycle()
    return SectionVisibility(music = music, youtube = youtube, liveTv = liveTv)
}

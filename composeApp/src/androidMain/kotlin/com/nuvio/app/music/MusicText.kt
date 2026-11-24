package com.nuvio.app.music

import androidx.compose.runtime.Composable
import java.io.IOException
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A status or error message from Music code that runs outside composition. It carries a string
 * resource and its arguments, and the screen resolves them into the current language.
 */
sealed class MusicText {
    data class Localized(val res: StringResource, val args: List<Any> = emptyList()) : MusicText()
    data class Raw(val text: String) : MusicText()
}

class MusicTextException(val text: MusicText) : IOException()

internal fun Throwable.musicText(fallback: StringResource): MusicText =
    (this as? MusicTextException)?.text
        ?: message?.takeIf { it.isNotBlank() }?.let { MusicText.Raw(it) }
        ?: MusicText.Localized(fallback)

@Composable
internal fun MusicText.resolve(): String = when (this) {
    is MusicText.Raw -> text
    is MusicText.Localized -> stringResource(res, *args.toTypedArray())
}

package com.nuvio.app.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.livetv.model.EpgProgram
import com.nuvio.app.livetv.model.LiveChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_ui_watch
import org.jetbrains.compose.resources.stringResource

private val HeroBg = Color(0xFF1B1B1F)
private val HeroAccent = Color(0xFFE53935)

/**
 * The Now & Next card at the top of the Channels page: what's on, how far through, and the
 * actions people reach for first.
 */
@Composable
internal fun NowNextHero(
    channel: LiveChannel,
    program: EpgProgram?,
    now: Long,
    use24Hour: Boolean,
    favorite: Boolean,
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
    onMultiview: () -> Unit,
    onGuide: () -> Unit,
) {
    val clock = remember(use24Hour) { SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()) }
    val progress = program?.progress(now)?.coerceIn(0f, 1f) ?: 0f
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF3A1A1C), HeroBg),
                )
            )
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF26262B)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!channel.logo.isNullOrBlank()) {
                        AsyncImage(
                            model = channel.logo,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(7.dp),
                        )
                    } else {
                        Text(channel.name.take(2).uppercase(), fontWeight = FontWeight.Bold, color = Color(0xFFB0B0B0))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        "NOW · " + channel.name.uppercase(),
                        color = Color(0xFFFFB4B2),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        program?.displayTitle() ?: channel.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (program != null) "${clock.format(Date(program.startMs))} – ${clock.format(Date(program.stopMs))}"
                        else channel.group,
                        color = Color(0xFFB9B9C3),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = onWatch,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF111111)),
                ) {
                    Text(stringResource(Res.string.live_tv_ui_watch), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(99.dp)),
                color = HeroAccent,
                trackColor = Color(0x33FFFFFF),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                HeroAction(
                    label = if (favorite) "★ Favorites" else "☆ Favorites",
                    selected = favorite,
                    onClick = onFavorite,
                )
                HeroAction(label = "▦ Multiview", onClick = onMultiview)
                HeroAction(label = "Guide ›", onClick = onGuide)
            }
        }
    }
}

/** One short action chip in the hero. */
@Composable
private fun HeroAction(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) HeroAccent else Color(0xFF26262B))
            .border(1.dp, if (selected) HeroAccent else Color(0x22FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

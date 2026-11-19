package com.nuvio.app.youtube

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/** The YouTube settings, full screen over the YouTube page. [onClose] reloads the feed. */
@Composable
fun YouTubeSettingsSheet(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0E0E)) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.youtube_settings_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClose) { Text(stringResource(Res.string.action_done)) }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        YouTubeSettingsContent()
                    }
                }
            }
        }
    }
}

/**
 * Every YouTube setting, in the colours of whatever it sits in: the dialog above, or a page of the
 * app's own Settings. Changes apply as they're made; the YouTube page reloads its feed when it's next shown.
 */
@Composable
fun YouTubeSettingsContent() {
    // Outside a Material surface the default text colour is black, so take the app's own.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        YouTubeSettingsBody()
    }
}

@Composable
private fun YouTubeSettingsBody() {
    val context = LocalContext.current.applicationContext
    // Bumped after every change so the switches below re-read what's stored.
    var version by remember { mutableIntStateOf(0) }
    fun changed(block: () -> Unit) {
        block()
        version++
        YouTubeSession.settingsVersion++
    }
    @Suppress("UNUSED_EXPRESSION") version

    val accounts: YouTubeAccountViewModel = viewModel { YouTubeAccountViewModel(context) }
    val account by accounts.state.collectAsStateWithLifecycle()
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val profileName = profileState.activeProfile?.name
    var showAccounts by remember { mutableStateOf(false) }
    var showRegion by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { accounts.refresh() }

    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    Column(Modifier.fillMaxWidth()) {
        SettingsBlock(stringResource(Res.string.compose_settings_page_account), isTablet) {
            if (account.signedIn) {
                SettingsNavigationRow(
                    title = stringResource(
                        Res.string.youtube_settings_signed_in_as,
                        account.accountName ?: stringResource(Res.string.youtube_settings_your_account),
                    ),
                    description = stringResource(Res.string.youtube_settings_switch_or_sign_out),
                    isTablet = isTablet,
                    onClick = { showAccounts = true },
                )
                if (account.linkedAccount != null) {
                    SettingsGroupDivider(isTablet)
                    Text(
                        stringResource(
                            Res.string.youtube_settings_linked_to,
                            profileName ?: stringResource(Res.string.youtube_settings_this_profile),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondary(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                } else {
                    SettingsGroupDivider(isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.youtube_settings_link_to_nuvio),
                        description = null,
                        isTablet = isTablet,
                        onClick = accounts::linkToNuvioOne,
                    )
                }
            } else if (account.checked) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_auth_sign_in),
                    description = stringResource(Res.string.youtube_settings_sign_in_description),
                    isTablet = isTablet,
                    onClick = { accounts.startSignIn() },
                )
                account.linkedAccount?.let { linked ->
                    SettingsGroupDivider(isTablet)
                    Text(
                        stringResource(
                            Res.string.youtube_settings_linked_elsewhere,
                            profileName ?: stringResource(Res.string.youtube_settings_this_profile),
                            linked.name,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondary(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }

        SettingsBlock(stringResource(Res.string.compose_settings_page_playback), isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.youtube_settings_autoplay),
                description = stringResource(Res.string.youtube_settings_autoplay_description),
                checked = YouTubeSettings.autoplayNext(context),
                isTablet = isTablet,
            ) { changed { YouTubeSettings.setAutoplayNext(context, it) } }
            SettingsGroupDivider(isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.youtube_settings_start_at),
                description = null,
                isTablet = isTablet,
                trailingContent = {
                    Text(
                        YouTubeSettings.qualityLabel(YouTubeSettings.preferredHeight(context)),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
            ) {
                val choices = YouTubeSettings.qualityChoices
                changed {
                    YouTubeSettings.setPreferredHeight(
                        context,
                        choices[(choices.indexOf(YouTubeSettings.preferredHeight(context)) + 1) % choices.size],
                    )
                }
            }
            SettingsGroupDivider(isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.youtube_settings_preferred_codec),
                description = null,
                isTablet = isTablet,
                trailingContent = {
                    Text(
                        YouTubeSettings.codecLabel(YouTubeSettings.codecPreference(context)),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
            ) {
                val choices = YouTubeSettings.codecChoices
                changed {
                    YouTubeSettings.setCodecPreference(
                        context,
                        choices[(choices.indexOf(YouTubeSettings.codecPreference(context)) + 1) % choices.size],
                    )
                }
            }
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.youtube_settings_remember_tab),
                description = stringResource(Res.string.youtube_settings_remember_tab_description),
                checked = YouTubeSettings.rememberLastTab(context),
                isTablet = isTablet,
            ) { changed { YouTubeSettings.setRememberLastTab(context, it) } }
        }

        SettingsBlock(stringResource(Res.string.youtube_settings_section_sponsorblock), isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.youtube_settings_skip_marked),
                description = stringResource(Res.string.youtube_settings_skip_marked_description),
                checked = YouTubeSettings.sponsorBlockEnabled(context),
                isTablet = isTablet,
            ) { changed { YouTubeSettings.setSponsorBlockEnabled(context, it) } }
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.youtube_settings_ask_before_skipping),
                description = stringResource(Res.string.youtube_settings_ask_before_skipping_description),
                checked = YouTubeSettings.sponsorAsk(context),
                isTablet = isTablet,
            ) { changed { YouTubeSettings.setSponsorAsk(context, it) } }
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.youtube_settings_show_notice),
                description = null,
                checked = YouTubeSettings.sponsorNotice(context),
                isTablet = isTablet,
            ) { changed { YouTubeSettings.setSponsorNotice(context, it) } }
            val on = YouTubeSettings.sponsorCategoriesOn(context)
            YouTubeSettings.sponsorCategories.forEach { (id, labelRes) ->
                SettingsGroupDivider(isTablet)
                SettingsSwitchRow(
                    title = stringResource(labelRes),
                    description = null,
                    checked = id in on,
                    isTablet = isTablet,
                ) { changed { YouTubeSettings.setSponsorCategory(context, id, it) } }
            }
        }

        SettingsBlock(stringResource(Res.string.youtube_settings_section_feeds), isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.youtube_settings_feed_preview),
                description = stringResource(Res.string.youtube_settings_feed_preview_description),
                checked = YouTubeSettings.feedPreview(context),
                isTablet = isTablet,
            ) { changed { YouTubeSettings.setFeedPreview(context, it) } }
            SettingsGroupDivider(isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.youtube_settings_content_region),
                description = null,
                isTablet = isTablet,
                trailingContent = {
                    Text(
                        contentRegionLabel(YouTubeSettings.contentRegion(context)),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
            ) { showRegion = true }
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.youtube_settings_hide_shorts),
                description = null,
                checked = YouTubeSettings.hideShorts(context),
                isTablet = isTablet,
            ) { changed { YouTubeSettings.setHideShorts(context, it) } }
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.youtube_settings_hide_watched),
                description = null,
                checked = YouTubeSettings.hideWatched(context),
                isTablet = isTablet,
            ) { changed { YouTubeSettings.setHideWatched(context, it) } }
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.youtube_settings_hide_upcoming),
                description = stringResource(Res.string.youtube_settings_hide_upcoming_description),
                checked = YouTubeSettings.hideUpcoming(context),
                isTablet = isTablet,
            ) { changed { YouTubeSettings.setHideUpcoming(context, it) } }
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.youtube_settings_dearrow_titles),
                description = stringResource(Res.string.youtube_settings_dearrow_titles_description),
                checked = YouTubeSettings.deArrowTitles(context),
                isTablet = isTablet,
            ) { changed { YouTubeSettings.setDeArrowTitles(context, it) } }
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.youtube_settings_dearrow_thumbnails),
                description = stringResource(Res.string.youtube_settings_dearrow_thumbnails_description),
                checked = YouTubeSettings.deArrowThumbnails(context),
                isTablet = isTablet,
            ) { changed { YouTubeSettings.setDeArrowThumbnails(context, it) } }
        }

        SettingsBlock(stringResource(Res.string.youtube_settings_section_tabs), isTablet) {
            val hidden = YouTubeSettings.hiddenTabs(context)
            val order = YouTubeSettings.tabOrder(context)
            order.forEachIndexed { i, tab ->
                if (i > 0) SettingsGroupDivider(isTablet)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(tab.titleRes), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(
                        enabled = i > 0,
                        onClick = {
                            changed { YouTubeSettings.setTabOrder(context, order.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }
                        },
                    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(Res.string.settings_playback_toolbar_move_up)) }
                    IconButton(
                        enabled = i < order.lastIndex,
                        onClick = {
                            changed { YouTubeSettings.setTabOrder(context, order.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }
                        },
                    ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(Res.string.settings_playback_toolbar_move_down)) }
                    Switch(checked = tab.name !in hidden, onCheckedChange = { changed { YouTubeSettings.setTabVisible(context, tab, it) } })
                }
            }
        }

        SettingsBlock(stringResource(Res.string.youtube_settings_section_hidden_words), isTablet) {
            var word by remember { mutableStateOf("") }
            Text(
                stringResource(Res.string.youtube_settings_hidden_words_description),
                style = MaterialTheme.typography.bodySmall,
                color = secondary(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(Res.string.youtube_settings_hidden_word_placeholder)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                        if (word.isNotBlank()) changed { YouTubeSettings.addHiddenKeyword(context, word.trim()) }
                        word = ""
                    }),
                )
                Button(onClick = {
                    if (word.isNotBlank()) changed { YouTubeSettings.addHiddenKeyword(context, word.trim()) }
                    word = ""
                }) { Text(stringResource(Res.string.youtube_settings_add)) }
            }
            YouTubeSettings.hiddenKeywords(context).forEachIndexed { i, kw ->
                SettingsGroupDivider(isTablet)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(kw, Modifier.weight(1f))
                    TextButton(onClick = { changed { YouTubeSettings.removeHiddenKeyword(context, kw) } }) {
                        Text(stringResource(Res.string.action_remove))
                    }
                }
            }
        }

        SettingsBlock(stringResource(Res.string.youtube_settings_section_hidden_channels), isTablet) {
            val channels = YouTubeSettings.hiddenChannels(context)
            if (channels.isEmpty()) {
                Text(
                    stringResource(Res.string.youtube_settings_no_hidden_channels),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            channels.forEachIndexed { i, (id, name) ->
                if (i > 0) SettingsGroupDivider(isTablet)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, Modifier.weight(1f))
                    TextButton(onClick = { changed { YouTubeSettings.unhideChannel(context, id) } }) {
                        Text(stringResource(Res.string.youtube_settings_show_again))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    YouTubeSignInDialog(accounts, account)
    if (showRegion) {
        ContentRegionDialog(
            current = YouTubeSettings.contentRegion(context),
            onSelect = { region ->
                changed {
                    YouTubeSettings.setContentRegion(context, region)
                    YouTubeContentRegion.apply(context)
                }
                showRegion = false
            },
            onDismiss = { showRegion = false },
        )
    }
    if (showAccounts) {
        YouTubeAccountsDialog(
            account = account,
            onSelect = { id -> showAccounts = false; accounts.selectAccount(id) { YouTubeSession.settingsVersion++ } },
            onAdd = { showAccounts = false; accounts.startSignIn() },
            onSignOut = { showAccounts = false; accounts.signOut { YouTubeSession.settingsVersion++ } },
            onDismiss = { showAccounts = false },
            profileName = profileName,
            onLinkToNuvioOne = accounts::linkToNuvioOne,
        )
    }
    // Signing in finishes on another device, so the feeds are refreshed when it does.
    LaunchedEffect(account.signedIn, account.accountName) { YouTubeSession.settingsVersion++ }
}

@Composable
private fun SettingsBlock(title: String, isTablet: Boolean, content: @Composable ColumnScope.() -> Unit) {
    SettingsSection(title = title, isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) { content() }
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun secondary(): Color = LocalContentColor.current.copy(alpha = 0.65f)

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
    HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.14f))
}

@Composable
private fun Choice(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = secondary())
    }
}

@Composable
private fun Toggle(label: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondary()) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ContentRegionDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val defaultLabel = stringResource(Res.string.youtube_settings_region_device)
    val options = remember(defaultLabel) { contentRegionChoices(defaultLabel) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0E0E)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.youtube_settings_content_region), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
                }
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    items(options, key = { it.first }) { (code, name) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(code) }.padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (code == current) Text("\u2713", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun contentRegionChoices(defaultLabel: String): List<Pair<String, String>> =
    listOf("" to defaultLabel) +
        Locale.getISOCountries().map { code ->
            code to (runCatching {
                Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault())
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: code)
        }.sortedBy { it.second }

@Composable
private fun contentRegionLabel(region: String): String =
    if (region.isBlank()) stringResource(Res.string.youtube_settings_region_device)
    else runCatching {
        Locale.Builder().setRegion(region).build().getDisplayCountry(Locale.getDefault())
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: region

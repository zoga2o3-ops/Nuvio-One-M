package com.nuvio.app.livetv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuvio.app.livetv.LiveTv
import com.nuvio.app.livetv.model.ChannelSort
import com.nuvio.app.livetv.model.LiveTvGroupTags
import com.nuvio.app.livetv.model.LiveTvSettings
import com.nuvio.app.livetv.record.SmbClient
import com.nuvio.app.livetv.record.SmbConfig
import com.nuvio.app.livetv.record.SmbSettings
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The Live TV settings page, shown inside Nuvio's settings. */
@Composable
fun LiveTvSettingsContent() {
    val context = LocalContext.current.applicationContext
    val live = remember { LiveTv.get(context) }
    val settings by live.prefs.settings.collectAsStateWithLifecycle(initialValue = LiveTvSettings())
    val profileId by live.profile.activeProfileId.collectAsStateWithLifecycle()
    // The same sources the Live TV page uses, so playlists and guides can be managed here too.
    val sources: LiveTvViewModel = viewModel { LiveTvViewModel(context) }
    val playlists by sources.playlists.collectAsStateWithLifecycle()
    val epgs by sources.epgSources.collectAsStateWithLifecycle()
    val playlistAccounts by sources.accounts.collectAsStateWithLifecycle()
    val sourceStatus by sources.status.collectAsStateWithLifecycle()
    val user by sources.user.collectAsStateWithLifecycle()
    val allChannels by sources.allChannels.collectAsStateWithLifecycle()
    var showPlaylists by remember { mutableStateOf(false) }
    var showPlaylistVisibility by remember { mutableStateOf(false) }
    var showAddPlaylist by remember { mutableStateOf(false) }
    var showAddEpg by remember { mutableStateOf(false) }
    var showGroupTags by remember { mutableStateOf(false) }
    var groupTags by remember { mutableStateOf<List<LiveTvGroupTags.Tag>>(emptyList()) }
    var scanningTags by remember { mutableStateOf(false) }
    val notifications = rememberLiveTvNotificationPermission()
    val scope = rememberCoroutineScope()
    fun change(transform: (LiveTvSettings) -> LiveTvSettings) {
        scope.launch { live.prefs.updateSettings(transform) }
    }
    var pinStep by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var editHomeRowTitle by remember { mutableStateOf(false) }
    var showSmbConfig by remember { mutableStateOf(false) }
    var smbConfig by remember { mutableStateOf(SmbSettings.load(context)) }

    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxWidth()) {
            SettingsBlock(stringResource(Res.string.live_tv_ui_playlists_and_guide), isTablet) {
            ActionLine(
                title = stringResource(Res.string.live_tv_ui_playlists_and_guide),
                detail = if (sourceStatus.loading) {
                    sourceStatus.message?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.live_tv_ui_working)
                } else {
                    stringResource(Res.string.live_tv_settings_source_counts, playlists.size, epgs.size)
                },
            ) { showPlaylists = true }
            if (playlists.isNotEmpty()) {
                ActionLine(
                    title = stringResource(Res.string.live_tv_ui_playlists_on_this_profile),
                    detail = stringResource(Res.string.live_tv_ui_playlists_on_this_profile_hint),
                ) { showPlaylistVisibility = true }
            }
            ActionLine(
                title = stringResource(Res.string.live_tv_ui_update_all),
                detail = stringResource(Res.string.live_tv_settings_update_all_description),
            ) { sources.refresh() }

            }
            SettingsBlock(stringResource(Res.string.live_tv_section_channels), isTablet) {
            ChoiceRow(
                stringResource(Res.string.live_tv_ui_sort_channels),
                when (settings.channelSort) { ChannelSort.PLAYLIST -> stringResource(Res.string.live_tv_ui_as_in_the_playlist); ChannelSort.NUMBER -> stringResource(Res.string.live_tv_ui_by_number); ChannelSort.NAME -> stringResource(Res.string.live_tv_ui_by_name) },
                listOf(stringResource(Res.string.live_tv_ui_as_in_the_playlist) to ChannelSort.PLAYLIST, stringResource(Res.string.live_tv_ui_by_number) to ChannelSort.NUMBER, stringResource(Res.string.live_tv_ui_by_name) to ChannelSort.NAME),
            ) { v -> change { it.copy(channelSort = v) } }
            ChoiceRow(
                stringResource(Res.string.live_tv_ui_order_of_groups),
                when (settings.groupSort) { "playlist" -> stringResource(Res.string.live_tv_ui_as_in_the_playlist); "name" -> "A to Z"; else -> "Mine" },
                listOf("Mine (long-press a group to move it)" to "custom", stringResource(Res.string.live_tv_ui_as_in_the_playlist) to "playlist", "A to Z" to "name"),
            ) { v -> change { it.copy(groupSort = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_remember_the_last_group), "Open Live TV on the group you were in.", settings.rememberLastGroup) { v -> change { it.copy(rememberLastGroup = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_all_channels_group), null, settings.showAllChannelsGroup) { v -> change { it.copy(showAllChannelsGroup = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_favorites_group), null, settings.showFavoritesGroup) { v -> change { it.copy(showFavoritesGroup = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_recent_group), "Channels you watched lately.", settings.showRecentGroup) { v -> change { it.copy(showRecentGroup = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_channel_count_on_groups), null, settings.showGroupCounts) { v -> change { it.copy(showGroupCounts = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_channel_numbers), null, settings.showChannelNumbers) { v -> change { it.copy(showChannelNumbers = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_channel_logos), null, settings.showChannelLogos) { v -> change { it.copy(showChannelLogos = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_compact_rows), "Fit more channels on screen.", settings.compactRows) { v -> change { it.copy(compactRows = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_number_channels_in_order), "1, 2, 3 down the list you see, whatever the playlist says.", settings.sequentialNumbers) { v -> change { it.copy(sequentialNumbers = v) } }
            ActionLine(
                title = stringResource(Res.string.live_tv_ui_filter_by_group_tag),
                detail = if (user.groupTagFilters.isEmpty()) {
                    stringResource(Res.string.live_tv_ui_filter_by_group_tag_description)
                } else {
                    stringResource(Res.string.live_tv_ui_group_tags_selected, user.groupTagFilters.size)
                },
            ) { showGroupTags = true }
            var editNames by remember { mutableStateOf(false) }
            ActionLine(stringResource(Res.string.live_tv_ui_clean_up_channel_names), if (settings.nameRemovals.isBlank()) stringResource(Res.string.live_tv_ui_remove_tags_hint) else stringResource(Res.string.live_tv_ui_removing, settings.nameRemovals)) { editNames = true }
            SwitchRow(stringResource(Res.string.live_tv_ui_also_clean_group_names), "Take the same words off group names too.", settings.cleanGroupNames) { v -> change { it.copy(cleanGroupNames = v) } }
            if (editNames) {
                TextPromptDialog(
                    title = stringResource(Res.string.live_tv_ui_words_to_remove_from_names),
                    initial = settings.nameRemovals,
                    hint = stringResource(Res.string.live_tv_ui_separate_them_with_commas_for_example_us_hd_fhd_4k),
                    onOk = { v -> change { it.copy(nameRemovals = v) }; editNames = false },
                    onDismiss = { editNames = false },
                )
            }
            ActionLine(stringResource(Res.string.live_tv_ui_put_channels_back_in_playlist_order), "Forget the order you set with Move earlier / later.") { scope.launch { live.prefs.resetChannelOrder() } }

            }
            SettingsBlock(stringResource(Res.string.live_tv_ui_guide), isTablet) {
            SwitchRow(stringResource(Res.string.live_tv_ui_24_hour_clock), null, settings.use24HourClock) { v -> change { it.copy(use24HourClock = v) } }
            StepperRow(stringResource(Res.string.live_tv_ui_shift_the_guide), "${settings.epgOffsetMinutes / 60.0} hours".replace(".0 ", " "),
                onMinus = { change { it.copy(epgOffsetMinutes = (it.epgOffsetMinutes - 30).coerceAtLeast(-12 * 60)) } },
                onPlus = { change { it.copy(epgOffsetMinutes = (it.epgOffsetMinutes + 30).coerceAtMost(12 * 60)) } })
            StepperRow(stringResource(Res.string.live_tv_ui_days_of_guide_to_keep), "${settings.epgFutureDays} days ahead",
                onMinus = { change { it.copy(epgFutureDays = (it.epgFutureDays - 1).coerceAtLeast(1)) } },
                onPlus = { change { it.copy(epgFutureDays = (it.epgFutureDays + 1).coerceAtMost(14)) } })
            StepperRow(stringResource(Res.string.live_tv_ui_past_programmes), "${settings.epgPastHours} hours back",
                onMinus = { change { it.copy(epgPastHours = (it.epgPastHours - 12).coerceAtLeast(0)) } },
                onPlus = { change { it.copy(epgPastHours = (it.epgPastHours + 12).coerceAtMost(7 * 24)) } })
            ChoiceRow(
                stringResource(Res.string.live_tv_ui_guide_size),
                when (settings.guideSize) { stringResource(Res.string.live_tv_ui_compact) -> "Compact"; stringResource(Res.string.live_tv_ui_large) -> "Large"; stringResource(Res.string.live_tv_ui_xlarge) -> "Extra large"; else -> "Standard" },
                listOf("Compact" to stringResource(Res.string.live_tv_ui_compact), "Standard" to "STANDARD", "Large" to stringResource(Res.string.live_tv_ui_large), "Extra large" to stringResource(Res.string.live_tv_ui_xlarge)),
            ) { v -> change { it.copy(guideSize = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_show_start_and_end_times), "Both times under each show, in taller rows.", settings.showProgramTimes) { v -> change { it.copy(showProgramTimes = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_show_episode_numbers), "\"S2 E5\" under a show's name.", settings.showEpisodeNumbers) { v -> change { it.copy(showEpisodeNumbers = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_line_for_the_current_time), null, settings.showNowLine) { v -> change { it.copy(showNowLine = v) } }
            ChoiceRow(
                stringResource(Res.string.live_tv_ui_refresh_the_guide),
                "Every ${settings.epgRefreshHours} hours",
                listOf(6, 12, 24, 48).map { "Every $it hours" to it },
            ) { v -> change { it.copy(epgRefreshHours = v) } }
            ChoiceRow(
                stringResource(Res.string.live_tv_ui_refresh_playlists),
                "Every ${settings.playlistRefreshHours} hours",
                listOf(6, 12, 24, 48, 168).map { "Every $it hours" to it },
            ) { v -> change { it.copy(playlistRefreshHours = v) } }

            }
            SettingsBlock(stringResource(Res.string.compose_settings_page_playback), isTablet) {
            SwitchRow(stringResource(Res.string.live_tv_ui_reconnect_when_a_channel_drops), "Try again a few times before giving up.", settings.autoReconnect) { v -> change { it.copy(autoReconnect = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_prefer_hls_for_catch_up), "Gives replays a length, so you can skip around.", settings.catchupPreferHls) { v -> change { it.copy(catchupPreferHls = v) } }
            StepperRow(stringResource(Res.string.live_tv_ui_catch_up_time_shift), "${settings.catchupShiftMinutes} minutes",
                onMinus = { change { it.copy(catchupShiftMinutes = (it.catchupShiftMinutes - 5).coerceAtLeast(-180)) } },
                onPlus = { change { it.copy(catchupShiftMinutes = (it.catchupShiftMinutes + 5).coerceAtMost(180)) } })

            ChoiceRow(
                stringResource(Res.string.live_tv_ui_buffer_size),
                when (settings.bufferSize) { "small" -> stringResource(Res.string.live_tv_ui_small); "large" -> stringResource(Res.string.live_tv_ui_large); "xlarge" -> stringResource(Res.string.live_tv_ui_extra_large); else -> stringResource(Res.string.live_tv_ui_normal) },
                listOf(stringResource(Res.string.live_tv_ui_small_fastest) to "small", stringResource(Res.string.live_tv_ui_normal) to "normal", stringResource(Res.string.live_tv_ui_large_poor) to "large", stringResource(Res.string.live_tv_ui_extra_large) to "xlarge"),
            ) { v -> change { it.copy(bufferSize = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_try_another_copy_when_a_channel_won_t_play), "Another channel with the same name takes over.", settings.autoBackupChannel) { v -> change { it.copy(autoBackupChannel = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_closed_captions_on_by_default), "Turn captions on when a channel has them.", settings.captionsByDefault) { v -> change { it.copy(captionsByDefault = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_dolby_dts_passthrough), "Send surround sound on untouched. Off: the phone decodes it.", settings.audioPassthrough) { v -> change { it.copy(audioPassthrough = v) } }
            ChoiceRow(
                stringResource(Res.string.live_tv_ui_frame_rate),
                if (settings.frameRateMode == "OFF") stringResource(Res.string.live_tv_ui_leave_as_it_is) else stringResource(Res.string.live_tv_ui_match_the_picture),
                listOf(stringResource(Res.string.live_tv_ui_leave_as_it_is) to "OFF", stringResource(Res.string.live_tv_ui_match_the_picture_motion) to "START"),
            ) { v -> change { it.copy(frameRateMode = v) } }

            }
            SettingsBlock(stringResource(Res.string.live_tv_ui_pause_and_rewind), isTablet) {
            SwitchRow(stringResource(Res.string.live_tv_ui_pause_and_rewind), "Pausing a channel keeps recording it, so you carry on where you stopped.", settings.timeshiftEnabled) { v -> change { it.copy(timeshiftEnabled = v) } }
            StepperRow(stringResource(Res.string.live_tv_ui_keep_the_last), "${settings.timeshiftMinutes} minutes",
                onMinus = { change { it.copy(timeshiftMinutes = (it.timeshiftMinutes - 5).coerceAtLeast(5)) } },
                onPlus = { change { it.copy(timeshiftMinutes = (it.timeshiftMinutes + 5).coerceAtMost(30)) } })
            SwitchRow(stringResource(Res.string.live_tv_ui_rewind_anytime), "Keep the last minutes while you watch, so you can rewind without pausing. Uses a second connection to your provider.", settings.rewindAlways) { v -> change { it.copy(rewindAlways = v) } }

            }
            SettingsBlock(stringResource(Res.string.yt_tab_sports), isTablet) {
            val sports = remember { live.sports }
            val sportsSettings by sports.settings.collectAsStateWithLifecycle()
            if (!notifications.canNotify) {
                ActionLine(
                    stringResource(Res.string.live_tv_ui_turn_on_notifications),
                    stringResource(Res.string.live_tv_ui_scoring_alerts_and_team_reminders_need_notification_permissi),
                    notifications.ask,
                )
            }
            ChoiceRow(
                stringResource(Res.string.live_tv_ui_your_region),
                com.nuvio.app.livetv.sports.SportsCatalog.regionName(com.nuvio.app.livetv.sports.SportsCatalog.regionOf(sportsSettings)),
                com.nuvio.app.livetv.sports.SportsCatalog.regions.map { (code, name) -> name to code },
            ) { v -> sports.updateSettings { it.copy(region = v) } }
            ChoiceRow(
                stringResource(Res.string.live_tv_ui_channels_from),
                com.nuvio.app.livetv.sports.ChannelRegions.all.firstOrNull { it.first == sportsSettings.channelRegion }?.second ?: stringResource(Res.string.live_tv_ui_all_regions),
                com.nuvio.app.livetv.sports.ChannelRegions.all.map { (code, name) -> name to code },
            ) { v -> sports.updateSettings { it.copy(channelRegion = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_video_window), "Watch games in a window at the top of the Sports page. Off: games open full screen.", sportsSettings.videoWindow) { v -> sports.updateSettings { it.copy(videoWindow = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_games_in_the_calendar), "Show games of the teams you follow on the Calendar.", settings.calendarSports) { v -> change { it.copy(calendarSports = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_hide_scores), "Keep results off the screen until you're ready.", sportsSettings.hideScores) { v -> sports.updateSettings { it.copy(hideScores = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_scoring_alerts), "A notification when a team you follow scores, and the final score when the game ends.", sportsSettings.scoringAlerts) { v ->
                if (v) notifications.ask()
                sports.updateSettings { it.copy(scoringAlerts = v) }
            }
            SwitchRow(stringResource(Res.string.live_tv_ui_remind_me_about_my_teams), "A notification before every game of a team you follow, without asking for each one.", sportsSettings.remindMyTeams) { v ->
                if (v) notifications.ask()
                sports.updateSettings { it.copy(remindMyTeams = v) }
            }
            SwitchRow(stringResource(Res.string.live_tv_ui_switch_to_the_game), "Open the game's channel by itself a minute before it starts, with a few seconds to cancel. Needs the app open.", sportsSettings.autoSwitch) { v -> sports.updateSettings { it.copy(autoSwitch = v) } }
            StepperRow(stringResource(Res.string.live_tv_ui_remind_me_before_a_game), "${sportsSettings.remindMinutes} minutes",
                onMinus = { sports.updateSettings { it.copy(remindMinutes = (it.remindMinutes - 5).coerceAtLeast(0)) } },
                onPlus = { sports.updateSettings { it.copy(remindMinutes = (it.remindMinutes + 5).coerceAtMost(120)) } })
            SwitchRow(stringResource(Res.string.live_tv_ui_only_games_on_my_channels), "Leave out games none of your channels show.", sportsSettings.onlyOnMyChannels) { v -> sports.updateSettings { it.copy(onlyOnMyChannels = v) } }
            SwitchRow(stringResource(Res.string.live_tv_ui_best_channel_first), "Put the likeliest channel at the top of a game's list.", sportsSettings.bestChannelFirst) { v -> sports.updateSettings { it.copy(bestChannelFirst = v) } }
            ActionLine(stringResource(Res.string.live_tv_ui_forget_the_channels_i_picked), "Undo every \"Wrong channel?\" fix.") { sports.clearFixes() }

            }
            SettingsBlock(stringResource(Res.string.live_tv_ui_recordings), isTablet) {
            StepperRow(stringResource(Res.string.live_tv_ui_start_recording_early), "${settings.recordPreMinutes} minutes",
                onMinus = { change { it.copy(recordPreMinutes = (it.recordPreMinutes - 1).coerceAtLeast(0)) } },
                onPlus = { change { it.copy(recordPreMinutes = (it.recordPreMinutes + 1).coerceAtMost(30)) } })
            StepperRow(stringResource(Res.string.live_tv_ui_keep_recording_after_the_show), "${settings.recordPostMinutes} minutes",
                onMinus = { change { it.copy(recordPostMinutes = (it.recordPostMinutes - 1).coerceAtLeast(0)) } },
                onPlus = { change { it.copy(recordPostMinutes = (it.recordPostMinutes + 1).coerceAtMost(60)) } })
            val storage = remember { com.nuvio.app.livetv.record.RecordingStorage.choices(context) }
            ChoiceRow(
                stringResource(Res.string.live_tv_ui_save_recordings_to),
                storage.firstOrNull { it.id == settings.recordingStorage }?.label ?: stringResource(Res.string.live_tv_ui_this_device),
                storage.map { "${it.label}  (${com.nuvio.app.livetv.record.RecordingStorage.sizeLabel(it.freeBytes)} free)" to it.id },
            ) { v -> change { it.copy(recordingStorage = v) } }
            SwitchRow(
                stringResource(Res.string.live_tv_ui_save_recordings_to_smb),
                stringResource(Res.string.live_tv_ui_smb_mirror_description),
                settings.recordingSmbEnabled,
            ) { enabled ->
                if (enabled && smbConfig == null) {
                    showSmbConfig = true
                } else {
                    change { it.copy(recordingSmbEnabled = enabled) }
                }
            }
            ActionLine(
                title = stringResource(Res.string.live_tv_ui_smb_share),
                detail = smbConfig?.label ?: stringResource(Res.string.live_tv_ui_smb_not_set),
                onClick = { showSmbConfig = true },
            )
            ChoiceRow(
                stringResource(Res.string.live_tv_ui_when_space_runs_low),
                if (settings.recordingSpaceRule == "DELETE_OLDEST") stringResource(Res.string.live_tv_ui_delete_the_oldest_recording) else stringResource(Res.string.live_tv_ui_stop_recording),
                listOf(stringResource(Res.string.live_tv_ui_stop_recording) to "STOP", stringResource(Res.string.live_tv_ui_delete_the_oldest_recording) to "DELETE_OLDEST"),
            ) { v -> change { it.copy(recordingSpaceRule = v) } }

            }
            SettingsBlock(stringResource(Res.string.live_tv_ui_on_screen), isTablet) {
            SwitchRow(stringResource(Res.string.live_tv_ui_4k_fhd_hd_badges), "Next to channels you've watched.", settings.showQualityBadges) { v -> change { it.copy(showQualityBadges = v) } }
            ChoiceRow(
                stringResource(Res.string.live_tv_ui_clock_over_the_picture),
                if (settings.clockMode == "always") stringResource(Res.string.live_tv_ui_always) else stringResource(Res.string.settings_playback_auto_sync_tolerance_off),
                listOf(stringResource(Res.string.settings_playback_auto_sync_tolerance_off) to "never", "Always" to "always"),
            ) { v -> change { it.copy(clockMode = v) } }
            ChoiceRow(
                stringResource(Res.string.live_tv_ui_clock_position),
                when (settings.clockPosition) { "top_left" -> stringResource(Res.string.live_tv_ui_top_left); "bottom_left" -> stringResource(Res.string.live_tv_ui_bottom_left); "bottom_right" -> stringResource(Res.string.live_tv_ui_bottom_right); else -> stringResource(Res.string.live_tv_ui_top_right) },
                listOf(stringResource(Res.string.live_tv_ui_top_left) to "top_left", stringResource(Res.string.live_tv_ui_top_right) to "top_right", stringResource(Res.string.live_tv_ui_bottom_left) to "bottom_left", stringResource(Res.string.live_tv_ui_bottom_right) to "bottom_right"),
            ) { v -> change { it.copy(clockPosition = v) } }
            ChoiceRow(
                stringResource(Res.string.live_tv_ui_clock_size),
                when (settings.clockSize) { "small" -> stringResource(Res.string.live_tv_ui_small); "large" -> stringResource(Res.string.live_tv_ui_large); else -> stringResource(Res.string.live_tv_ui_medium) },
                listOf(stringResource(Res.string.live_tv_ui_small) to "small", stringResource(Res.string.live_tv_ui_medium) to "medium", stringResource(Res.string.live_tv_ui_large) to "large"),
            ) { v -> change { it.copy(clockSize = v) } }
            StepperRow(stringResource(Res.string.live_tv_ui_clock_opacity), "${settings.clockOpacity} %",
                onMinus = { change { it.copy(clockOpacity = (it.clockOpacity - 10).coerceAtLeast(20)) } },
                onPlus = { change { it.copy(clockOpacity = (it.clockOpacity + 10).coerceAtMost(100)) } })
            SwitchRow(stringResource(Res.string.live_tv_ui_clock_background), "A soft dark pill behind the clock.", settings.clockBackground) { v -> change { it.copy(clockBackground = v) } }

            }
            SettingsBlock(stringResource(Res.string.live_tv_ui_starting), isTablet) {
            var startPage by remember { mutableStateOf(LiveTvStart.startPage(context)) }
            ChoiceRow(
                stringResource(Res.string.live_tv_ui_open_the_app_on),
                if (startPage == "LIVE_TV") stringResource(Res.string.compose_nav_live_tv) else stringResource(Res.string.compose_nav_home),
                listOf(stringResource(Res.string.compose_nav_home) to "HOME", stringResource(Res.string.compose_nav_live_tv) to "LIVE_TV"),
            ) { v -> LiveTvStart.setStartPage(context, v); startPage = v }
            SwitchRow(stringResource(Res.string.live_tv_ui_play_the_last_channel), "When Live TV opens, start the channel you watched last.", settings.autoPlayLastChannel) { v -> change { it.copy(autoPlayLastChannel = v) } }

            }
            SettingsBlock(stringResource(Res.string.live_tv_ui_home_screen), isTablet) {
            val homeRowOn = settings.homeRowEnabled && profileId !in settings.homeRowHiddenProfiles
            SwitchRow(
                stringResource(Res.string.live_tv_ui_home_row),
                "Show your channels on Nuvio's Home screen for this profile.",
                homeRowOn,
            ) { v ->
                change {
                    it.copy(
                        homeRowEnabled = true,
                        homeRowHiddenProfiles = if (v) it.homeRowHiddenProfiles - profileId else it.homeRowHiddenProfiles + profileId,
                    )
                }
            }
            if (homeRowOn) {
                ChoiceRow(
                    stringResource(Res.string.live_tv_ui_channels_in_the_row),
                    when (settings.homeRowSource) {
                        "recent" -> stringResource(Res.string.live_tv_ui_recently_watched)
                        "favorites", "" -> stringResource(Res.string.live_tv_filter_favorites)
                        else -> user.customGroups.firstOrNull { it.id == settings.homeRowSource }?.name
                            ?: stringResource(Res.string.live_tv_filter_favorites)
                    },
                    buildList {
                        add(stringResource(Res.string.live_tv_filter_favorites) to "favorites")
                        add(stringResource(Res.string.live_tv_ui_recently_watched) to "recent")
                        user.customGroups.forEach { add(it.name to it.id) }
                    },
                ) { v -> change { it.copy(homeRowSource = v) } }
                val liveTvTitle = stringResource(Res.string.live_tv_title)
                ActionLine(
                    stringResource(Res.string.live_tv_ui_row_title),
                    settings.homeRowTitle.ifBlank { liveTvTitle },
                ) { editHomeRowTitle = true }
                ChoiceRow(
                    stringResource(Res.string.live_tv_ui_row_order),
                    when (settings.homeRowSort) {
                        "number" -> stringResource(Res.string.live_tv_ui_channel_number)
                        "ending" -> stringResource(Res.string.live_tv_ui_ending_soonest)
                        else -> stringResource(Res.string.live_tv_ui_your_order)
                    },
                    listOf(
                        stringResource(Res.string.live_tv_ui_your_order) to "yours",
                        stringResource(Res.string.live_tv_ui_channel_number) to "number",
                        stringResource(Res.string.live_tv_ui_ending_soonest) to "ending",
                    ),
                ) { v -> change { it.copy(homeRowSort = v) } }
                ChoiceRow(
                    stringResource(Res.string.live_tv_ui_channels_shown),
                    if (settings.homeRowLimit == 0) stringResource(Res.string.live_tv_ui_filter_all) else "${settings.homeRowLimit}",
                    listOf(
                        "10" to 10,
                        "20" to 20,
                        "40" to 40,
                        stringResource(Res.string.live_tv_ui_filter_all) to 0,
                    ),
                ) { v -> change { it.copy(homeRowLimit = v) } }
                SwitchRow(stringResource(Res.string.live_tv_ui_hide_no_listings), null, settings.homeRowHidePlaceholders) { v -> change { it.copy(homeRowHidePlaceholders = v) } }
                SwitchRow(stringResource(Res.string.live_tv_ui_show_next), null, settings.homeRowShowNext) { v -> change { it.copy(homeRowShowNext = v) } }
                SwitchRow(stringResource(Res.string.live_tv_ui_compact_cards), null, settings.homeRowCompact) { v -> change { it.copy(homeRowCompact = v) } }
            }
            SwitchRow(
                stringResource(Res.string.live_tv_ui_new_episodes_on_home),
                stringResource(Res.string.live_tv_ui_new_episodes_on_home_description),
                settings.calendarNewEpisodeCards,
            ) { v -> change { it.copy(calendarNewEpisodeCards = v) } }
            SwitchRow(
                stringResource(Res.string.live_tv_ui_continue_watching_on_home),
                "Show On Demand titles you started.",
                settings.onDemandHomeRowEnabled,
            ) { v -> change { it.copy(onDemandHomeRowEnabled = v) } }

            }
            SettingsBlock(stringResource(Res.string.live_tv_ui_sync_with_your_nuvio_account), isTablet) {
                AccountSyncSection(live)
            }
            SettingsBlock(stringResource(Res.string.live_tv_ui_sync_with_google_drive), isTablet) {
                DriveSyncSection(live)
            }

            SettingsBlock(stringResource(Res.string.live_tv_ui_parental_controls), isTablet) {
            if (settings.parentalPin.isBlank()) {
                ActionLine(stringResource(Res.string.live_tv_ui_set_a_pin), "Then long-press a group to lock it.") { pinStep = "set" }
            } else {
                ActionLine(stringResource(Res.string.live_tv_ui_change_pin), null) { pinStep = "verify-change" }
                ActionLine(stringResource(Res.string.live_tv_ui_remove_pin), "Locked groups open freely again.") { pinStep = "verify-remove" }
            }

            if (settings.parentalPin.isNotBlank()) {
                ChoiceRow(
                    stringResource(Res.string.live_tv_ui_lock_again),
                    when (settings.relockMinutes) {
                        -1 -> stringResource(Res.string.live_tv_ui_when_i_leave_live_tv)
                        0 -> stringResource(Res.string.live_tv_ui_when_app_closes)
                        60 -> stringResource(Res.string.live_tv_ui_after_1_hour)
                        1 -> stringResource(Res.string.live_tv_ui_after_1_minute)
                        else -> stringResource(Res.string.live_tv_ui_after_n_minutes, settings.relockMinutes)
                    },
                    listOf(stringResource(Res.string.live_tv_ui_when_i_leave_live_tv) to "-1", stringResource(Res.string.live_tv_ui_after_1_minute) to "1", stringResource(Res.string.live_tv_ui_after_5_minutes) to "5", stringResource(Res.string.live_tv_ui_after_15_minutes) to "15", stringResource(Res.string.live_tv_ui_after_1_hour) to "60", stringResource(Res.string.live_tv_ui_when_app_closes) to "0"),
                ) { v -> change { it.copy(relockMinutes = v.toIntOrNull() ?: -1) } }
                SwitchRow(stringResource(Res.string.live_tv_ui_lock_adult_groups_automatically), "Groups with an adult name need the PIN too.", settings.lockAdultContent) { v -> change { it.copy(lockAdultContent = v) } }
            }

            }
            SettingsBlock(stringResource(Res.string.compose_player_reset), isTablet) {
            ActionLine(stringResource(Res.string.live_tv_ui_clear_names_numbers_and_group_order), "Hidden channels and favorites stay as they are.") { confirmReset = true }
            }
        }
    }

    when (pinStep) {
        "set", "change" -> PinDialog(
            title = stringResource(Res.string.live_tv_ui_choose_a_new_pin),
            setting = true,
            onPin = { pin -> change { it.copy(parentalPin = pin) }; pinStep = null },
            onDismiss = { pinStep = null },
        )
        "verify-change" -> PinDialog(
            title = stringResource(Res.string.live_tv_ui_enter_your_current_pin),
            expected = settings.parentalPin,
            onPin = { pinStep = "change" },
            onDismiss = { pinStep = null },
        )
        "verify-remove" -> PinDialog(
            title = stringResource(Res.string.live_tv_ui_enter_your_pin_to_remove_it),
            expected = settings.parentalPin,
            onPin = {
                change { it.copy(parentalPin = "") }
                scope.launch { live.userState.value.lockedGroups.forEach { id -> live.prefs.setGroupLocked(id, false) } }
                pinStep = null
            },
            onDismiss = { pinStep = null },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(Res.string.live_tv_ui_clear_your_edits)) },
            text = { Text(stringResource(Res.string.live_tv_ui_channel_names_channel_numbers_group_names_and_group_order_go)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { live.prefs.resetChannelEdits(); live.prefs.resetGroupEdits() }
                    confirmReset = false
                }) { Text(stringResource(Res.string.settings_custom_poster_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    if (showPlaylists) {
        PlaylistsDialog(
            playlists = playlists,
            epgs = epgs,
            onDismiss = { showPlaylists = false },
            onAdd = { showAddPlaylist = true },
            onAddEpg = { showAddEpg = true },
            onRefresh = sources::refresh,
            onRemove = sources::removePlaylist,
            onEnabled = sources::setPlaylistEnabled,
            onRemoveEpg = sources::removeEpg,
            onEpgEnabled = sources::setEpgEnabled,
            accounts = playlistAccounts,
            onEdit = sources::updatePlaylist,
        )
    }
    if (showPlaylistVisibility) {
        PlaylistVisibilityDialog(
            playlists = playlists,
            hidden = user.hiddenPlaylists,
            onToggle = sources::setPlaylistVisible,
            onDismiss = { showPlaylistVisibility = false },
        )
    }
    if (showAddPlaylist) {
        AddPlaylistDialog(
            onDismiss = { showAddPlaylist = false },
            onM3u = { name, url -> sources.addM3u(name, url); showAddPlaylist = false; showPlaylists = false },
            onXtream = { name, server, user, pass -> sources.addXtream(name, server, user, pass); showAddPlaylist = false; showPlaylists = false },
            onStalker = { name, portal, mac -> sources.addStalker(name, portal, mac); showAddPlaylist = false; showPlaylists = false },
        )
    }
    if (showAddEpg) {
        AddEpgDialog(
            onDismiss = { showAddEpg = false },
            onAdd = { name, url -> sources.addEpg(name, url); showAddEpg = false },
        )
    }
    if (showGroupTags) {
        GroupTagsDialog(
            tags = groupTags,
            selected = user.groupTagFilters,
            scanning = scanningTags,
            channelCount = allChannels.size,
            onScan = {
                scope.launch {
                    scanningTags = true
                    groupTags = withContext(Dispatchers.Default) { LiveTvGroupTags.scan(allChannels) }
                    scanningTags = false
                }
            },
            onToggle = { tag, enabled -> sources.setGroupTagFilter(tag, enabled) },
            onClear = { sources.clearGroupTagFilters() },
            onDismiss = { showGroupTags = false },
        )
    }
    if (editHomeRowTitle) {
        HomeRowTitleDialog(
            initial = settings.homeRowTitle,
            onSave = { title ->
                change { it.copy(homeRowTitle = title) }
                editHomeRowTitle = false
            },
            onDismiss = { editHomeRowTitle = false },
        )
    }
    if (showSmbConfig) {
        SmbConfigDialog(
            initial = smbConfig,
            onSave = { config ->
                SmbSettings.save(context, config)
                smbConfig = config
                if (!settings.recordingSmbEnabled) {
                    change { it.copy(recordingSmbEnabled = true) }
                }
                showSmbConfig = false
            },
            onClear = {
                SmbSettings.clear(context)
                smbConfig = null
                change { it.copy(recordingSmbEnabled = false) }
                showSmbConfig = false
            },
            onDismiss = { showSmbConfig = false },
        )
    }
}

@Composable
private fun SmbConfigDialog(
    initial: SmbConfig?,
    onSave: (SmbConfig) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var host by remember(initial) { mutableStateOf(initial?.host.orEmpty()) }
    var port by remember(initial) { mutableStateOf((initial?.port ?: 445).toString()) }
    var share by remember(initial) { mutableStateOf(initial?.share.orEmpty()) }
    var folder by remember(initial) { mutableStateOf(initial?.folder.orEmpty()) }
    var domain by remember(initial) { mutableStateOf(initial?.domain.orEmpty()) }
    var username by remember(initial) { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember(initial) { mutableStateOf(initial?.password.orEmpty()) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    fun current(): SmbConfig? {
        val hostValue = host.trim()
        val shareValue = share.trim()
        if (hostValue.isBlank() || shareValue.isBlank()) return null
        return SmbConfig(
            host = hostValue,
            port = (port.toIntOrNull() ?: 445).coerceIn(1, 65535),
            share = shareValue,
            folder = folder.trim('/'),
            domain = domain.trim(),
            username = username,
            password = password,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.live_tv_ui_smb_share)) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(host, { host = it }, label = { Text(stringResource(Res.string.live_tv_ui_smb_host)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it }, label = { Text(stringResource(Res.string.live_tv_ui_smb_port)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(share, { share = it }, label = { Text(stringResource(Res.string.live_tv_ui_smb_share_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(folder, { folder = it }, label = { Text(stringResource(Res.string.live_tv_ui_smb_folder)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(domain, { domain = it }, label = { Text(stringResource(Res.string.live_tv_ui_smb_domain)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(username, { username = it }, label = { Text(stringResource(Res.string.live_tv_ui_smb_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text(stringResource(Res.string.live_tv_ui_smb_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                val result = testResult
                if (result != null) {
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { current()?.let(onSave) },
                enabled = !testing && current() != null,
            ) { Text(stringResource(Res.string.live_tv_ui_smb_save)) }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        val config = current() ?: return@TextButton
                        testing = true
                        testResult = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { SmbClient.test(config) }
                            testing = false
                            testResult = if (result.isSuccess) {
                                val free = result.getOrDefault(0L)
                                if (free > 0) {
                                    getString(
                                        Res.string.live_tv_ui_smb_connected_free,
                                        com.nuvio.app.livetv.record.RecordingStorage.sizeLabel(free),
                                    )
                                } else {
                                    getString(Res.string.live_tv_ui_smb_connected)
                                }
                            } else {
                                result.exceptionOrNull()?.message
                                    ?: getString(Res.string.live_tv_ui_smb_connection_failed)
                            }
                        }
                    },
                    enabled = !testing && current() != null,
                ) { Text(if (testing) stringResource(Res.string.live_tv_ui_smb_testing) else stringResource(Res.string.live_tv_ui_smb_test)) }
                if (initial != null) {
                    TextButton(onClick = onClear, enabled = !testing) { Text(stringResource(Res.string.live_tv_ui_smb_clear)) }
                }
                TextButton(onClick = onDismiss, enabled = !testing) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun HomeRowTitleDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.live_tv_ui_row_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.live_tv_title)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value.trim()) }) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun SettingsBlock(title: String, isTablet: Boolean, content: @Composable ColumnScope.() -> Unit) {
    SettingsSection(title = title, isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) { content() }
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
    HorizontalDivider(Modifier.padding(bottom = 4.dp))
}

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@Composable
private fun ActionLine(title: String, detail: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@Composable
private fun StepperRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onMinus) { Text("−") }
            TextButton(onClick = onPlus) { Text("+") }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@Composable
private fun <T> ChoiceRow(title: String, current: String, options: List<Pair<String, T>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ActionLine(title, current) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    options.forEach { (label, value) ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(value); open = false }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** Syncs your Live TV setup through your own Google Drive, shared with the TV app. */
@Composable
private fun AccountSyncSection(live: LiveTv) {
    val sync = remember { live.cloudSync }
    val state by sync.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    if (!state.available) {
        Text(
            stringResource(Res.string.live_tv_ui_setup_sync_works_with_the_official_nuvio_server_it_isn_t_ava),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        return
    }
    if (!state.signedIn) {
        Text(
            stringResource(Res.string.live_tv_ui_sign_in_to_your_nuvio_account_profile_account_to_keep_your_l),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        return
    }
    Text(
        "Signed in as ${state.email ?: "your Nuvio account"}.",
        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
    SwitchRow(
        stringResource(Res.string.live_tv_ui_setup_sync),
        stringResource(Res.string.live_tv_ui_setup_sync_description),
        state.enabled,
    ) { v -> if (v) sync.turnOn() else sync.turnOff() }
    if (state.enabled) {
        Text(
            if (state.lastSyncMs > 0) {
                stringResource(Res.string.live_tv_ui_last_synced, java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT
                ).format(java.util.Date(state.lastSyncMs)))
            } else stringResource(Res.string.live_tv_ui_not_synced_yet),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        ActionLine(stringResource(Res.string.settings_simkl_sync_now), stringResource(Res.string.live_tv_ui_check_changes)) { sync.syncNow() }
        ActionLine(stringResource(Res.string.live_tv_ui_restore_the_previous_copy), stringResource(Res.string.live_tv_ui_bring_back_saved)) { sync.restorePrevious() }
        ActionLine(stringResource(Res.string.live_tv_ui_delete_the_saved_copy), stringResource(Res.string.live_tv_ui_delete_keeps_device)) { confirmDelete = true }
    }
    if (state.busy) Text(stringResource(Res.string.live_tv_ui_working), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    state.message?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(Res.string.live_tv_ui_delete_the_saved_live_tv_setup)) },
            text = { Text(stringResource(Res.string.live_tv_ui_your_nuvio_account_s_saved_copy_is_removed_this_device_keeps)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; sync.deleteCloudCopy() }) { Text(stringResource(Res.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun DriveSyncSection(live: LiveTv) {
    val sync = live.driveSync
    val state by sync.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val codeCopied = stringResource(Res.string.settings_tracking_device_code_copied)
    val googleCodeLabel = stringResource(Res.string.live_tv_ui_google_code)
    val scope = rememberCoroutineScope()
    var askRestore by remember { mutableStateOf<com.nuvio.app.livetv.sync.LiveTvDriveSync.BackupInfo?>(null) }
    var checking by remember { mutableStateOf(false) }

    if (!state.available) {
        Text(
            stringResource(Res.string.live_tv_ui_google_drive_sync_isn_t_set_up_in_this_build_no_google_clien),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        return
    }
    if (!state.connected) {
        ActionLine(
            stringResource(Res.string.live_tv_ui_connect_google_drive),
            stringResource(Res.string.live_tv_ui_keeps_your_groups_favorites_names_and_playlists_in_step_with),
        ) { sync.beginSignIn() }
    } else {
        val connectedBase = state.email?.let {
            stringResource(Res.string.live_tv_ui_connected_as, stringResource(Res.string.settings_debrid_connected), it)
        } ?: stringResource(Res.string.settings_debrid_connected)
        Text(
            if (state.lastSyncMs > 0) stringResource(
                Res.string.live_tv_ui_connected_last_synced_short,
                connectedBase,
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(state.lastSyncMs)),
            ) else stringResource(Res.string.live_tv_ui_connected, connectedBase),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        SwitchRow(stringResource(Res.string.live_tv_ui_sync_automatically), stringResource(Res.string.live_tv_ui_backup_soon), state.autoSync) { sync.setAutoSync(it) }
        ActionLine(stringResource(Res.string.live_tv_ui_back_up_now), stringResource(Res.string.live_tv_ui_save_setup_to_drive)) { sync.backUpNow() }
        ActionLine(stringResource(Res.string.live_tv_ui_restore_from_google_drive), stringResource(Res.string.live_tv_ui_replace_with_saved)) {
            scope.launch {
                checking = true
                val info = sync.backupInfo()
                checking = false
                if (info == null) android.widget.Toast.makeText(context, getString(Res.string.live_tv_ui_no_backup_found_in_google_drive_yet), android.widget.Toast.LENGTH_SHORT).show()
                else askRestore = info
            }
        }
        ActionLine(stringResource(Res.string.live_tv_disconnect), stringResource(Res.string.live_tv_ui_stops_syncing)) { sync.disconnect() }
    }
    if (state.busy || checking) Text(stringResource(Res.string.live_tv_ui_working), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }

    state.signIn?.let { p ->
        // The browser opens at Google's page with the code already in it: choose your account and approve.
        androidx.compose.runtime.LaunchedEffect(p.userCode) {
            // The code goes on the clipboard too, to paste if Google's page asks for it.
            runCatching {
                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText(googleCodeLabel, p.userCode))
            }
            runCatching {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(p.qrUrl)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        AlertDialog(
            onDismissRequest = { sync.cancelSignIn() },
            title = { Text(stringResource(Res.string.live_tv_ui_connect_google_drive)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.live_tv_ui_your_browser_opens_google_s_page_if_it_asks_for_a_code_paste))
                    Text(p.userCode, style = MaterialTheme.typography.headlineMedium)
                    Text(p.url, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(Res.string.live_tv_ui_this_window_closes_by_itself_once_you_ve_approved_it), style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.7f))
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        runCatching {
                            (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                .setPrimaryClip(android.content.ClipData.newPlainText(googleCodeLabel, p.userCode))
                            android.widget.Toast.makeText(context, codeCopied, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(stringResource(Res.string.settings_tracking_device_code_copy)) }
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(p.qrUrl)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }) { Text(stringResource(Res.string.live_tv_ui_open_again)) }
                }
            },
            dismissButton = { TextButton(onClick = { sync.cancelSignIn() }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    askRestore?.let { info ->
        AlertDialog(
            onDismissRequest = { askRestore = null },
            title = { Text(stringResource(Res.string.live_tv_ui_restore_from_google_drive_2)) },
            text = {
                Text(
                    stringResource(
                    Res.string.live_tv_ui_saved_on_device,
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(info.savedAtMs)),
                    if (info.fromThisTv) stringResource(Res.string.live_tv_ui_this_device) else info.device,
                ),
                )
            },
            confirmButton = { TextButton(onClick = { askRestore = null; sync.restoreNow() }) { Text(stringResource(Res.string.live_tv_ui_restore)) } },
            dismissButton = { TextButton(onClick = { askRestore = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun GroupTagsDialog(
    tags: List<LiveTvGroupTags.Tag>,
    selected: Set<String>,
    scanning: Boolean,
    channelCount: Int,
    onScan: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.live_tv_ui_filter_by_group_tag)) },
        text = {
            Column {
                Text(
                    stringResource(Res.string.live_tv_ui_group_tags_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
                TextButton(onClick = onScan, enabled = !scanning && channelCount > 0) {
                    Text(
                        when {
                            scanning -> stringResource(Res.string.live_tv_ui_group_tags_scanning)
                            tags.isEmpty() -> stringResource(Res.string.live_tv_ui_group_tags_scan)
                            else -> stringResource(Res.string.live_tv_ui_group_tags_scan_again)
                        },
                    )
                }
                if (channelCount == 0 && tags.isEmpty()) {
                    Text(
                        stringResource(Res.string.live_tv_ui_group_tags_no_channels),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                } else if (tags.isNotEmpty() && !scanning) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(tags, key = { it.label }) { tag ->
                            val isSelected = selected.any { it.equals(tag.label, ignoreCase = true) }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(tag.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${tag.channelCount} channels · ${tag.groupCount} groups",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalContentColor.current.copy(alpha = 0.7f),
                                    )
                                }
                                Switch(checked = isSelected, onCheckedChange = { onToggle(tag.label, it) })
                            }
                        }
                    }
                } else if (!scanning) {
                    Text(
                        stringResource(Res.string.live_tv_ui_group_tags_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.live_tv_ui_group_tags_done)) }
        },
        dismissButton = {
            TextButton(onClick = onClear, enabled = selected.isNotEmpty()) {
                Text(stringResource(Res.string.live_tv_ui_group_tags_clear))
            }
        },
    )
}

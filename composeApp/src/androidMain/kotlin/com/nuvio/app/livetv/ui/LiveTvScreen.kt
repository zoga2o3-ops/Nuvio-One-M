package com.nuvio.app.livetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nuvio.app.features.livetv.LiveTvPlayRequest
import com.nuvio.app.livetv.model.ChannelGroup
import com.nuvio.app.livetv.model.EpgProgram
import com.nuvio.app.livetv.model.LiveChannel
import com.nuvio.app.livetv.sports.Sport
import com.nuvio.app.livetv.model.PlaylistGroupOrder
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val CardBg = Color(0xFF1B1B1F)
private val Accent = Color(0xFFE53935)

/** The Live TV page: your channels with what's on now, grouped, searchable, ready to play. */
@Composable
fun LiveTvScreen(modifier: Modifier, onPlay: (LiveTvPlayRequest) -> Unit) {
    val context = LocalContext.current.applicationContext
    val vm: LiveTvViewModel = viewModel { LiveTvViewModel(context) }
    LiveTvCloudPrompt()
    val allChannels by vm.channels.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val epgs by vm.epgSources.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val programs by vm.programs.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val shapes by vm.pictureShapes.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()

    var group by remember { mutableStateOf(ChannelGroup.ALL) }
    var restoredGroup by remember { mutableStateOf(false) }
    var actionChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var actionGroup by remember { mutableStateOf<LiveGroup?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var pickGroupFor by remember { mutableStateOf<LiveChannel?>(null) }
    var copyGroupFor by remember { mutableStateOf<LiveChannel?>(null) }
    var guideFor by remember { mutableStateOf<LiveChannel?>(null) }
    var logoFor by remember { mutableStateOf<LiveChannel?>(null) }
    var newGroup by remember { mutableStateOf<String?>(null) }
    var askNewGroup by remember { mutableStateOf(false) }
    // Groups you've opened with the PIN since this page opened.
    var unlockTick by remember { mutableStateOf(0) }
    val unlocked = remember(unlockTick, settings.relockMinutes) { com.nuvio.app.livetv.parental.LiveTvUnlocks.active(settings.relockMinutes) }
    // Groups that were opened with the PIN lock again when their time is up.
    LaunchedEffect(settings.relockMinutes) {
        while (true) { kotlinx.coroutines.delay(15_000); unlockTick++ }
    }
    val relockSetting by androidx.compose.runtime.rememberUpdatedState(settings.relockMinutes)
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (relockSetting == com.nuvio.app.livetv.parental.LiveTvUnlocks.LEAVE) com.nuvio.app.livetv.parental.LiveTvUnlocks.lockAll() }
    }
    var askPinFor by remember { mutableStateOf<String?>(null) }
    var setPinFor by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var showPlaylists by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showGroupPicker by remember { mutableStateOf(false) }
    var sourceFilter by remember { mutableStateOf<String?>(null) }
    var showMultiviewPicker by remember { mutableStateOf(false) }
    var multiviewChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var showAddEpg by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showRecordings by remember { mutableStateOf(false) }
    var showOnDemand by remember { mutableStateOf(false) }
    var showSports by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var sportsDay by rememberSaveable { mutableStateOf(0) }
    var sportsMode by rememberSaveable { mutableStateOf("games") }
    var sportsSport by rememberSaveable { mutableStateOf<Sport?>(null) }
    var recordFor by remember { mutableStateOf<LiveChannel?>(null) }
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val combining by vm.combineProgress.collectAsStateWithLifecycle()
    // Opening the tab pulls in recordings made on other devices from the share.
    LaunchedEffect(showRecordings) { if (showRecordings) vm.refreshRecordings() }
    val seriesRules by vm.seriesRules.collectAsStateWithLifecycle()
    // Messages from recordings (a drive that isn't plugged in, a recording that failed).
    val notice by com.nuvio.app.livetv.record.LiveTvNotice.current.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        notice?.let {
            android.widget.Toast.makeText(context, it.text, android.widget.Toast.LENGTH_LONG).show()
            com.nuvio.app.livetv.record.LiveTvNotice.clear(it)
        }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val notifications = rememberLiveTvNotificationPermission()
    fun askNotifications() = notifications.ask()

    // Locked groups stay out of every list until you've opened them with the PIN.
    // With a PIN set, groups you locked and groups with an adult name (when that's on) need it.
    val isLockedGroup: (String, String) -> Boolean = remember(user.lockedGroups, settings.parentalPin, settings.lockAdultContent) {
        { id, title ->
            id in user.lockedGroups ||
                (settings.parentalPin.length >= 4 && settings.lockAdultContent && com.nuvio.app.livetv.parental.ParentalControls.isAdult(title))
        }
    }
    // Filtering and grouping a big playlist (tens of thousands of channels) is too much for the
    // frame that opens Live TV. It runs on a worker thread and the list appears as soon as it's
    // ready, keeping the previous result while a new one is worked out.
    val index by produceState(
        initialValue = ChannelIndex(),
        allChannels, user, unlocked, isLockedGroup,
    ) {
        value = withContext(Dispatchers.Default) {
            val visible = allChannels.filter {
                it.sourceId !in user.hiddenPlaylists &&
                    it.key !in user.hiddenChannels && it.groupId !in user.hiddenGroups && !user.hidesByGroupTag(it, user.keptChannelKeys) &&
                    (!isLockedGroup(it.groupId, it.group) || it.groupId in unlocked)
            }
            val seen = LinkedHashMap<String, LiveGroup>()
            visible.forEach { c ->
                val title = user.groupNames[c.groupId] ?: c.group
                val existing = seen[c.groupId]
                seen[c.groupId] = if (existing == null) LiveGroup(c.groupId, title, 1) else existing.copy(count = existing.count + 1)
            }
            val groups = seen.values.toList().let { list ->
                // An Xtream provider's own category order first, where it gave one.
                val ranks = PlaylistGroupOrder.ranks
                if (ranks.isEmpty()) list else list.sortedBy { ranks[it.id] ?: Int.MAX_VALUE }
            }
            // Locked groups with no visible channels still need a chip, so they can be opened.
            val lockedChips = allChannels
                .filter { isLockedGroup(it.groupId, it.group) && it.groupId !in unlocked && it.groupId !in user.hiddenGroups }
                .groupBy { it.groupId }
                .map { (id, list) -> LiveGroup(id, user.groupNames[id] ?: list.first().group, list.size) }
            ChannelIndex(
                ready = true,
                channels = visible,
                groups = groups,
                lockedChips = lockedChips,
                byKey = visible.associateBy { it.key },
                byGroup = visible.groupBy { it.groupId },
            )
        }
    }
    val channels = index.channels
    val groups = index.groups
    val chips by produceState(
        initialValue = emptyList<LiveGroup>(),
        index, user.customGroups, user.channelCopies, settings.groupSort, user.groupOrder,
    ) {
        if (!index.ready) return@produceState
        value = withContext(Dispatchers.Default) {
            // Your own groups, then the playlists' groups.
            val ownGroups = user.customGroups.map { g -> LiveGroup(g.id, g.name, g.channelKeys.count { it in index.byKey }) }
            val all = ownGroups + index.groups.map { g ->
                // A channel copied into a group counts there too.
                val extra = user.channelCopies[g.id].orEmpty().count { it in index.byKey }
                if (extra > 0) g.copy(count = g.count + extra) else g
            } + index.lockedChips.filter { l -> index.groups.none { it.id == l.id } }
            when (settings.groupSort) {
                "playlist" -> all
                "name" -> all.sortedBy { it.title.lowercase() }
                else -> all.sortedBy { g -> user.groupOrder.indexOf(g.id).let { if (it < 0) Int.MAX_VALUE else it } }
            }
        }
    }
    // A group that is locked (the PIN was set or removed while on it) is left for All.
    LaunchedEffect(group, isLockedGroup, unlocked, chips) {
        val special = group == ChannelGroup.ALL || group == ChannelGroup.FAVORITES || group == ChannelGroup.RECENT
        if (!special && group !in unlocked && isLockedGroup(group, chips.firstOrNull { it.id == group }?.title.orEmpty())) {
            group = ChannelGroup.ALL
        }
    }
    val favorites = remember(user) { user.favorites.toSet() }
    val shown by produceState(
        initialValue = emptyList<LiveChannel>(),
        index, group, query, favorites, sourceFilter,
        settings.channelSort, settings.sequentialNumbers,
        user.favorites, user.recent, user.customGroups, user.channelCopies, user.channelOrder,
    ) {
        if (!index.ready) return@produceState
        value = withContext(Dispatchers.Default) {
            val base = when (group) {
                ChannelGroup.ALL -> index.channels
                ChannelGroup.FAVORITES -> user.favorites.mapNotNull { k -> index.byKey[k] }
                ChannelGroup.RECENT -> user.recent.mapNotNull { k -> index.byKey[k] }
                else -> if (group.startsWith(ChannelGroup.CUSTOM_PREFIX)) {
                    val keys = user.customGroups.firstOrNull { it.id == group }?.channelKeys.orEmpty()
                    keys.mapNotNull { index.byKey[it] }
                } else {
                    val inGroup = index.byGroup[group].orEmpty() +
                        user.channelCopies[group].orEmpty().mapNotNull { index.byKey[it] }
                            .filter { c -> index.byGroup[group].orEmpty().none { it.key == c.key } }
                    // Your own order for this group wins over the sort.
                    val mine = user.channelOrder[group]
                    if (mine.isNullOrEmpty()) inGroup
                    else inGroup.sortedBy { c -> mine.indexOf(c.key).let { if (it < 0) Int.MAX_VALUE else it } }
                }
            }
            val scoped = if (sourceFilter == null) base else base.filter { it.sourceId == sourceFilter }
            val sorted = when {
                group == ChannelGroup.FAVORITES || group == ChannelGroup.RECENT -> scoped
                group != ChannelGroup.ALL && group.startsWith(ChannelGroup.CUSTOM_PREFIX) -> scoped
                group != ChannelGroup.ALL && !user.channelOrder[group].isNullOrEmpty() -> scoped
                settings.channelSort == com.nuvio.app.livetv.model.ChannelSort.NUMBER ->
                    scoped.sortedWith(compareBy({ if (it.number > 0) it.number else Int.MAX_VALUE }, { it.name.lowercase() }))
                settings.channelSort == com.nuvio.app.livetv.model.ChannelSort.NAME -> scoped.sortedBy { it.name.lowercase() }
                else -> scoped
            }
            val q = query.trim()
            val found = if (q.isEmpty()) sorted else sorted.filter { it.name.contains(q, ignoreCase = true) }
            // stringResource(Res.string.live_tv_ui_number_channels_in_order): 1, 2, 3 down the list shown.
            if (settings.sequentialNumbers) found.mapIndexed { i, c -> c.copy(number = i + 1) } else found
        }
    }

    val heroChannel = remember(shown, user.lastChannelKey) {
        shown.firstOrNull { it.key == user.lastChannelKey } ?: shown.firstOrNull()
    }
    val heroProgram = remember(programs, heroChannel, now) {
        heroChannel?.let { c ->
            programs[c.key]?.firstOrNull { it.isLive(now) } ?: programs[c.key]?.firstOrNull { it.startMs > now }
        }
    }
    val nowNext: @Composable () -> Unit = {
        heroChannel?.let { c ->
            Column {
                NowNextHero(
                    channel = c,
                    program = heroProgram,
                    now = now,
                    use24Hour = settings.use24HourClock,
                    favorite = c.key in favorites,
                    onWatch = { vm.recordWatched(c.key); playLive(onPlay, c.toRequest(programs[c.key], now)) },
                    onFavorite = { vm.toggleFavorite(c.key) },
                    onMultiview = { showMultiviewPicker = true },
                    onGuide = {
                        showGuide = true
                        showRecordings = false
                        showOnDemand = false
                        showSports = false
                        showCalendar = false
                    },
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Channels", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text("${shown.size}", style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.6f))
                    Spacer(Modifier.weight(1f))
                    GroupChip(stringResource(Res.string.live_tv_ui_groups) + " \u25BE", false) { showGroupPicker = true }
                    if (user.hiddenChannels.isNotEmpty() || user.hiddenGroups.isNotEmpty()) {
                        GroupChip(stringResource(Res.string.settings_homescreen_hidden), false) { showHidden = true }
                    }
                    GroupChip(if (settings.channelGrid) "\u2630" else "\u25A6", false) {
                        vm.updateSettings { it.copy(channelGrid = !it.channelGrid) }
                    }
                    GroupChip("+", false) { newGroup = null; askNewGroup = true }
                }
            }
        }
    }

    // stringResource(Res.string.live_tv_ui_play_the_last_channel): when Live TV first opens in this run of the app, it starts the one you watched last.
    LaunchedEffect(user.lastChannelKey, allChannels.size, settings.autoPlayLastChannel) {
        val key = user.lastChannelKey
        if (settings.autoPlayLastChannel && !LiveTvStart.autoPlayedThisRun && key != null && allChannels.isNotEmpty()) {
            allChannels.firstOrNull { it.key == key }?.let { c ->
                LiveTvStart.autoPlayedThisRun = true
                playLive(onPlay, c.toRequest(programs[c.key], now))
            }
        }
    }

    // A playlist has its own groups: start at All when the playlist changes.
    LaunchedEffect(sourceFilter) {
        if (sourceFilter != null) group = ChannelGroup.ALL
    }

    // Open on the group you were last in.
    LaunchedEffect(user.lastGroupId, settings.rememberLastGroup, chips) {
        val last = user.lastGroupId
        if (!restoredGroup && last != null && settings.rememberLastGroup && chips.isNotEmpty()) {
            restoredGroup = true
            if (last in setOf(ChannelGroup.ALL, ChannelGroup.FAVORITES, ChannelGroup.RECENT) || chips.any { it.id == last }) group = last
        }
    }

    LaunchedEffect(showGuide, shown) {
        if (showGuide) vm.loadGuide(shown.map { it.key })
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        BoxWithConstraints(modifier.fillMaxSize().statusBarsPadding()) {
            // A phone held sideways is wide but short, so it's a phone, not a tablet: keep the
            // compact list and don't reserve the tablet rail space that isn't there.
            val tablet = maxWidth >= 768.dp && maxHeight >= 600.dp
            val compact = maxWidth < 600.dp
            val navBarStyle by com.nuvio.app.features.settings.ThemeSettingsRepository.navBarStyle.collectAsStateWithLifecycle()
            // The tablet top rail only exists with the CLASSIC nav; every other style uses a bottom
            // pill, so only CLASSIC needs the space at the top kept clear.
            val topRail = maxWidth >= 768.dp && navBarStyle == com.nuvio.app.features.settings.NavBarStyle.CLASSIC
            Column(Modifier.fillMaxSize().padding(top = if (topRail) 72.dp else 0.dp)) {
                val lastChannel = remember(allChannels, user.lastChannelKey) { allChannels.firstOrNull { it.key == user.lastChannelKey } }
                val mainTabs: @Composable () -> Unit = {
                    if (playlists.isNotEmpty()) {
                        Row(
                            Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            IconTab(Icons.Default.LiveTv, stringResource(Res.string.live_tv_section_channels), !showGuide && !showRecordings && !showOnDemand && !showSports && !showCalendar) { showGuide = false; showRecordings = false; showOnDemand = false; showSports = false; showCalendar = false }
                            IconTab(Icons.AutoMirrored.Filled.List, stringResource(Res.string.live_tv_ui_guide), showGuide) { showGuide = true; showRecordings = false; showOnDemand = false; showSports = false; showCalendar = false }
                            IconTab(Icons.Default.CalendarMonth, stringResource(Res.string.library_calendar_title), showCalendar) { showCalendar = true; showGuide = false; showRecordings = false; showOnDemand = false; showSports = false }
                            IconTab(Icons.Default.SportsSoccer, stringResource(Res.string.yt_tab_sports), showSports) { showSports = true; showGuide = false; showRecordings = false; showOnDemand = false; showCalendar = false }
                            IconTab(Icons.Default.PlayCircleOutline, stringResource(Res.string.live_tv_ui_on_demand), showOnDemand) { showOnDemand = true; showGuide = false; showRecordings = false; showSports = false; showCalendar = false }
                            IconTab(
                                Icons.Default.FiberManualRecord,
                                if (recordings.any { it.isRecording }) stringResource(Res.string.live_tv_ui_u25cf_recordings) else stringResource(Res.string.live_tv_ui_recordings),
                                showRecordings,
                            ) { showRecordings = true; showGuide = false; showOnDemand = false; showSports = false; showCalendar = false }
                        }
                    }
                }
                Header(
                    tabs = mainTabs,
                    query = query,
                    onQuery = { query = it },
                    onPlaylists = { showPlaylistPicker = true },
                    playlistName = sourceFilter?.let { id -> playlists.firstOrNull { it.id == id }?.name },
                    centre = if (showSports && sportsMode == "games") {
                        { SportsDaySwitcher(day = sportsDay, onDay = { sportsDay = it }, repo = vm.sports) }
                    } else null,
                )
                if (playlists.isEmpty()) {
                    EmptyState(onAdd = { showAdd = true })
                } else {
                    StatusLine(status)
                    if (showCalendar) {
                        CalendarPage(calendar = vm.calendar, use24Hour = settings.use24HourClock, now = now, onPlay = { r -> onPlay(r) })
                    } else if (showSports) {
                        SportsPage(
                            repo = vm.sports,
                            hasChannels = allChannels.isNotEmpty(),
                            use24Hour = settings.use24HourClock,
                            day = sportsDay,
                            mode = sportsMode,
                            onMode = { sportsMode = it },
                            sport = sportsSport,
                            onSport = { sportsSport = it },
                            onWatch = { c -> vm.recordWatched(c.key); playLive(onPlay, c.toRequest(programs[c.key], now)) },
                            onRecord = { ev, m ->
                                android.widget.Toast.makeText(context, vm.recordSportsEvent(ev, m.channel), android.widget.Toast.LENGTH_LONG).show()
                            },
                        )
                    } else if (showOnDemand) {
                        OnDemandPage(
                            repo = vm.onDemand,
                            relockMinutes = settings.relockMinutes,
                            groupOrder = user.vodGroupOrder,
                            groupNames = user.vodGroupNames,
                            onSaveGroups = { hidden, order, names -> vm.saveVodGroups(hidden, order, names) },
                            hiddenCategories = user.vodHiddenCategories,
                            onHideCategory = { vm.hideVodCategory(it.uid) },
                            onShowAllCategories = vm::showAllVodCategories,
                            onPlay = onPlay,
                            sorts = user.vodSorts,
                            defaultSort = settings.vodDefaultSort,
                            onSortPicked = vm::setVodSort,
                            lockedCategories = user.lockedGroups.filter { it.startsWith("vod:") }.map { it.removePrefix("vod:") }.toSet(),
                            pin = settings.parentalPin,
                            onLockCategory = vm::lockVodCategory,
                            onSetPin = vm::setPin,
                        )
                    } else if (showRecordings) {
                        RecordingsList(
                            recordings = recordings,
                            schedules = schedules,
                            freeBytes = vm.freeBytes(),
                            onPlay = { r -> onPlay(vm.playRequest(r)) },
                            onStop = { vm.stopRecording(it.id) },
                            onDelete = { vm.deleteRecording(it.id) },
                            onCancelSchedule = { vm.cancelSchedule(it.id) },
                            shareReady = vm.smbConfigured(),
                            combining = combining,
                            onCombine = { r, toShare -> vm.combineRecording(r.id, toShare) },
                            series = seriesRules,
                            onRemoveSeries = { vm.removeSeries(it.id) },
                            onKeepLast = { r, n -> vm.setKeepLast(r.id, n) },
                            resumeOf = vm::resumeOf,
                            watchedOf = vm::isRecordingWatched,
                        )
                    } else if (shown.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (!index.ready || (status.loading && channels.isEmpty())) CircularProgressIndicator()
                            else Text(
                                if (query.isNotBlank()) stringResource(Res.string.live_tv_ui_no_channels_match) else stringResource(Res.string.live_tv_ui_no_channels_yet_2),
                                color = LocalContentColor.current.copy(alpha = 0.65f),
                            )
                        }
                    } else if (showGuide) {
                        if (programs.isEmpty() && !status.loading) {
                            Text(
                                stringResource(Res.string.live_tv_ui_no_guide_yet_open_sources_and_add_a_guide_link_or_use_a_play),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalContentColor.current.copy(alpha = 0.65f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        LiveTvGuideGrid(
                            channels = shown,
                            programs = programs,
                            now = now,
                            tablet = tablet,
                            wide = !compact,
                            pastHours = settings.epgPastHours,
                            onArchiveNeeded = { vm.loadCatchupArchive(it) },
                            reminders = user.reminders,
                            onRemind = { c, p, on -> askNotifications(); vm.setReminder(c, p, on) },
                            isRecordSet = { c, p -> vm.isScheduled(c.key, p) },
                            onRecordSeries = { c, p ->
                                android.widget.Toast.makeText(context, vm.addSeries(c, p), android.widget.Toast.LENGTH_LONG).show()
                            },
                            onRecord = { c, p, on ->
                                if (on) android.widget.Toast.makeText(context, vm.scheduleProgram(c, p), android.widget.Toast.LENGTH_LONG).show()
                                else vm.cancelProgram(c.key, p.startMs)
                            },
                            onPlay = { c -> vm.recordWatched(c.key); playLive(onPlay, c.toRequest(programs[c.key], now)) },
                            onCatchup = { c, p ->
                                scope.launch {
                                    val request = vm.catchupRequest(c, p)
                                    if (request != null) { com.nuvio.app.livetv.player.LiveTvReconnect.arm(request.url, request.channelKey); onPlay(request) }
                                    else android.widget.Toast.makeText(context, getString(Res.string.live_tv_ui_that_programme_can_t_be_replayed), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    } else if (settings.channelGrid) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = if (tablet) 150.dp else 112.dp),
                            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item(key = "now-next", span = { GridItemSpan(maxLineSpan) }) { nowNext() }
                            gridItems(shown, key = { it.key }) { c ->
                                ChannelTile(c, programs[c.key], now, c.key in favorites, { actionChannel = c }, settings, user.channelQuality[c.key]?.takeIf { settings.showQualityBadges }) {
                                    vm.recordWatched(c.key); playLive(onPlay, c.toRequest(programs[c.key], now))
                                }
                            }
                        }
                    } else if (tablet) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 340.dp),
                            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item(key = "now-next", span = { GridItemSpan(maxLineSpan) }) { nowNext() }
                            gridItems(shown, key = { it.key }) { c ->
                                ChannelRow(c, programs[c.key], now, c.key in favorites, vm::toggleFavorite, { actionChannel = c }, settings, user.channelQuality[c.key]?.takeIf { settings.showQualityBadges }) {
                                    vm.recordWatched(c.key); playLive(onPlay, c.toRequest(programs[c.key], now))
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item(key = "now-next") { nowNext() }
                            items(shown, key = { it.key }) { c ->
                                ChannelRow(c, programs[c.key], now, c.key in favorites, vm::toggleFavorite, { actionChannel = c }, settings, user.channelQuality[c.key]?.takeIf { settings.showQualityBadges }) {
                                    vm.recordWatched(c.key); playLive(onPlay, c.toRequest(programs[c.key], now))
                                }
                            }
                        }
                    }
                }
            }
            if (playlists.isNotEmpty() && multiviewChannels.isEmpty() &&
                !showGuide && !showRecordings && !showOnDemand && !showSports && !showCalendar
            ) {
                SmallFloatingActionButton(
                    onClick = { showMultiviewPicker = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 96.dp),
                    containerColor = Color(0xFFE53935),
                ) {
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = stringResource(Res.string.live_tv_ui_multiview),
                        tint = Color.White,
                    )
                }
            }
        }
    }

    if (showMultiviewPicker) {
        LiveTvMultiviewPicker(
            channels = shown,
            onStart = { picked ->
                showMultiviewPicker = false
                multiviewChannels = picked
            },
            onDismiss = { showMultiviewPicker = false },
        )
    }
    if (multiviewChannels.isNotEmpty()) {
        LiveTvMultiviewScreen(
            channels = multiviewChannels,
            backupFor = { failed ->
                allChannels.firstOrNull { candidate ->
                    candidate.key != failed.key && candidate.groupId == failed.groupId &&
                        com.nuvio.app.livetv.data.LiveTvRepository.normalize(candidate.name) ==
                        com.nuvio.app.livetv.data.LiveTvRepository.normalize(failed.name)
                }
            },
            onClose = { multiviewChannels = emptyList() },
        )
    }

    actionChannel?.let { c ->
        ChannelActionsDialog(
            channel = c,
            favorite = c.key in favorites,
            shape = com.nuvio.app.livetv.player.PictureShape.of(shapes[c.key]),
            onFavorite = { vm.toggleFavorite(c.key) },
            onRename = { vm.renameChannel(c.key, it) },
            onNumber = { vm.setChannelNumber(c.key, it) },
            onHide = { vm.setChannelHidden(c.key, true) },
            onShape = { vm.setPictureShape(c.key, it) },
            onRemind = programs[c.key]?.firstOrNull { it.startMs > now }?.let { next ->
                { askNotifications(); vm.setReminder(c, next, true) }
            },
            onRecord = { recordFor = c },
            onMove = if (group != ChannelGroup.ALL && group != ChannelGroup.FAVORITES && group != ChannelGroup.RECENT && query.isBlank()) { delta ->
                vm.moveChannel(group, c.key, delta, shown.map { it.key })
            } else null,
            onAddToGroup = { pickGroupFor = c },
            onCopyToGroup = { copyGroupFor = c },
            onRemoveFromGroup = if (group.startsWith(ChannelGroup.CUSTOM_PREFIX) || c.key in user.channelCopies[group].orEmpty()) {
                { vm.removeFromGroup(group, c.key) }
            } else null,
            onGuide = { guideFor = c },
            onLogo = { logoFor = c },
            onDismiss = { actionChannel = null },
        )
    }
    logoFor?.let { c ->
        ChangeLogoDialog(
            channel = c,
            hasPicked = c.key in user.channelLogos,
            onPick = { url -> vm.setChannelLogo(c.key, url); logoFor = null },
            onDismiss = { logoFor = null },
        )
    }
    pickGroupFor?.let { c ->
        PickDialog(
            title = stringResource(Res.string.live_tv_ui_add_channel_to_group, c.name),
            options = user.customGroups.map { it.id to it.name },
            extra = stringResource(Res.string.live_tv_ui_new_group_u2026) to { newGroup = c.key; askNewGroup = true; pickGroupFor = null },
            onPick = { id -> vm.addToGroup(id, c.key); pickGroupFor = null },
            onDismiss = { pickGroupFor = null },
        )
    }
    copyGroupFor?.let { c ->
        PickDialog(
            title = stringResource(Res.string.live_tv_ui_copy_channel_to, c.name),
            options = groups.filter { it.id != c.groupId }.map { it.id to it.title },
            onPick = { id -> vm.copyToGroup(id, c.key); copyGroupFor = null },
            onDismiss = { copyGroupFor = null },
        )
    }
    guideFor?.let { c ->
        val sources by vm.epgChannels.collectAsStateWithLifecycle()
        GuidePickerDialog(
            channelName = c.name,
            sources = sources,
            onPick = { a -> vm.assignGuide(c.key, a); guideFor = null },
            onDismiss = { guideFor = null },
        )
    }
    if (askNewGroup) {
        TextPromptDialog(
            title = stringResource(Res.string.live_tv_ui_new_group),
            initial = "",
            onOk = { name -> if (name.isNotBlank()) vm.createGroup(name, newGroup); newGroup = null; askNewGroup = false },
            onDismiss = { newGroup = null; askNewGroup = false },
        )
    }
    actionGroup?.let { g ->
        GroupActionsDialog(
            title = g.title,
            locked = g.id in user.lockedGroups,
            onRename = { vm.renameGroup(g.id, it) },
            onHide = { vm.setGroupHidden(g.id, true); if (group == g.id) group = ChannelGroup.ALL },
            onLock = {
                if (g.id in user.lockedGroups) vm.setGroupLocked(g.id, false)
                else if (settings.parentalPin.isBlank()) setPinFor = g.id
                else { vm.setGroupLocked(g.id, true); if (group == g.id) group = ChannelGroup.ALL }
            },
            onMove = { delta -> vm.moveGroup(g.id, delta, chips.map { it.id }) },
            onDelete = if (g.id.startsWith(ChannelGroup.CUSTOM_PREFIX)) ({ vm.deleteGroup(g.id); if (group == g.id) group = ChannelGroup.ALL }) else null,
            onDismiss = { actionGroup = null },
        )
    }
    recordFor?.let { c ->
        val current = programs[c.key]?.firstOrNull { it.isLive(now) }
        RecordChannelDialog(
            channel = c,
            currentShow = current?.title,
            minutesLeftInShow = current?.let { ((it.stopMs - now) / 60_000L).toInt() },
            onRecordNow = { minutes, title ->
                vm.recordNow(c, title, minutes)
                android.widget.Toast.makeText(context, kotlinx.coroutines.runBlocking { getString(Res.string.live_tv_ui_recording_named, title) }, android.widget.Toast.LENGTH_SHORT).show()
            },
            onSchedule = { title, start, stop ->
                android.widget.Toast.makeText(context, vm.scheduleManual(c, title, start, stop), android.widget.Toast.LENGTH_LONG).show()
            },
            onDismiss = { recordFor = null },
        )
    }
    askPinFor?.let { id ->
        PinDialog(
            title = stringResource(Res.string.live_tv_ui_enter_your_pin),
            expected = settings.parentalPin,
            onPin = { com.nuvio.app.livetv.parental.LiveTvUnlocks.unlock(id); unlockTick++; group = id; vm.setLastGroup(id); askPinFor = null },
            onDismiss = { askPinFor = null },
        )
    }
    setPinFor?.let { id ->
        PinDialog(
            title = stringResource(Res.string.live_tv_ui_set_a_pin),
            setting = true,
            onPin = { pin -> vm.setPin(pin); vm.setGroupLocked(id, true); if (group == id) group = ChannelGroup.ALL; setPinFor = null },
            onDismiss = { setPinFor = null },
        )
    }
    if (showHidden) {
        HiddenDialog(
            groups = user.hiddenGroups.map { id -> id to (user.groupNames[id] ?: allChannels.firstOrNull { it.groupId == id }?.group ?: id) },
            channels = allChannels.filter { it.key in user.hiddenChannels },
            onShowGroup = { vm.setGroupHidden(it, false) },
            onShowChannel = { vm.setChannelHidden(it, false) },
            onShowAll = { vm.showAllHidden(); showHidden = false },
            onDismiss = { showHidden = false },
        )
    }

    if (showGroupPicker) {
        val allLabel = stringResource(Res.string.collections_tab_all)
        val favoritesLabel = stringResource(Res.string.live_tv_filter_favorites)
        val recentLabel = stringResource(Res.string.collections_editor_tmdb_sort_recent)
        val choices = mutableListOf(GroupChoice(ChannelGroup.ALL, allLabel, index.channels.size))
        if (settings.showFavoritesGroup && favorites.isNotEmpty()) {
            choices += GroupChoice(ChannelGroup.FAVORITES, favoritesLabel, favorites.size)
        }
        if (settings.showRecentGroup && user.recent.isNotEmpty()) {
            choices += GroupChoice(ChannelGroup.RECENT, recentLabel, user.recent.size)
        }
        chips.forEach { g ->
            val locked = isLockedGroup(g.id, g.title) && g.id !in unlocked
            choices += GroupChoice(g.id, g.title, g.count, locked)
        }
        GroupPickerDialog(
            choices = choices,
            selected = group,
            onSelect = { id ->
                val g = chips.firstOrNull { it.id == id }
                if (g != null && isLockedGroup(id, g.title) && id !in unlocked) {
                    askPinFor = id
                } else {
                    group = id
                    vm.setLastGroup(id)
                }
                showGroupPicker = false
            },
            onHidden = if (user.hiddenChannels.isNotEmpty() || user.hiddenGroups.isNotEmpty()) {
                { showGroupPicker = false; showHidden = true }
            } else null,
            onDismiss = { showGroupPicker = false },
        )
    }
    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            playlists = playlists,
            channelCounts = remember(allChannels) { allChannels.groupingBy { it.sourceId }.eachCount() },
            selected = sourceFilter,
            onSelect = { id ->
                sourceFilter = id
                showPlaylistPicker = false
            },
            onManage = { showPlaylistPicker = false; showPlaylists = true },
            onDismiss = { showPlaylistPicker = false },
        )
    }
    if (showPlaylists) {
        PlaylistsDialog(
            playlists = playlists,
            epgs = epgs,
            onDismiss = { showPlaylists = false },
            onAdd = { showAdd = true },
            onAddEpg = { showAddEpg = true },
            onRefresh = vm::refresh,
            onRemove = vm::removePlaylist,
            onEnabled = vm::setPlaylistEnabled,
            onRemoveEpg = vm::removeEpg,
            onEpgEnabled = vm::setEpgEnabled,
            accounts = accounts,
            onEdit = vm::updatePlaylist,
        )
    }
    if (showAddEpg) {
        AddEpgDialog(
            onDismiss = { showAddEpg = false },
            onAdd = { name, url -> vm.addEpg(name, url); showAddEpg = false },
        )
    }
    if (showAdd) {
        AddPlaylistDialog(
            onDismiss = { showAdd = false },
            onM3u = { name, url -> vm.addM3u(name, url); showAdd = false; showPlaylists = false },
            onXtream = { name, server, user, pass -> vm.addXtream(name, server, user, pass); showAdd = false; showPlaylists = false },
            onStalker = { name, portal, mac -> vm.addStalker(name, portal, mac); showAdd = false; showPlaylists = false },
        )
    }
}

/** The channel list's filtered, grouped and indexed form, worked out away from the UI thread. */
private data class ChannelIndex(
    val ready: Boolean = false,
    val channels: List<LiveChannel> = emptyList(),
    val groups: List<LiveGroup> = emptyList(),
    val lockedChips: List<LiveGroup> = emptyList(),
    val byKey: Map<String, LiveChannel> = emptyMap(),
    val byGroup: Map<String, List<LiveChannel>> = emptyMap(),
)

private fun LiveChannel.toRequest(programs: List<EpgProgram>?, now: Long) = liveTvRequest(this, programs, now)

@Composable
private fun Header(
    tabs: @Composable () -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    onPlaylists: () -> Unit,
    playlistName: String? = null,
    centre: (@Composable () -> Unit)? = null,
) {
    var searchOpen by remember { mutableStateOf(false) }
    val search: @Composable (Modifier) -> Unit = { m ->
        OutlinedTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            modifier = m, placeholder = { Text(stringResource(Res.string.live_tv_search_placeholder)) },
            shape = RoundedCornerShape(12.dp), colors = fieldColors())
    }
    Column(Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp)) {
        val compact = LocalConfiguration.current.screenWidthDp < 600
        Box(Modifier.fillMaxWidth()) {
            if (compact && centre != null) {
                // Phone with the day switcher: title, the day in the middle, then search and playlists.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        Text("Live TV", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    }
                    centre()
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(
                            onClick = {
                                searchOpen = !searchOpen
                                if (!searchOpen) onQuery("")
                            },
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = if (searchOpen) stringResource(Res.string.live_tv_ui_hide_search) else stringResource(Res.string.live_tv_search_placeholder))
                        }
                        TextButton(
                            onClick = onPlaylists,
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Text(
                                playlistName ?: stringResource(Res.string.live_tv_ui_playlists),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Live TV", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) onQuery("")
                        },
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(Icons.Default.Search, contentDescription = if (searchOpen) stringResource(Res.string.live_tv_ui_hide_search) else stringResource(Res.string.live_tv_search_placeholder))
                    }
                    TextButton(onClick = onPlaylists, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(playlistName ?: stringResource(Res.string.live_tv_ui_playlists), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge) }
                }
            }
            // On a phone the icons get their own centred row; a wide screen has room in the top row.
            if (!compact) Box(Modifier.align(Alignment.Center)) { tabs() }
        }
        if (compact) {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) { tabs() }
        }
        if (!compact && centre != null) {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) { centre() }
        }
        if (searchOpen) search(Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GroupChip(label: String, selected: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

/** The page tabs (Channels, Guide, Calendar...): icon-only, red when it's the page you're on. */
@Composable
private fun IconTab(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    // Landscape and tablet have room for wider buttons; a phone stays compact.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Accent else Color.Transparent)
            .border(1.dp, if (selected) Accent else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (wide) 14.dp else 16.dp, vertical = if (wide) 8.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Color.White else Accent,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun StatusLine(status: com.nuvio.app.livetv.data.LiveTvStatus) {
    val text = status.error ?: status.message.takeIf { status.loading && !status.quiet } ?: return
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (status.loading && status.error == null) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        if (status.loading && status.error == null) Spacer(Modifier.width(8.dp))
        Text(
            text, style = MaterialTheme.typography.bodySmall,
            color = if (status.error != null) Accent else LocalContentColor.current.copy(alpha = 0.7f),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.live_tv_ui_no_channels_yet), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(Res.string.live_tv_ui_add_a_playlist_link_m3u_or_an_xtream_login_to_get_your_chann),
                color = LocalContentColor.current.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAdd) { Text(stringResource(Res.string.live_tv_ui_add_a_playlist)) }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: LiveChannel,
    programs: List<EpgProgram>?,
    now: Long,
    favorite: Boolean,
    onFavorite: (String) -> Unit,
    onLongClick: () -> Unit,
    settings: com.nuvio.app.livetv.model.LiveTvSettings,
    quality: String? = null,
    onClick: () -> Unit,
) {
    val current = programs?.firstOrNull { it.isLive(now) }
    val next = current?.let { c -> programs.firstOrNull { it.startMs >= c.stopMs } }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(if (settings.compactRows) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (settings.showChannelLogos) Box(
            Modifier.size(width = if (settings.compactRows) 56.dp else 72.dp, height = if (settings.compactRows) 42.dp else 54.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF26262B)),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                )
            } else {
                Text(channel.name.take(2).uppercase(), style = MaterialTheme.typography.titleMedium, color = Color(0xFFB0B0B0))
            }
        }
        if (settings.showChannelLogos) Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (channel.number > 0 && settings.showChannelNumbers) "${channel.number}  " else "") + channel.name,
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (quality != null) QualityBadge(quality)
            }
            if (current != null) {
                Text(
                    current.title, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = LocalContentColor.current.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(current.progress(now)).height(3.dp).background(Accent))
                }
                next?.let {
                    Text(
                        "Next: ${it.title}", style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = LocalContentColor.current.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                Text(channel.group, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.55f), maxLines = 1)
            }
        }
        IconButton(onClick = { onFavorite(channel.key) }) {
            Icon(
                if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (favorite) stringResource(Res.string.live_tv_ui_remove_from_favorites) else stringResource(Res.string.live_tv_ui_add_to_favorites),
                tint = if (favorite) Color(0xFFFFC107) else LocalContentColor.current.copy(alpha = 0.6f),
            )
        }
    }
}

/** Opens a channel, keeping it going if the stream drops. */
private fun playLive(onPlay: (LiveTvPlayRequest) -> Unit, request: LiveTvPlayRequest) {
    com.nuvio.app.livetv.player.LiveTvReconnect.arm(request.url, request.channelKey)
    onPlay(request)
}

/** "4K", stringResource(Res.string.live_tv_ui_fhd), "HD" or "SD", small, next to a channel's name. */
@Composable
internal fun QualityBadge(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = LocalContentColor.current.copy(alpha = 0.75f),
        modifier = Modifier.padding(start = 8.dp)
            .border(1.dp, LocalContentColor.current.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** A channel as a tile for the grid view: its logo, name, and what's on now. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChannelTile(
    channel: LiveChannel,
    programs: List<EpgProgram>?,
    now: Long,
    favorite: Boolean,
    onLongClick: () -> Unit,
    settings: com.nuvio.app.livetv.model.LiveTvSettings,
    quality: String? = null,
    onClick: () -> Unit,
) {
    val current = programs?.firstOrNull { it.isLive(now) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF26262B)),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
            }
            if (favorite) Text("\u2605", Modifier.align(Alignment.TopEnd).padding(4.dp), color = Color(0xFFFFC107))
            if (quality != null) Box(Modifier.align(Alignment.BottomStart).padding(4.dp)) { QualityBadge(quality) }
        }
        Text(
            (if (settings.showChannelNumbers) "${channel.number}  " else "") + channel.name,
            Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
        )
        Text(
            current?.title ?: channel.group,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.6f),
        )
    }
}

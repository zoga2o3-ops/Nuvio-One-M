package com.nuvio.app.livetv.sync

import android.content.Context
import android.os.Build
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.network.ServerConfigurationRepository
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.livetv.data.LiveTvPreferences
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/** What the settings screen shows for Setup Sync. */
data class CloudSyncState(
    /** Only on the official Nuvio server: that is the sign-in the sync service checks. */
    val available: Boolean = false,
    val signedIn: Boolean = false,
    val email: String? = null,
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val lastSyncMs: Long = 0L,
    /** A first-time question waiting for the person. */
    val prompt: CloudPrompt? = null
)

enum class CloudPrompt {
    /** This device has a setup and the account has none yet. */
    BACK_UP,
    /** The account has a saved setup and this device has a different one. */
    CHOOSE
}

enum class CloudChoice { USE_SAVED, COMBINE, LATER }

/**
 * Keeps the Live TV setup the same on every device signed in to the same Nuvio account.
 *
 * It uses the Nuvio sign-in the app already has: the access token is sent to our own small
 * service (the livetv-cloud Worker), which asks Nuvio whose token it is and keeps one saved copy
 * per account. Nothing is written to Nuvio's servers, and the token is never stored here.
 *
 * What is synced is exactly what the Google Drive backup syncs (LiveTvPreferences.exportForSync),
 * combined the same way (LiveTvDriveSync.merge). A device never overwrites a copy it hasn't seen:
 * the service refuses a save made from an out-of-date copy, and the next round combines.
 */
class LiveTvCloudSync(
    context: Context,
    private val prefs: LiveTvPreferences,
    private val drive: LiveTvDriveSync
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = appContext.getSharedPreferences("livetv_cloud_sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    private val _state = MutableStateFlow(
        CloudSyncState(available = serverAvailable(), lastSyncMs = store.getLong(KEY_LAST_SYNC, 0L))
    )
    val state: StateFlow<CloudSyncState> = _state.asStateFlow()

    @Volatile private var started = false
    @Volatile private var userId: String? = null
    /** True while a saved setup is being written here, so it isn't saved straight back. */
    @Volatile private var applying = false

    private val deviceId: String
        get() = store.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            store.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    // ------------------------------------------------------------------ start

    /** Starts watching the Nuvio sign-in. Called once when the app opens. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch { AuthRepository.state.collect { onAuthState(it) } }
        // This device's Live TV settings changed: sync shortly after (not for per-device things
        // like the last channel watched, which aren't part of the setup).
        scope.launch {
            prefs.changes.drop(1).debounce(8_000).collect {
                if (!applying && isOn()) syncIfChanged()
            }
        }
        scope.launch {
            while (isActive) {
                delay(60 * 60_000L)
                if (isOn()) syncNow()
            }
        }
    }

    private suspend fun onAuthState(authState: AuthState) {
        val available = serverAvailable()
        when (authState) {
            is AuthState.Authenticated -> {
                if (authState.isAnonymous) {
                    userId = null
                    _state.value = _state.value.copy(
                        available = available, signedIn = false, email = null, enabled = false
                    )
                    return
                }
                userId = authState.userId
                val on = isEnabled(authState.userId)
                _state.value = _state.value.copy(
                    available = available, signedIn = true, email = authState.email, enabled = on
                )
                if (!available) return
                mutex.withLock {
                    if (on) reconcileAndReport(authState.userId) else decideFirstTime(authState.userId)
                }
            }
            AuthState.Unauthenticated -> {
                userId = null
                // This device keeps its setup; it just stops syncing until someone signs in again.
                _state.value = _state.value.copy(
                    available = available, signedIn = false, email = null, enabled = false, message = null
                )
            }
            AuthState.Loading -> Unit
        }
    }

    // ------------------------------------------------------------------ what the settings screen does

    fun syncNow() {
        scope.launch {
            mutex.withLock {
                val uid = userId ?: return@withLock
                if (isEnabled(uid)) reconcileAndReport(uid, doneMessage = "Up to date.")
            }
        }
    }

    /** "Turn on" in settings. */
    fun turnOn() {
        scope.launch {
            mutex.withLock {
                val uid = userId ?: return@withLock
                if (!serverAvailable()) return@withLock
                store.edit().putBoolean(KEY_DECLINED + uid, false).apply()
                _state.value = _state.value.copy(busy = true, message = null, prompt = null)
                val message = runCatching {
                    val local = prefs.exportForSync()
                    val hasLocal = prefs.syncHasSources(local)
                    val remote = fetch()
                    when {
                        remote is Remote.Found && usable(remote.data) -> {
                            val merged = drive.merge(
                                readBase(uid), local, remote.data, localWinsWithoutBase = true,
                                removedHere = prefs.removedSourceIds()
                            )
                            if (merged.toString() != local.toString()) apply(merged)
                            if (merged.toString() != remote.data.toString()) {
                                when (val result = put(remote.revision, merged)) {
                                    is Put.Saved -> Unit
                                    else -> return@runCatching putText(result)
                                }
                            }
                            saved(uid, merged)
                            setEnabled(uid, true)
                            "Setup Sync is on. Combined with your saved setup."
                        }
                        hasLocal -> when (val result = put(0, local)) {
                            is Put.Saved -> {
                                saved(uid, local)
                                setEnabled(uid, true)
                                "Your Live TV setup is saved to your Nuvio account."
                            }
                            else -> putText(result)
                        }
                        else -> {
                            setEnabled(uid, true)
                            "Setup Sync is on. It will save your setup once you add a playlist."
                        }
                    }
                }.getOrElse { NETWORK_TEXT }
                _state.value = _state.value.copy(
                    busy = false, enabled = isEnabled(uid),
                    lastSyncMs = store.getLong(KEY_LAST_SYNC, 0L), message = message, prompt = null
                )
            }
        }
    }

    /** The first-time prompt: save this device's setup, or not now. */
    fun answerBackUp(yes: Boolean) {
        val uid = userId ?: return
        _state.value = _state.value.copy(prompt = null)
        if (yes) {
            turnOn()
        } else {
            store.edit().putBoolean(KEY_DECLINED + uid, true).apply()
            _state.value = _state.value.copy(message = null)
        }
    }

    /** The first-time prompt when the account already has a saved setup. */
    fun answerChoose(choice: CloudChoice) {
        val uid = userId ?: return
        _state.value = _state.value.copy(prompt = null)
        when (choice) {
            CloudChoice.COMBINE -> turnOn()
            CloudChoice.LATER -> {
                store.edit().putBoolean(KEY_DECLINED + uid, true).apply()
                _state.value = _state.value.copy(message = null)
            }
            CloudChoice.USE_SAVED -> scope.launch {
                mutex.withLock {
                    _state.value = _state.value.copy(busy = true, message = null)
                    val message = runCatching {
                        val remote = fetch()
                        if (remote !is Remote.Found || !usable(remote.data)) {
                            "Couldn't reach your saved setup. Try again."
                        } else {
                            apply(remote.data)
                            saved(uid, remote.data)
                            setEnabled(uid, true)
                            "Using your saved Live TV setup."
                        }
                    }.getOrElse { NETWORK_TEXT }
                    _state.value = _state.value.copy(
                        busy = false, enabled = isEnabled(uid),
                        lastSyncMs = store.getLong(KEY_LAST_SYNC, 0L), message = message
                    )
                }
            }
        }
    }

    /** Stops syncing on this device. The saved copy stays in the account. */
    fun turnOff() {
        val uid = userId ?: return
        setEnabled(uid, false)
        store.edit().putBoolean(KEY_DECLINED + uid, true).apply()
        _state.value = _state.value.copy(enabled = false, message = "Off. Your saved copy stays saved.", prompt = null)
    }

    /** Removes the saved copy from the cloud and stops syncing here. This device keeps its setup. */
    fun deleteCloudCopy() {
        scope.launch {
            mutex.withLock {
                val uid = userId ?: return@withLock
                _state.value = _state.value.copy(busy = true, message = null)
                val message = runCatching {
                    val reply = call("DELETE", "/v1/setup")
                    if (reply.code == 200) {
                        setEnabled(uid, false)
                        store.edit().putBoolean(KEY_DECLINED + uid, true).apply()
                        baseFile().delete()
                        "Deleted your saved Live TV setup from the cloud. This device keeps its own setup."
                    } else failText(reply.code)
                }.getOrElse { NETWORK_TEXT }
                _state.value = _state.value.copy(
                    busy = false, enabled = isEnabled(uid),
                    lastSyncMs = store.getLong(KEY_LAST_SYNC, 0L), message = message
                )
            }
        }
    }

    /** Brings back the copy from before the last save (a safety net if a sync went wrong). */
    fun restorePrevious() {
        scope.launch {
            mutex.withLock {
                val uid = userId ?: return@withLock
                _state.value = _state.value.copy(busy = true, message = null)
                val message = runCatching {
                    val reply = call("GET", "/v1/setup/previous")
                    val data = reply.json?.optJSONObject("data")
                    // Goes with the saved copy: without it the PUT would use 0 and get a 409.
                    val currentRevision = reply.json?.optInt("currentRevision") ?: 0
                    when {
                        reply.code == 404 -> "There is no earlier copy yet."
                        reply.code != 200 || data == null -> failText(reply.code)
                        else -> {
                            apply(data)
                            when (val result = put(currentRevision, data)) {
                                is Put.Saved -> { saved(uid, data); "Restored the earlier copy." }
                                else -> putText(result)
                            }
                        }
                    }
                }.getOrElse { NETWORK_TEXT }
                _state.value = _state.value.copy(
                    busy = false, enabled = isEnabled(uid),
                    lastSyncMs = store.getLong(KEY_LAST_SYNC, 0L), message = message
                )
            }
        }
    }

    // ------------------------------------------------------------------ deciding and syncing

    private fun isEnabled(uid: String) = store.getBoolean(KEY_ENABLED + uid, false)

    private fun isOn(): Boolean = userId?.let { isEnabled(it) } == true

    private fun setEnabled(uid: String, on: Boolean) {
        store.edit().putBoolean(KEY_ENABLED + uid, on).apply()
        if (userId == uid) _state.value = _state.value.copy(enabled = on)
    }

    /**
     * An account this device hasn't synced with yet. If it has no Live TV setup and the account
     * does, the saved setup is restored. A device with its own setup is asked once whether to
     * save it, or which of the two setups to keep.
     */
    private suspend fun decideFirstTime(uid: String) {
        if (store.getBoolean(KEY_DECLINED + uid, false)) return
        runCatching {
            val local = prefs.exportForSync()
            val hasLocal = prefs.syncHasSources(local)
            val remote = fetch()
            when {
                remote is Remote.Found && usable(remote.data) -> {
                    if (!hasLocal) {
                        apply(remote.data)
                        saved(uid, remote.data)
                        setEnabled(uid, true)
                        _state.value = _state.value.copy(
                            lastSyncMs = store.getLong(KEY_LAST_SYNC, 0L),
                            message = "Restored your Live TV setup."
                        )
                    } else {
                        _state.value = _state.value.copy(prompt = CloudPrompt.CHOOSE)
                    }
                }
                remote is Remote.None && hasLocal -> {
                    _state.value = _state.value.copy(prompt = CloudPrompt.BACK_UP)
                }
            }
        }
    }

    private suspend fun syncIfChanged() {
        val hash = sha(prefs.exportForSync().toString())
        if (hash == store.getString(KEY_LOCAL_HASH, null)) return
        mutex.withLock {
            val uid = userId ?: return@withLock
            if (isEnabled(uid)) reconcileAndReport(uid)
        }
    }

    private suspend fun reconcileAndReport(uid: String, doneMessage: String? = null) {
        _state.value = _state.value.copy(busy = true)
        val result = runCatching { reconcile(uid) }
        _state.value = _state.value.copy(
            busy = false,
            lastSyncMs = store.getLong(KEY_LAST_SYNC, 0L),
            message = result.fold(onSuccess = { it ?: doneMessage }, onFailure = { NETWORK_TEXT })
        )
    }

    /**
     * One round: take the saved copy, combine it with this device's changes, apply what's new here
     * and save what's new there. Tries again if another device saved in between. Returns a message
     * to show, or null when there is nothing to say.
     */
    private suspend fun reconcile(uid: String): String? {
        repeat(3) {
            val remote = fetch()
            val local = prefs.exportForSync()
            val removedHere = prefs.removedSourceIds()
            when {
                remote is Remote.Failed -> return failText(remote.code)
                remote is Remote.Found && usable(remote.data) -> {
                    val merged = drive.merge(readBase(uid), local, remote.data, false, removedHere)
                    val changedHere = merged.toString() != local.toString()
                    if (changedHere) apply(merged)
                    val news = if (changedHere) UPDATED_TEXT else null
                    if (merged.toString() == remote.data.toString()) {
                        saved(uid, remote.data)
                        return news
                    }
                    when (val result = put(remote.revision, merged)) {
                        is Put.Saved -> { saved(uid, merged); return news }
                        Put.Conflict -> Unit // another device saved first: look again
                        else -> return putText(result)
                    }
                }
                else -> {
                    // Nothing usable saved: never let that empty this device; save this setup if it has one.
                    if (!prefs.syncHasSources(local)) return null
                    val savedRevision = (remote as? Remote.Found)?.revision ?: 0
                    when (val result = put(savedRevision, local)) {
                        is Put.Saved -> { saved(uid, local); return null }
                        Put.Conflict -> Unit
                        else -> return putText(result)
                    }
                }
            }
        }
        return "Couldn't finish syncing. Will try again."
    }

    private fun usable(data: JSONObject): Boolean = prefs.syncHasSources(data)

    /** Writes a saved setup into this device, without it being saved straight back. */
    private suspend fun apply(data: JSONObject) {
        applying = true
        try {
            drive.applyLocally(data)
        } finally {
            applying = false
        }
    }

    /** Remembers what both sides now agree on: the starting point for the next combine. */
    private suspend fun saved(uid: String, data: JSONObject) {
        val removedHere = prefs.removedSourceIds()
        runCatching { baseFile().writeText(data.toString()) }
        store.edit()
            .putString(KEY_BASE_USER, uid)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .putString(KEY_LOCAL_HASH, sha(prefs.exportForSync().toString()))
            .apply()
        if (removedHere.isNotEmpty()) prefs.forgetRemovedSources(removedHere)
    }

    private fun baseFile() = File(appContext.filesDir, "livetv_cloud_base.json")

    /** What both sides last agreed on, but only if it was for this account. */
    private fun readBase(uid: String): JSONObject? {
        if (store.getString(KEY_BASE_USER, null) != uid) return null
        return runCatching { JSONObject(baseFile().readText()) }.getOrNull()
    }

    // ------------------------------------------------------------------ the service

    private sealed interface Remote {
        data object None : Remote
        data class Found(val revision: Int, val data: JSONObject) : Remote
        data class Failed(val code: Int) : Remote
    }

    private sealed interface Put {
        data class Saved(val revision: Int) : Put
        data object Conflict : Put
        data object TooBig : Put
        data object Limit : Put
        data class Failed(val code: Int) : Put
    }

    private class Reply(val code: Int, val json: JSONObject?)

    private fun serverAvailable(): Boolean {
        val server = ServerConfigurationRepository.active.value
        return !server.isCustom &&
            server.backendUrl.toHttpUrlOrNull()?.host.equals("api.nuvio.tv", ignoreCase = true)
    }

    private suspend fun fetch(): Remote {
        val reply = call("GET", "/v1/setup")
        val data = reply.json?.optJSONObject("data")
        return when {
            reply.code == 200 && data != null -> Remote.Found(reply.json?.optInt("revision") ?: 0, data)
            reply.code == 404 -> Remote.None
            else -> Remote.Failed(reply.code)
        }
    }

    private suspend fun put(baseRevision: Int, data: JSONObject): Put {
        val body = JSONObject()
            .put("baseRevision", baseRevision)
            .put("device", Build.MODEL ?: "Phone")
            .put("deviceId", deviceId)
            .put("data", data)
        val reply = call("PUT", "/v1/setup", body)
        return when (reply.code) {
            200 -> Put.Saved(reply.json?.optInt("revision") ?: (baseRevision + 1))
            409 -> Put.Conflict
            413 -> Put.TooBig
            429 -> Put.Limit
            else -> Put.Failed(reply.code)
        }
    }

    /** Calls the service with the Nuvio sign-in; if the sign-in has just expired, refreshes it once and retries. */
    private suspend fun call(method: String, path: String, body: JSONObject? = null): Reply {
        var reply = callOnce(method, path, body)
        if (reply.code == 401) {
            runCatching { SupabaseProvider.client.auth.refreshCurrentSession() }
            reply = callOnce(method, path, body)
        }
        return reply
    }

    private suspend fun callOnce(method: String, path: String, body: JSONObject?): Reply = withContext(Dispatchers.IO) {
        val token = SupabaseProvider.client.auth.currentSessionOrNull()?.accessToken
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext Reply(401, null)
        val server = ServerConfigurationRepository.active.value
        val request = Request.Builder()
            .url(CLOUD_URL + path)
            .header("Authorization", "Bearer $token")
            .header("apikey", server.publishableKey)
            .method(method, body?.toString()?.toRequestBody(json))
            .build()
        http.newCall(request).execute().use { response ->
            Reply(response.code, runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull())
        }
    }

    private fun failText(code: Int) = when (code) {
        401 -> "Sign in to Nuvio again to sync."
        else -> NETWORK_TEXT
    }

    private fun putText(result: Put) = when (result) {
        Put.Conflict -> "Your saved setup changed on another device. Try again."
        Put.TooBig -> "Your Live TV setup is too big to save to the cloud."
        Put.Limit -> "Too many saves today. Syncing continues tomorrow."
        is Put.Failed -> failText(result.code)
        is Put.Saved -> ""
    }

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        /** The new Cloudflare Worker + D1 service, shared with the TV app. */
        val CLOUD_URL: String = SyncBuildConfig.LIVETV_SYNC_URL
        private const val UPDATED_TEXT = "Updated from your other device."
        private const val NETWORK_TEXT = "Couldn't reach sync. Will try again."
        private const val KEY_ENABLED = "enabled_"
        private const val KEY_DECLINED = "declined_"
        private const val KEY_BASE_USER = "base_user"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_LOCAL_HASH = "local_hash"
        private const val KEY_DEVICE_ID = "device_id"
    }
}

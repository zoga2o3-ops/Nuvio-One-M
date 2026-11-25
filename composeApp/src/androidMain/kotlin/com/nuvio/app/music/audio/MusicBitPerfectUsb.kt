package com.nuvio.app.music.audio

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import com.decent.usbaudio.UsbAudioDevice
import com.nuvio.app.music.MusicSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns the optional direct-to-USB path used by Music's Media3 audio sink. */
object MusicBitPerfectUsb {
    data class State(
        val enabled: Boolean = false,
        val active: Boolean = false,
        val bitPerfect: Boolean = false,
        val deviceName: String? = null,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var context: Context? = null
    @Volatile private var output: ExclusiveUsbOutput? = null
    @Volatile private var sourceLossless = false
    @Volatile private var directReleasedAtMs = 0L

    fun init(context: Context) {
        val app = context.applicationContext
        this.context = app
        if (output == null) synchronized(this) {
            if (output == null) output = ExclusiveUsbOutput(app)
        }
        restoreEnabledState()
    }

    fun audioOutput(): ExclusiveUsbOutput? = output

    fun setSourceLossless(lossless: Boolean) {
        sourceLossless = lossless
        if (!lossless) {
            val current = _state.value
            _state.value = current.copy(bitPerfect = false, message = null)
        }
    }

    fun isSourceLossless(): Boolean = sourceLossless

    /** Called after the preference is restored; never opens a permission prompt on its own. */
    @Synchronized
    fun restoreEnabledState() {
        val app = context ?: return
        val enabled = MusicSettings.bitPerfectUsb.value
        val device = findAudioDevice(app)
        val granted = device?.let { UsbAudioDevice.getInstance(app).hasPermission(it) } == true
        output?.setWanted(enabled && granted)
        _state.value = State(
            enabled = enabled,
            active = output?.isActive() == true,
            deviceName = device?.productName,
            message = when {
                !enabled -> null
                device == null -> "Connect a USB audio DAC to use direct output."
                !granted -> "USB permission is needed before direct output can start."
                else -> "USB direct output is enabled; the active route is verified per track."
            },
        )
    }

    /** Requests Android's standard USB permission only after the listener enables the option. */
    fun setEnabled(enabled: Boolean, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val app = context ?: return onResult(false, "Music playback is not initialized yet.")
        if (!enabled) {
            if (output?.isActive() == true) markDirectReleased()
            output?.setWanted(false)
            _state.value = State(enabled = false)
            return onResult(true, null)
        }

        val device = findAudioDevice(app)
        if (device == null) {
            _state.value = State(enabled = false, message = "No USB audio DAC is connected.")
            return onResult(false, "Connect a USB audio DAC, then try again.")
        }

        val usb = UsbAudioDevice.getInstance(app)
        if (usb.hasPermission(device)) {
            enableAfterPermission(device)
            return onResult(true, null)
        }

        _state.value = State(
            enabled = false,
            deviceName = device.productName,
            message = "Waiting for USB permission.",
        )
        usb.requestPermission(device) { granted ->
            if (granted) {
                enableAfterPermission(device)
                onResult(true, null)
            } else {
                _state.value = State(
                    enabled = false,
                    deviceName = device.productName,
                    message = "USB permission was not granted.",
                )
                onResult(false, "USB permission was not granted.")
            }
        }
    }

    fun refreshActive(active: Boolean, bitPerfect: Boolean = active, message: String? = null) {
        val current = _state.value
        _state.value = current.copy(active = active, bitPerfect = bitPerfect, message = message ?: current.message)
    }

    fun markDirectReleased() {
        directReleasedAtMs = SystemClock.elapsedRealtime()
    }

    /** Wait for Android to reclaim a DAC after the native driver releases it. */
    fun normalOutputPending(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val releasedAt = directReleasedAtMs
        if (releasedAt == 0L) return false
        if (SystemClock.elapsedRealtime() - releasedAt >= USB_HAND_BACK_TIMEOUT_MS) {
            directReleasedAtMs = 0L
            return false
        }
        val manager = context?.getSystemService(AudioManager::class.java)
        val usbVisibleToAndroid = manager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().any {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (usbVisibleToAndroid) {
            directReleasedAtMs = 0L
            return false
        }
        return true
    }

    fun release() {
        output?.release()
        output = null
        context = null
        sourceLossless = false
        directReleasedAtMs = 0L
        _state.value = State()
    }

    private fun enableAfterPermission(device: UsbDevice) {
        output?.setWanted(true)
        _state.value = State(
            enabled = true,
            active = output?.isActive() == true,
            deviceName = device.productName,
            message = "Enabled. Direct output is verified only for supported lossless PCM tracks.",
        )
    }

    private fun findAudioDevice(context: Context): UsbDevice? {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        return manager.deviceList.values.firstOrNull { device ->
            (0 until device.interfaceCount).any { index ->
                device.getInterface(index).interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_AUDIO
            }
        }
    }

    private const val USB_HAND_BACK_TIMEOUT_MS = 3_000L
}

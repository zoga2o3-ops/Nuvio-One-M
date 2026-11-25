@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.app.music.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.nuvio.app.music.MusicEqualizerPreset
import com.nuvio.app.music.MusicSettings
import java.nio.ByteBuffer

/** Builds the usual Media3 sink and wraps it with the optional direct USB path. */
internal class MusicAudioRenderersFactory(
    context: Context,
    private val usbOutput: ExclusiveUsbOutput?,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? {
        val normalSink = super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
        return when {
            normalSink == null -> null
            usbOutput == null -> normalSink
            else -> MusicBitPerfectAudioSink(normalSink, usbOutput)
        }
    }
}

/**
 * Directs supported integer PCM to LastWave's native USB Audio Class driver.
 * All other formats keep Media3's normal sink. The regular sink is configured
 * alongside the direct route so a USB failure can fall back without losing the
 * decoder buffer that Media3 is retrying.
 */
internal class MusicBitPerfectAudioSink(
    private val normal: AudioSink,
    private val usb: ExclusiveUsbOutput,
) : AudioSink {
    private val idleReleaseHandler = Handler(Looper.getMainLooper())
    private val idleRelease = Runnable {
        if (!playing && usb.isActive()) {
            MusicBitPerfectUsb.markDirectReleased()
            usb.reset()
            directActive = false
            MusicBitPerfectUsb.refreshActive(false, false)
        }
    }
    private var configuredFormat: Format? = null
    @Volatile private var directActive = false
    private var directEligible = false
    private var playing = false
    private var endOfStream = false
    private var playbackParameters = PlaybackParameters.DEFAULT

    val isBitPerfectActive: Boolean
        get() = directActive && usb.isActive() && MusicBitPerfectUsb.isSourceLossless() &&
            usb.isClockMatched() &&
            (usb.usesHardwareVolume() || usb.softwareGain() >= 0.999999f)

    override fun setListener(listener: AudioSink.Listener) = normal.setListener(listener)
    override fun setClock(clock: Clock) = normal.setClock(clock)
    override fun supportsFormat(format: Format): Boolean = normal.supportsFormat(format)
    override fun getFormatSupport(format: Format): Int = normal.getFormatSupport(format)
    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = normal.getFormatOffloadSupport(format)

    override fun configure(format: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        idleReleaseHandler.removeCallbacks(idleRelease)
        val channelMapIsIdentity = outputChannels == null || outputChannels.indices.all { outputChannels[it] == it }
        val useDirect = MusicSettings.bitPerfectUsb.value && usb.isWanted() &&
            MusicBitPerfectUsb.isSourceLossless() && isDirectPcm(format) && channelMapIsIdentity &&
            !MusicSettings.skipSilence.value && MusicSettings.equalizerPreset.value == MusicEqualizerPreset.OFF &&
            playbackParameters.speed == 1f && playbackParameters.pitch == 1f
        if (!useDirect && (directActive || usb.isActive())) {
            MusicBitPerfectUsb.markDirectReleased()
            usb.reset()
        }
        directActive = false
        MusicBitPerfectUsb.refreshActive(false, false)
        normal.configure(format, specifiedBufferSize, outputChannels)
        configuredFormat = format
        directEligible = useDirect
        endOfStream = false
        if (!useDirect) return

        directActive = runCatching { usb.configure(format) && usb.isClockMatched() }.getOrDefault(false)
        if (!directActive) {
            MusicBitPerfectUsb.markDirectReleased()
            if (usb.isActive()) usb.reset()
            MusicBitPerfectUsb.refreshActive(
                active = false,
                bitPerfect = false,
                message = usb.lastFailureReason ?: "This DAC could not open the track's exact PCM format; Android output is being used.",
            )
            return
        }
        MusicBitPerfectUsb.refreshActive(directActive && usb.isActive(), isBitPerfectActive, message = null)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        if (directActive && usb.isActive()) usb.getCurrentPositionUs()
        else normal.getCurrentPositionUs(sourceEnded)

    override fun getAudioTrackBufferSizeUs(): Long =
        if (directActive && usb.isActive()) 0L else normal.audioTrackBufferSizeUs

    override fun play() {
        idleReleaseHandler.removeCallbacks(idleRelease)
        playing = true
        if (directEligible && !directActive) {
            val format = configuredFormat
            directActive = format != null && runCatching {
                usb.configure(format) && usb.isClockMatched()
            }.getOrDefault(false)
            MusicBitPerfectUsb.refreshActive(directActive && usb.isActive(), isBitPerfectActive)
        }
        if (directActive && usb.isActive()) usb.setPaused(false) else normal.play()
    }

    override fun pause() {
        playing = false
        if (directActive && usb.isActive()) {
            usb.setPaused(true)
            idleReleaseHandler.postDelayed(idleRelease, USB_IDLE_RELEASE_MS)
        } else {
            normal.pause()
        }
    }

    override fun handleDiscontinuity() {
        if (directActive && usb.isActive()) usb.handleDiscontinuity() else normal.handleDiscontinuity()
    }

    override fun handleBuffer(buffer: ByteBuffer, bufferPresentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (directActive && usb.isActive()) {
            val accepted = runCatching {
                usb.write(buffer, bufferPresentationTimeUs, isFloatBuffer = false)
            }.getOrDefault(false)
            if (accepted) return true
            if (usb.isActive()) return false
            directActive = false
            MusicBitPerfectUsb.markDirectReleased()
            MusicBitPerfectUsb.refreshActive(false, false)
            if (playing) normal.play()
        }
        if (MusicBitPerfectUsb.normalOutputPending()) return false
        return normal.handleBuffer(buffer, bufferPresentationTimeUs, encodedAccessUnitCount)
    }

    override fun playToEndOfStream() {
        if (directActive && usb.isActive()) {
            endOfStream = true
        } else {
            normal.playToEndOfStream()
        }
    }

    override fun isEnded(): Boolean = if (directActive && usb.isActive()) {
        (endOfStream && !usb.hasQueuedAudio()).also { ended ->
            if (ended) idleReleaseHandler.postDelayed(idleRelease, USB_IDLE_RELEASE_MS)
        }
    } else {
        normal.isEnded()
    }

    override fun hasPendingData(): Boolean = if (directActive && usb.isActive()) {
        if (endOfStream) usb.hasQueuedAudio() else usb.hasPendingData()
    } else {
        MusicBitPerfectUsb.normalOutputPending() || normal.hasPendingData()
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        this.playbackParameters = playbackParameters
        normal.setPlaybackParameters(playbackParameters)
        if (directActive && (playbackParameters.speed != 1f || playbackParameters.pitch != 1f)) {
            directEligible = false
            MusicBitPerfectUsb.markDirectReleased()
            usb.reset()
            directActive = false
            MusicBitPerfectUsb.refreshActive(false, false)
            if (playing) normal.play()
        }
    }

    override fun getPlaybackParameters(): PlaybackParameters =
        if (directActive) PlaybackParameters.DEFAULT else normal.playbackParameters

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        normal.setSkipSilenceEnabled(if (directActive) false else skipSilenceEnabled)
    }

    override fun getSkipSilenceEnabled(): Boolean =
        if (directActive) false else normal.skipSilenceEnabled

    override fun setAudioAttributes(audioAttributes: AudioAttributes) = normal.setAudioAttributes(audioAttributes)
    override fun getAudioAttributes(): AudioAttributes? = normal.audioAttributes
    override fun setAudioSessionId(audioSessionId: Int) = normal.setAudioSessionId(audioSessionId)
    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        if (!directActive) normal.setAuxEffectInfo(auxEffectInfo)
    }
    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) = normal.setPreferredDevice(audioDeviceInfo)
    override fun enableTunnelingV21() = normal.enableTunnelingV21()
    override fun disableTunneling() = normal.disableTunneling()
    override fun setOffloadMode(offloadMode: Int) = normal.setOffloadMode(offloadMode)
    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) =
        normal.setOffloadDelayPadding(delayInFrames, paddingInFrames)
    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) = normal.setOutputStreamOffsetUs(outputStreamOffsetUs)

    override fun setVolume(volume: Float) {
        normal.setVolume(volume)
        if (directActive) {
            usb.setVolume(volume)
            MusicBitPerfectUsb.refreshActive(usb.isActive(), isBitPerfectActive)
        }
    }

    override fun flush() {
        if (directActive && usb.isActive()) usb.flush()
        normal.flush()
        endOfStream = false
    }

    override fun reset() {
        if (directActive) {
            usb.prepareForNextItem()
            idleReleaseHandler.postDelayed(idleRelease, USB_IDLE_RELEASE_MS)
        }
        normal.reset()
        configuredFormat = null
        directEligible = false
        directActive = false
        endOfStream = false
        MusicBitPerfectUsb.refreshActive(false, false)
    }

    override fun release() {
        idleReleaseHandler.removeCallbacks(idleRelease)
        normal.release()
    }

    private fun isDirectPcm(format: Format): Boolean =
        format.sampleMimeType == androidx.media3.common.MimeTypes.AUDIO_RAW &&
            format.sampleRate > 0 && format.channelCount in 1..2 &&
            format.encoderDelay == 0 && format.encoderPadding == 0 &&
            format.pcmEncoding in setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)

    private companion object {
        const val USB_IDLE_RELEASE_MS = 2_500L
    }
}

package com.nova.assistant.voice.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import com.nova.assistant.core.diagnostics.DevDiagnostics
import com.nova.assistant.core.logging.NovaLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Continuous audio capture streamer using Android AudioRecord.
 * Streams 16kHz mono 16-bit PCM audio chunks to a consumer (e.g. WakeWordEngine).
 * Dynamically manages Bluetooth SCO and wired headset microphone routing.
 */
class AudioStreamManager(
    private val context: Context,
    private val sampleRate: Int = 16000
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var isBluetoothScoStarted = false
    private var scoReceiver: BroadcastReceiver? = null
    private var currentCallback: ((ShortArray, Int) -> Unit)? = null

    // Rolling circular buffer holding the last 2.0 seconds of audio at 16kHz
    private val ringBufferSize = sampleRate * 2 // 32,000 samples
    private val ringBuffer = ShortArray(ringBufferSize)
    private var ringBufferWritePos = 0
    private val ringBufferLock = Any()

    /**
     * Retrieves a snapshot of the most recent audio samples captured by the stream.
     * Used for full-utterance speaker verification upon wake-word detection.
     */
    fun getRecentAudioSnapshot(durationMs: Long = 1800): ShortArray {
        val count = ((sampleRate * durationMs) / 1000).toInt().coerceIn(1600, ringBufferSize)
        val result = ShortArray(count)
        synchronized(ringBufferLock) {
            val startPos = (ringBufferWritePos - count + ringBufferSize) % ringBufferSize
            for (i in 0 until count) {
                result[i] = ringBuffer[(startPos + i) % ringBufferSize]
            }
        }
        return result
    }

    /**
     * Starts continuous audio capture and feeds PCM chunks to [onAudioChunk].
     */
    fun startStreaming(onAudioChunk: (ShortArray, Int) -> Unit): Boolean {
        currentCallback = onAudioChunk
        if (isRunning.get()) {
            isPaused.set(false)
            NovaLogger.d("AudioStreamManager", "Updated callback and unpaused active AudioRecord stream.")
            return true
        }

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        try {
            logAvailableAudioInputDevices()
            setupBluetoothScoIfNeeded()

            // Try VOICE_RECOGNITION first, fallback to MIC
            var record: AudioRecord? = null
            try {
                record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            } catch (e: Exception) {
                NovaLogger.w("AudioStreamManager", "VOICE_RECOGNITION audio source failed, falling back to MIC", e)
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                NovaLogger.e("AudioStreamManager", "AudioRecord failed to initialize.")
                DevDiagnostics.show(context, "AudioRecord FAILED to initialize", true)
                record.release()
                return false
            }

            // Route to wired/Bluetooth earphone microphone if connected
            configurePreferredInputDevice(record)

            record.startRecording()
            audioRecord = record
            isRunning.set(true)
            isPaused.set(false)

            val routedDesc = getActiveDeviceDescription(record)
            NovaLogger.i("AudioStreamManager", "AudioRecord recording started: $routedDesc (16kHz Mono 16-bit PCM, buffer=$bufferSize bytes)")
            DevDiagnostics.show(context, "Audio Capture Active ($routedDesc)")

            recordingJob = scope.launch {
                val pcmBuffer = ShortArray(bufferSize / 2)
                var chunkCount = 0L

                try {
                    while (isActive && isRunning.get()) {
                        if (isPaused.get()) {
                            kotlinx.coroutines.delay(40)
                            continue
                        }

                        val readCount = record.read(pcmBuffer, 0, pcmBuffer.size)
                        if (readCount > 0) {
                            // Detailed periodic PCM diagnostics (every 100 chunks ~ 1.28s)
                            if (chunkCount < 3L || chunkCount % 100L == 0L) {
                                var nonZero = 0
                                var maxAmp = 0
                                var sumSquares = 0.0
                                for (i in 0 until readCount) {
                                    val v = pcmBuffer[i].toInt()
                                    val absV = kotlin.math.abs(v)
                                    if (absV > maxAmp) maxAmp = absV
                                    if (v != 0) nonZero++
                                    sumSquares += (v.toDouble() * v.toDouble())
                                }
                                val rms = sqrt(sumSquares / readCount)
                                NovaLogger.d("AudioStreamManager", "[PCM-DIAG] chunk#$chunkCount read=$readCount nonZero=$nonZero peak=$maxAmp rms=${String.format("%.1f", rms)} (activeDevice=${getActiveDeviceDescription(record)})")
                            }
                            chunkCount++

                            // Record into rolling ring buffer
                            synchronized(ringBufferLock) {
                                for (i in 0 until readCount) {
                                    ringBuffer[ringBufferWritePos] = pcmBuffer[i]
                                    ringBufferWritePos = (ringBufferWritePos + 1) % ringBufferSize
                                }
                            }

                            val cb = currentCallback
                            if (cb != null && !isPaused.get()) {
                                cb(pcmBuffer, readCount)
                            }
                        } else if (readCount < 0) {
                            NovaLogger.w("AudioStreamManager", "AudioRecord read returned error code: $readCount")
                            kotlinx.coroutines.delay(20)
                        }
                    }
                } catch (e: CancellationException) {
                    NovaLogger.d("AudioStreamManager", "Audio streaming job cancelled.")
                } catch (e: Exception) {
                    NovaLogger.e("AudioStreamManager", "Exception in audio recording loop", e)
                    DevDiagnostics.show(context, "AudioRecord Loop Exception: ${e.message}", true)
                }
            }
            return true
        } catch (e: SecurityException) {
            NovaLogger.e("AudioStreamManager", "SecurityException starting AudioRecord (permission denied)", e)
            DevDiagnostics.show(context, "AudioRecord Permission Denied", true)
            return false
        } catch (e: Exception) {
            NovaLogger.e("AudioStreamManager", "Unexpected exception creating AudioRecord", e)
            DevDiagnostics.show(context, "AudioRecord Init Error: ${e.message}", true)
            return false
        }
    }

    private fun setupBluetoothScoIfNeeded() {
        val am = audioManager ?: return
        if (am.isBluetoothScoAvailableOffCall) {
            try {
                if (!isBluetoothScoStarted) {
                    am.startBluetoothSco()
                    am.isBluetoothScoOn = true
                    isBluetoothScoStarted = true
                    NovaLogger.i("AudioStreamManager", "Initiated Bluetooth SCO audio connection.")
                }
            } catch (e: Exception) {
                NovaLogger.w("AudioStreamManager", "Failed to start Bluetooth SCO", e)
            }
        }
    }

    private fun stopBluetoothSco() {
        val am = audioManager ?: return
        if (isBluetoothScoStarted) {
            try {
                am.stopBluetoothSco()
                am.isBluetoothScoOn = false
                isBluetoothScoStarted = false
                NovaLogger.d("AudioStreamManager", "Stopped Bluetooth SCO.")
            } catch (e: Exception) {
                NovaLogger.w("AudioStreamManager", "Error stopping Bluetooth SCO", e)
            }
        }
    }

    private fun logAvailableAudioInputDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val am = audioManager ?: return
            val devices = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            for (dev in devices) {
                val typeName = getDeviceTypeName(dev.type)
                NovaLogger.d("AudioStreamManager", "Available Audio Input Device: $typeName (id=${dev.id}, address=${dev.address})")
            }
        }
    }

    private fun configurePreferredInputDevice(record: AudioRecord) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val am = audioManager ?: return
            val devices = am.getDevices(AudioManager.GET_DEVICES_INPUTS)

            // Prefer Wired Headset or Bluetooth Headset mic
            val preferred = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }

            if (preferred != null) {
                val success = record.setPreferredDevice(preferred)
                NovaLogger.i("AudioStreamManager", "Set preferred input device to ${getDeviceTypeName(preferred.type)}: success=$success")
            }
        }
    }

    private fun getActiveDeviceDescription(record: AudioRecord): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val dev = record.routedDevice
            if (dev != null) {
                return getDeviceTypeName(dev.type)
            }
        }
        return "Default Mic"
    }

    private fun getDeviceTypeName(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset Mic"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Headset Mic (SCO)"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE Headset Mic"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset Mic"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio In"
            else -> "Audio Device ($type)"
        }
    }

    /**
     * Pauses streaming without releasing the AudioRecord instance.
     */
    fun pauseStreaming() {
        isPaused.set(true)
        NovaLogger.d("AudioStreamManager", "Audio streaming paused.")
    }

    /**
     * Resumes streaming if AudioRecord is currently active.
     */
    fun resumeStreaming() {
        isPaused.set(false)
        NovaLogger.d("AudioStreamManager", "Audio streaming resumed.")
    }

    /**
     * Stops audio capture and completely releases AudioRecord resources.
     */
    fun stopStreaming() {
        isRunning.set(false)
        isPaused.set(false)
        currentCallback = null

        recordingJob?.cancel()
        recordingJob = null

        stopBluetoothSco()

        try {
            audioRecord?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED) {
                    if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        it.stop()
                    }
                }
                it.release()
            }
        } catch (e: Exception) {
            NovaLogger.e("AudioStreamManager", "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
            NovaLogger.i("AudioStreamManager", "AudioStreamManager stopped and released.")
        }
    }

    fun isCapturing(): Boolean = isRunning.get() && !isPaused.get()
}

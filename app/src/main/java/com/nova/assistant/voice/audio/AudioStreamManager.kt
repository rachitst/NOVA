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

            // VOICE_RECOGNITION first for full mic gain; on this device's SCO path it is the only
            // source that delivers audio at a level usable by the KWS/ASR models.
            var record: AudioRecord? = null
            for (source in intArrayOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
            )) {
                try {
                    record = AudioRecord(
                        source,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        NovaLogger.i("AudioStreamManager", "AudioRecord initialized with audio source $source")
                        break
                    }
                    record.release()
                    record = null
                } catch (e: Exception) {
                    NovaLogger.w("AudioStreamManager", "Audio source $source failed", e)
                    record = null
                }
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                NovaLogger.e("AudioStreamManager", "AudioRecord failed to initialize with any audio source.")
                DevDiagnostics.show(context, "AudioRecord FAILED to initialize", true)
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

                // DEV-only: periodically flush recent raw PCM to a file for offline KWS analysis
                var devDumpJob: Job? = null
                val devPending = ArrayList<ShortArray>()
                if (com.nova.assistant.core.config.NovaConfig.DEV) {
                    devDumpJob = scope.launch { runDevPcmDump(devPending) }
                }

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
                                if (devDumpJob != null) {
                                    synchronized(devPending) { devPending.add(pcmBuffer.copyOf(readCount)) }
                                }
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
                } finally {
                    devDumpJob?.cancel()
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
        if (com.nova.assistant.core.config.NovaConfig.DEV_FORCE_BUILTIN_MIC) return
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
                @Suppress("DEPRECATION")
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
            if (com.nova.assistant.core.config.NovaConfig.DEV_FORCE_BUILTIN_MIC) {
                val builtin = am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
                }
                if (builtin != null) {
                    val ok = record.setPreferredDevice(builtin)
                    NovaLogger.i("AudioStreamManager", "[DEV] Forced built-in mic as preferred input: $ok")
                    return
                }
            }
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

    /**
     * DEV-only diagnostic: continuously appends raw 16-bit PCM (16 kHz mono) to
     * nova_dev_stream.raw so captured audio can be analyzed offline (e.g. KWS debugging).
     * Never runs in release mode (gated by NovaConfig.DEV at the call site).
     */
    private suspend fun runDevPcmDump(pending: ArrayList<ShortArray>) {
        val dumpDir = context.getExternalFilesDir(null) ?: return
        val rawFile = java.io.File(dumpDir, "nova_dev_stream.raw")
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            kotlinx.coroutines.delay(4000)
            val batch = synchronized(pending) {
                if (pending.isEmpty()) null else {
                    val copy = ArrayList(pending)
                    pending.clear()
                    copy
                }
            } ?: continue
            try {
                if (rawFile.length() > 40_000_000L) rawFile.delete()
                java.io.FileOutputStream(rawFile, true).use { out ->
                    for (chunk in batch) {
                        val bytes = ByteArray(chunk.size * 2)
                        for (i in chunk.indices) {
                            bytes[i * 2] = (chunk[i].toInt() and 0xFF).toByte()
                            bytes[i * 2 + 1] = ((chunk[i].toInt() shr 8) and 0xFF).toByte()
                        }
                        out.write(bytes)
                    }
                }
            } catch (e: Exception) {
                NovaLogger.w("AudioStreamManager", "DEV PCM dump failed: ${e.message}")
            }
        }
    }

    private fun writeWav(file: java.io.File, samples: ShortArray) {
        val dataLen = samples.size * 2
        java.io.DataOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(file))).use { out ->
            out.writeBytes("RIFF"); out.writeIntLe(36 + dataLen); out.writeBytes("WAVE")
            out.writeBytes("fmt "); out.writeIntLe(16); out.writeShortLe(1); out.writeShortLe(1)
            out.writeIntLe(sampleRate); out.writeIntLe(sampleRate * 2); out.writeShortLe(2); out.writeShortLe(16)
            out.writeBytes("data"); out.writeIntLe(dataLen)
            for (s in samples) out.writeShortLe(s.toInt() and 0xFFFF)
        }
    }

    private fun java.io.DataOutputStream.writeIntLe(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF); write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
    }

    private fun java.io.DataOutputStream.writeShortLe(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
    }
}

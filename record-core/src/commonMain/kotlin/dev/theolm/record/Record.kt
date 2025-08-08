package dev.theolm.record

import dev.theolm.record.config.RecordConfig
import dev.theolm.record.player.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

public object Record {
    private var recordConfig = RecordConfig()

    // Coroutine scope for managing the monitoring flow lifecycle
    private var monitorScope: CoroutineScope? = null

    // Job for managing the silence timeout
    private var silenceTimeoutJob: Job? = null

    // Callback-based approach instead of StateFlow
//    private var silenceTimeoutCallback: ((Boolean) -> Unit)? = null

    // You would fine-tune these values based on your specific use case
    // For 16-bit PCM audio, typical values range from 100-1000
    private val silenceThreshold = 1000.0 // RMS value threshold for silence
    private val silenceTimeoutMs = 5000L // 5 seconds timeout for silence

    /**
     * Set a callback to be notified when silence timeout occurs
     * @param callback Function that receives true when silence timeout occurs, false when audio returns
     */
    public fun setSilenceTimeoutCallback(callback: ((Boolean) -> Unit)?) {
//        silenceTimeoutCallback = callback
    }

    public fun startRecording() {
        // Cancel any existing monitoring scope
        monitorScope?.cancel()
        monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // Set up the audio data callback to handle silence detection
        RecordCore.setAudioDataCallback { audioData ->
            audioData?.let {
                val rms = calculateRms(audioData)
                println("RMS: $rms") // Debugging output
                val isSilent = rms < silenceThreshold

                // Handle silence timeout
                handleSilenceTimeout(isSilent)
            }
        }

        RecordCore.startRecording(recordConfig)
    }

    public fun stopRecording(): String {
        val result = RecordCore.stopRecording(recordConfig)
        // Clear the audio data callback and stop monitoring
        RecordCore.setAudioDataCallback(null)
        stopMonitoring()
        return result
    }

    public fun isRecording(): Boolean {
        return RecordCore.isRecording()
    }

    public fun setConfig(config: RecordConfig) {
        recordConfig = config
    }

    private fun handleSilenceTimeout(isSilent: Boolean) {
        if (isSilent) {
            // Start silence timeout if not already running
            if (silenceTimeoutJob == null || !silenceTimeoutJob!!.isActive) {
                silenceTimeoutJob = monitorScope?.launch {
                    try {
                        delay(silenceTimeoutMs)
                        // If we reach here, silence timeout has expired
                        if (isRecording()) {
                            println("Silence timeout reached - stopping recording automatically")
//                            silenceTimeoutCallback?.invoke(true)
                        }
                    } catch (e: Exception) {
                        // Timeout was cancelled (audio detected), which is normal
                    }
                }
            }
        } else {
            // Cancel silence timeout when audio is detected
            silenceTimeoutJob?.cancel()
            silenceTimeoutJob = null
//            silenceTimeoutCallback?.invoke(false) // Reset timeout notification
        }
    }

    /**
     * Stop monitoring and release resources
     */
    public fun stopMonitoring() {
        silenceTimeoutJob?.cancel()
        silenceTimeoutJob = null
//        silenceTimeoutCallback?.invoke(false) // Reset timeout notification
//        silenceTimeoutCallback = null
        monitorScope?.cancel()
        monitorScope = null
    }

    private fun calculateRms(audioData: ByteArray): Double {
        if (audioData.size < 2) return 0.0 // Not enough data for 16-bit samples

        // The soundDataFlow provides raw PCM audio data (16-bit samples)
        // regardless of the final output format (MPEG_4/AAC is applied during file encoding)
        val shortData = ShortArray(audioData.size / 2)

        // Convert bytes to 16-bit samples (little-endian format)
        for (i in shortData.indices) {
            val byteIndex = i * 2
            if (byteIndex + 1 < audioData.size) {
                // Little-endian: low byte first, then high byte
                val lowByte = audioData[byteIndex].toInt() and 0xFF
                val highByte = audioData[byteIndex + 1].toInt() and 0xFF
                shortData[i] = (highByte shl 8 or lowByte).toShort()
            }
        }

        // Calculate RMS (Root Mean Square)
        val sumOfSquares = shortData.sumOf { sample ->
            val normalizedSample = sample.toDouble()
            normalizedSample * normalizedSample
        }

        return sqrt(sumOfSquares / shortData.size)
    }

    public fun playRecording(filePath: String) {
        AudioPlayer.play(filePath)
    }

    public fun stopPlaying() {
        AudioPlayer.stop()
    }

    public fun isPlaying(): Boolean {
        return AudioPlayer.isPlaying()
    }
}

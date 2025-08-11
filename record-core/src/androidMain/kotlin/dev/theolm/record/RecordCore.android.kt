@file:Suppress("MatchingDeclarationName")

package dev.theolm.record

import android.Manifest.permission.RECORD_AUDIO
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.AudioFormat
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dev.theolm.record.config.OutputFormat
import dev.theolm.record.config.RecordConfig
import dev.theolm.record.error.NoOutputFileException
import dev.theolm.record.error.PermissionMissingException
import dev.theolm.record.error.RecordFailException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal actual object RecordCore {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var recordingThread: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recorder: MediaRecorder? = null
    private var output: String? = null
    @Volatile
    private var myRecordingState: RecordingState = RecordingState.IDLE

    // Replace StateFlow with callback
    private var audioDataCallback: ((ByteArray?) -> Unit)? = null

    actual fun setAudioDataCallback(callback: ((ByteArray?) -> Unit)?) {
        audioDataCallback = callback
    }

    @Throws(RecordFailException::class)
    internal actual fun startRecording(config: RecordConfig) {
        checkPermission()
        output = config.getOutput()
        File(output!!).parentFile?.mkdirs() //Ensure the output file path exists before recording starts

        // Always use AudioRecord for real-time audio data, regardless of output format
        val bufferSize = AudioRecord.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.apply {
            startRecording()
            myRecordingState = RecordingState.RECORDING

            recordingJob = recordingThread.launch {
                try {
                    when(config.outputFormat) {
                        OutputFormat.MPEG_4 -> {
                            // For MPEG_4, capture raw audio data and use MediaRecorder separately
                            captureAudioDataForMpeg4(bufferSize, config)
                        }
                        OutputFormat.WAV -> {
                            writeAudioDataToFile(bufferSize, config.sampleRate)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RecordCore", "Recording failed", e)
                }
            }
        }

        // For MPEG_4, also start MediaRecorder for file output
        if (config.outputFormat == OutputFormat.MPEG_4) {
            startMediaRecorderForMpeg4(config)
        }
    }

    private fun startMediaRecorderForMpeg4(config: RecordConfig) {
        recorder = createMediaRecorder(config)
        recorder?.apply {
            runCatching {
                prepare()
            }.onFailure {
                throw RecordFailException()
            }

            setOnErrorListener { _, _, _ ->
                stopRecording(config)
            }

            start()
        }
    }

    private fun captureAudioDataForMpeg4(bufferSize: Int, config: RecordConfig) {
        val data = ByteArray(bufferSize)

        // Just capture audio data for callback, MediaRecorder handles file writing
        while (isRecording()) {
            val read = audioRecord?.read(data, 0, data.size) ?: 0

            // Call callback with real-time audio data
            audioDataCallback?.invoke(data.copyOf(read))

            // Small delay to prevent excessive CPU usage
            Thread.sleep(10)
        }
    }

    @Throws(NoOutputFileException::class)
    internal actual fun stopRecording(config: RecordConfig): String {
        myRecordingState = RecordingState.IDLE

        // Stop AudioRecord (used for both formats for callback)
        recordingJob?.cancel()
        audioRecord?.apply {
            try {
                stop()
            } catch (e: Exception) {
                Log.e("RecordCore", "Error stopping AudioRecord", e)
            } finally {
                release()
            }
        }
        audioRecord = null

        // Clear audio data callback
        audioDataCallback?.invoke(null)

        // For MPEG_4, also stop MediaRecorder
        if (config.outputFormat == OutputFormat.MPEG_4) {
            recorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.e("RecordCore", "Error stopping MediaRecorder", e)
                } finally {
                    release()
                }
            }
            recorder = null
        }

        return output.also {
            output = null
        } ?: throw NoOutputFileException()
    }

    internal actual fun isRecording(): Boolean = myRecordingState == RecordingState.RECORDING

    private fun createMediaRecorder(config: RecordConfig) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(applicationContext)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(config.outputFormat.toMediaRecorderOutputFormat())
            setOutputFile(output)
            setAudioEncoder(config.audioEncoder.toMediaRecorderAudioEncoder())
        }

    private fun checkPermission() {
        if (
            ContextCompat.checkSelfPermission(
                applicationContext,
                RECORD_AUDIO
            ) != PERMISSION_GRANTED
        ) {
            throw PermissionMissingException()
        }
    }

    private fun writeAudioDataToFile(bufferSize: Int, sampleRate: Int) {
        val data = ByteArray(bufferSize)
        var totalAudioLength = 0

        FileOutputStream(output).use { fos ->
            // Write a placeholder for the WAV file header
            fos.write(ByteArray(44))

            // Write PCM data
            while (isRecording()) {
                val read = audioRecord?.read(data, 0, data.size) ?: 0

                // Call callback with audio data
                audioDataCallback?.invoke(data)

                if (read > 0) {
                    fos.write(data, 0, read)
                    totalAudioLength += read
                }
            }

            // Update WAV header after recording is done
            fos.channel.position(0) // Rewind to start of file
            fos.writeWavHeader(sampleRate, totalAudioLength + 36) // Data size + 36 bytes for header
        }
    }


    private fun calculateRms(audioData: ByteArray): Double {
        // Assumes 16-bit PCM audio (2 bytes per sample)
        val shortData = ShortArray(audioData.size / 2)
        // This conversion depends on the audio format and endianness
        for (i in shortData.indices) {
            shortData[i] = ((audioData[i * 2 + 1].toInt() shl 8) or (audioData[i * 2].toInt() and 0xFF)).toShort()
        }

        val sumOfSquares = shortData.sumOf { it.toDouble() * it.toDouble() }
        return sqrt(sumOfSquares / shortData.size)
    }
}
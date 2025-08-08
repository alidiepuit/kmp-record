@file:Suppress("MatchingDeclarationName")

package dev.theolm.record

import dev.theolm.record.config.RecordConfig
import dev.theolm.record.error.NoOutputFileException
import dev.theolm.record.error.PermissionMissingException
import dev.theolm.record.error.RecordFailException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioInputNode
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioQuality
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptions
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionUndetermined
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSURL.Companion.fileURLWithPath

internal actual object RecordCore {
    private var recorder: AVAudioRecorder? = null
    private var audioEngine: AVAudioEngine? = null
    private var inputNode: AVAudioInputNode? = null
    private var output: String? = null
    private var isRecording: Boolean = false

    // Replace StateFlow with callback
    private var audioDataCallback: ((ByteArray?) -> Unit)? = null

    actual fun setAudioDataCallback(callback: ((ByteArray?) -> Unit)?) {
        audioDataCallback = callback
    }

    @OptIn(ExperimentalForeignApi::class)
    @Throws(RecordFailException::class)
    internal actual fun startRecording(config: RecordConfig) {
        checkPermission()
        configureAudioSession()

        output = config.getOutput()

        // Setup AVAudioEngine for real-time audio data capture
        setupAudioEngine(config)

        // Setup AVAudioRecorder for file recording
        val settings = mapOf<Any?, Any>(
            AVFormatIDKey to config.outputFormat.toAVFormatID(),
            AVSampleRateKey to config.sampleRate,
            AVNumberOfChannelsKey to 1, // Mono. Stereo is not supported for now.
            AVLinearPCMBitDepthKey to 16,
            AVLinearPCMIsFloatKey to false,
            AVEncoderAudioQualityKey to AVAudioQuality.MAX_VALUE
        )

        val url = fileURLWithPath(output!!)
        recorder = AVAudioRecorder(
            url,
            settings,
            null
        )

        recorder?.let {
            if (!it.prepareToRecord()) {
                throw RecordFailException()
            }
            if (!it.record()) {
                throw RecordFailException()
            }
        } ?: throw RecordFailException()

        // Start audio engine
        audioEngine?.let { engine ->
            try {
                engine.startAndReturnError(null)
                isRecording = true
            } catch (e: Exception) {
                throw RecordFailException()
            }
        } ?: throw RecordFailException()
    }

    internal actual fun stopRecording(config: RecordConfig): String {
        isRecording = false

        // Stop audio engine
        audioEngine?.stop()
        audioEngine = null
        inputNode = null

        // Stop recorder
        recorder?.stop()

        // Clear audio data callback
        audioDataCallback?.invoke(null)

        return output.also {
            output = null
            recorder = null
        } ?: throw NoOutputFileException()
    }

    internal actual fun isRecording(): Boolean = isRecording

    @OptIn(ExperimentalForeignApi::class)
    private fun setupAudioEngine(config: RecordConfig) {
        audioEngine = AVAudioEngine()
        inputNode = audioEngine?.inputNode

        inputNode?.let { input ->
            val inputFormat = input.outputFormatForBus(0u)

            // Install tap to capture audio data
            input.installTapOnBus(0u, 1024u, inputFormat) { buffer, _ ->
                buffer?.let { audioBuffer ->
                    extractAudioData(audioBuffer)
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun extractAudioData(buffer: AVAudioPCMBuffer) {
        val frameLength = buffer.frameLength.toInt()
        val channelCount = buffer.format.channelCount.toInt()

        if (frameLength > 0 && channelCount > 0) {
            // Extract audio data as ByteArray
            val audioData = ByteArray(frameLength * channelCount * 2) // 16-bit = 2 bytes per sample

            // Convert audio buffer to ByteArray
            buffer.floatChannelData?.let { channelDataPtr ->
                // Get the first channel data pointer using proper cinterop syntax
                val firstChannelPtr = channelDataPtr[0]

                if (firstChannelPtr != null) {
                    for (i in 0 until frameLength) {
                        // Access float value using array index notation
                        val floatValue = firstChannelPtr[i]

                        // Ensure it's treated as a Float and convert to 16-bit signed integer
                        val floatSample = floatValue
                        val sample = (floatSample * Short.MAX_VALUE.toFloat()).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

                        val byteIndex = i * 2
                        if (byteIndex + 1 < audioData.size) {
                            // Store as little-endian (low byte first, then high byte)
                            audioData[byteIndex] = (sample.toInt() and 0xFF).toByte()
                            audioData[byteIndex + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }
                    }
                }
            }

            // Call callback with real-time audio data
            audioDataCallback?.invoke(audioData)
        }
    }

    /**
     * Config and Activate AVAudioSession
     */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun configureAudioSession() {
        memScoped {
            val audioSession = AVAudioSession.sharedInstance()
            val categoryErrorPtr = alloc<ObjCObjectVar<NSError?>>()
            audioSession.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                withOptions = AVAudioSessionCategoryOptions.MAX_VALUE,
                error = categoryErrorPtr.ptr
            )
            val categoryError = categoryErrorPtr.value
            if (categoryError != null) {
                println("Failed to set AVAudioSession category: ${categoryError.localizedDescription}")
                throw RecordFailException()
            }

            val activateErrorPtr = alloc<ObjCObjectVar<NSError?>>()
            audioSession.setActive(true, error = activateErrorPtr.ptr)
            val activateError = activateErrorPtr.value
            if (activateError != null) {
                println("Failed to activate AVAudioSession: ${activateError.localizedDescription}")
                throw RecordFailException()
            }
        }
    }

    private fun checkPermission() {
        val audioSession = AVAudioSession.sharedInstance()
        when (audioSession.recordPermission()) {
            AVAudioSessionRecordPermissionDenied -> {
                throw PermissionMissingException()
            }

            AVAudioSessionRecordPermissionUndetermined -> {
                // Permission has not been asked yet; requesting permission
                audioSession.requestRecordPermission { granted ->
                    if (!granted) {
                        throw PermissionMissingException()
                    }
                }
            }
        }
    }
}
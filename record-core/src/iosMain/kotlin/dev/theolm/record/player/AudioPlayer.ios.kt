@file:Suppress("MatchingDeclarationName")

package dev.theolm.record.player

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSError
import platform.Foundation.NSURL.Companion.fileURLWithPath

internal actual object AudioPlayer {
    private var audioPlayer: AVAudioPlayer? = null
    private var currentlyPlaying = false

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual fun play(filePath: String) {
        try {
            stop() // Stop any currently playing audio

            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                val url = fileURLWithPath(filePath)

                audioPlayer = AVAudioPlayer(url, error = errorPtr.ptr)
                val error = errorPtr.value

                if (error != null) {
                    println("Error creating AVAudioPlayer: ${error.localizedDescription}")
                    return
                }

                audioPlayer?.let { player ->
                    player.prepareToPlay()
                    if (player.play()) {
                        currentlyPlaying = true
                        println("Started playing: $filePath")
                    } else {
                        println("Failed to start playing")
                        currentlyPlaying = false
                    }
                }
            }
        } catch (e: Exception) {
            println("Error playing audio: ${e.message}")
            currentlyPlaying = false
        }
    }

    actual fun stop() {
        audioPlayer?.let { player ->
            try {
                player.stop()
            } catch (e: Exception) {
                println("Error stopping audio: ${e.message}")
            }
        }
        audioPlayer = null
        currentlyPlaying = false
    }

    actual fun isPlaying(): Boolean = currentlyPlaying
}

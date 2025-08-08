@file:Suppress("MatchingDeclarationName")

package dev.theolm.record.player

import android.media.MediaPlayer
import android.util.Log
import java.io.File

internal actual object AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlaying = false

    actual fun play(filePath: String) {
        try {
            stop() // Stop any currently playing audio

            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    currentlyPlaying = false
                    release()
                    mediaPlayer = null
                }
                setOnErrorListener { _, _, _ ->
                    currentlyPlaying = false
                    release()
                    mediaPlayer = null
                    false
                }
                start()
                currentlyPlaying = true
            }
            Log.d("AudioPlayer", "Started playing: $filePath")
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing audio: ${e.message}")
            currentlyPlaying = false
        }
    }

    actual fun stop() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error stopping audio: ${e.message}")
            }
        }
        mediaPlayer = null
        currentlyPlaying = false
    }

    actual fun isPlaying(): Boolean = currentlyPlaying
}

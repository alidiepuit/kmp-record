package dev.theolm.record.player

internal expect object AudioPlayer {
    fun play(filePath: String)
    fun stop()
    fun isPlaying(): Boolean
}

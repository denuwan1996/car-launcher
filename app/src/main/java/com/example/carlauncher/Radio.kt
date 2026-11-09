package com.example.carlauncher
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/** Internet radio. Replace URLs or load from radio-browser.info (https://de1.api.radio-browser.info/json/stations/bycountry/sri%20lanka). */
class Radio(ctx: Context) {
    private val player = ExoPlayer.Builder(ctx).build()
    val stations = listOf("Sample FM" to "https://example.com/stream.mp3")
    fun play(i: Int) { player.setMediaItem(MediaItem.fromUri(stations[i].second)); player.prepare(); player.play() }
    fun stop() = player.stop()
    fun release() = player.release()
}

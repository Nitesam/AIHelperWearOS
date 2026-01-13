package com.base.aihelperwearos.presentation.utils

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File

class AudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlayingFlag = false

    /**
     * Plays the specified audio file and notifies when playback completes.
     *
     * @param audioFile audio file to play.
     * @param onComplete callback invoked after playback finishes.
     * @return `Unit` after playback starts or fails.
     */
    fun playAudio(audioFile: File, onComplete: () -> Unit = {}) {
        try {
            stopAudio()

            if (!audioFile.exists()) {
                Log.e("AudioPlayer", "File non esiste: ${audioFile.absolutePath}")
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    isPlayingFlag = false
                    onComplete()
                    Log.d("AudioPlayer", "player finished")
                }
                start()
            }

            isPlayingFlag = true
            Log.d("AudioPlayer", "player started: ${audioFile.name}")

        } catch (e: Exception) {
            Log.e("AudioPlayer", "error during registration of audio", e)
            isPlayingFlag = false
        }
    }

    /**
     * Stops current playback and releases the media player.
     *
     * @return `Unit` after playback is stopped.
     */
    fun stopAudio() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            isPlayingFlag = false
            Log.d("AudioPlayer", "player ended")
        } catch (e: Exception) {
            Log.e("AudioPlayer", "audio error on stop", e)
        }
    }

    /**
     * Reports whether audio is currently playing.
     *
     * @return `Boolean` indicating playback state.
     */
    fun isPlaying() = isPlayingFlag

    /**
     * Releases player resources and stops any active playback.
     *
     * @return `Unit` after cleanup.
     */
    fun release() {
        stopAudio()
    }
}

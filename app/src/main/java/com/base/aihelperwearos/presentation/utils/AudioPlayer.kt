package com.base.aihelperwearos.presentation.utils

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File

class AudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlayingFlag = false

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
                    Log.d("AudioPlayer", "Riproduzione completata")
                }
                start()
            }

            isPlayingFlag = true
            Log.d("AudioPlayer", "Riproduzione avviata: ${audioFile.name}")

        } catch (e: Exception) {
            Log.e("AudioPlayer", "Errore riproduzione audio", e)
            isPlayingFlag = false
        }
    }

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
            Log.d("AudioPlayer", "Riproduzione fermata")
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Errore stop audio", e)
        }
    }

    fun isPlaying() = isPlayingFlag

    fun release() {
        stopAudio()
    }
}


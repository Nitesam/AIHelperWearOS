package com.base.aihelperwearos.presentation.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.util.*

class TextToSpeechHelper(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.ITALIAN)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported")
                } else {
                    isInitialized = true
                    Log.d("TTS", "TTS initialized successfully")
                }
            } else {
                Log.e("TTS", "TTS initialization failed")
            }
        }
    }

    fun synthesizeToFile(
        text: String,
        onSuccess: (File) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (!isInitialized) {
            onFailure(Exception("TTS non inizializzato"))
            return
        }

        if (text.isBlank()) {
            onFailure(Exception("Testo vuoto"))
            return
        }

        try {
            val audioDir = File(context.filesDir, "audio_messages")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }

            val timestamp = System.currentTimeMillis()
            val audioFile = File(audioDir, "tts_$timestamp.wav")

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("TTS", "Synthesis started")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("TTS", "Synthesis completed: ${audioFile.absolutePath}")
                    if (audioFile.exists()) {
                        onSuccess(audioFile)
                    } else {
                        onFailure(Exception("File audio non creato"))
                    }
                }

                override fun onError(utteranceId: String?) {
                    Log.e("TTS", "Synthesis error")
                    onFailure(Exception("Errore sintesi vocale"))
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e("TTS", "Synthesis error: $errorCode")
                    onFailure(Exception("Errore sintesi vocale: $errorCode"))
                }
            })

            val params = HashMap<String, String>()
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "tts_$timestamp"

            @Suppress("DEPRECATION")
            val result = tts?.synthesizeToFile(text, params, audioFile.absolutePath)

            if (result != TextToSpeech.SUCCESS) {
                onFailure(Exception("Sintesi fallita"))
            }

        } catch (e: Exception) {
            Log.e("TTS", "Error in synthesizeToFile", e)
            onFailure(e)
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}


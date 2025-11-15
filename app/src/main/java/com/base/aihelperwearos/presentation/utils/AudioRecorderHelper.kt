package com.base.aihelperwearos.presentation.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorderHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false

    fun startRecording(): File {
        try {
            val timestamp = System.currentTimeMillis()
            audioFile = File(context.cacheDir, "math_$timestamp.m4a")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile!!.absolutePath)

                prepare()
                start()
            }

            isRecording = true
            Log.d("AudioRecorder", "Recording started: ${audioFile!!.absolutePath}")

            return audioFile!!

        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error starting recording", e)
            cleanup()
            throw e
        }
    }

    fun stopRecording(): File {
        try {
            if (!isRecording) {
                throw IllegalStateException("Not recording")
            }

            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val file = audioFile ?: throw IllegalStateException("No audio file")

            Log.d("AudioRecorder", "Recording stopped: ${file.absolutePath}, size: ${file.length()} bytes")

            return file

        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error stopping recording", e)
            cleanup()
            throw e
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    fun cancelRecording() {
        try {
            if (isRecording) {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error cancelling", e)
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        mediaRecorder?.release()
        mediaRecorder = null
        audioFile?.delete()
        audioFile = null
        isRecording = false
    }

    fun isRecording() = isRecording
}


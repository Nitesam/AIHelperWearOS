package com.base.aihelperwearos.presentation.utils

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    private val sampleRate = 96000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    @SuppressLint("MissingPermission")
    suspend fun startRecording(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val audioDir = File(context.filesDir, "audio_messages")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }

            audioFile = File(audioDir, "voice_${System.currentTimeMillis()}.wav")

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext Result.failure(Exception("AudioRecord non inizializzato"))
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread {
                writeAudioDataToFile()
            }
            recordingThread?.start()

            Log.d("AudioRecorder", "Recording started: ${audioFile!!.absolutePath} (WAV format)")
            Result.success("Registrazione WAV avviata")

        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error starting recording", e)
            cleanup()
            Result.failure(e)
        }
    }

    suspend fun stopRecording(): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!isRecording) {
                return@withContext Result.failure(Exception("Non in registrazione"))
            }

            isRecording = false
            recordingThread?.join(1000)

            audioRecord?.apply {
                stop()
                release()
            }
            audioRecord = null

            val file = audioFile ?: return@withContext Result.failure(Exception("File non trovato"))

            if (!file.exists() || file.length() == 0L) {
                return@withContext Result.failure(Exception("File audio vuoto"))
            }

            Log.d("AudioRecorder", "Recording stopped: ${file.absolutePath}, size: ${file.length()} bytes (WAV)")
            Result.success(file)

        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error stopping recording", e)
            cleanup()
            Result.failure(e)
        }
    }

    private fun writeAudioDataToFile() {
        val data = ByteArray(bufferSize)
        var outputStream: FileOutputStream? = null

        try {
            outputStream = FileOutputStream(audioFile)
            writeWavHeader(outputStream, 0, sampleRate, 1, 16)

            var totalBytesWritten = 0L

            while (isRecording) {
                val bytesRead = audioRecord?.read(data, 0, bufferSize) ?: 0

                if (bytesRead > 0) {
                    outputStream.write(data, 0, bytesRead)
                    totalBytesWritten += bytesRead
                }
            }

            outputStream.close()
            updateWavHeader(audioFile!!, totalBytesWritten)

            Log.d("AudioRecorder", "WAV file written: $totalBytesWritten bytes PCM data")

        } catch (e: IOException) {
            Log.e("AudioRecorder", "Error writing audio data", e)
        } finally {
            try {
                outputStream?.close()
            } catch (e: IOException) {
                Log.e("AudioRecorder", "Error closing stream", e)
            }
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        dataSize: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        header.put("RIFF".toByteArray())
        header.putInt((36 + dataSize).toInt())
        header.put("WAVE".toByteArray())

        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(bitsPerSample.toShort())

        header.put("data".toByteArray())
        header.putInt(dataSize.toInt())

        out.write(header.array())
    }

    private fun updateWavHeader(file: File, dataSize: Long) {
        try {
            val raf = java.io.RandomAccessFile(file, "rw")

            raf.seek(4)
            raf.write(intToByteArray((36 + dataSize).toInt()))

            raf.seek(40)
            raf.write(intToByteArray(dataSize.toInt()))

            raf.close()
            Log.d("AudioRecorder", "WAV header updated: dataSize=$dataSize")
        } catch (e: IOException) {
            Log.e("AudioRecorder", "Error updating WAV header", e)
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    fun cancelRecording() {
        try {
            if (isRecording) {
                isRecording = false
                recordingThread?.join(500)

                audioRecord?.apply {
                    stop()
                    release()
                }
                audioRecord = null
            }
            cleanup()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error cancelling recording", e)
        }
    }

    private fun cleanup() {
        audioFile?.delete()
        audioFile = null
    }

    fun isRecording() = isRecording
}
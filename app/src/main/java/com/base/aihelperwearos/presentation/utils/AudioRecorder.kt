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

    /**
     * Starts capturing microphone audio and writes PCM data to a WAV file.
     *
     * @return `Result<String>` with a status message or failure.
     */
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

    /**
     * Stops recording and finalizes the WAV file on disk.
     *
     * @return `Result<File>` containing the recorded file or failure.
     */
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

    /**
     * Streams PCM frames from the recorder and writes them into the output file.
     *
     * @return `Unit` after recording loop ends and header is updated.
     */
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

    /**
     * Writes a WAV header to the output stream.
     *
     * @param out output stream to write the header into.
     * @param dataSize size of PCM data in bytes.
     * @param sampleRate sample rate in Hz.
     * @param channels number of audio channels.
     * @param bitsPerSample bit depth of each sample.
     * @return `Unit` after header is written.
     */
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

    /**
     * Updates the WAV header with the final data size.
     *
     * @param file WAV file to update.
     * @param dataSize size of PCM data in bytes.
     * @return `Unit` after header update completes.
     */
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

    /**
     * Converts an integer to a little-endian byte array.
     *
     * @param value integer value to convert.
     * @return little-endian `ByteArray`.
     */
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    /**
     * Cancels any active recording and cleans up resources.
     *
     * @return `Unit` after recording is stopped and files are cleared.
     */
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

    /**
     * Removes the temporary audio file and resets internal state.
     *
     * @return `Unit` after cleanup.
     */
    private fun cleanup() {
        audioFile?.delete()
        audioFile = null
    }

    /**
     * Reports whether recording is currently active.
     *
     * @return `Boolean` indicating recording state.
     */
    fun isRecording() = isRecording
}

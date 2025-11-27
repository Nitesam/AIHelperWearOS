package com.base.aihelperwearos.presentation.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.base.aihelperwearos.R
import com.base.aihelperwearos.presentation.utils.AudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class AudioRecordingService : Service() {

    private val binder = LocalBinder()
    private var audioRecorder: AudioRecorder? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var recordingCallback: ((Result<File>) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): AudioRecordingService = this@AudioRecordingService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioRecorder(applicationContext)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "AudioRecordingService::WakeLock"
        )

        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startForegroundRecording()
            ACTION_STOP_RECORDING -> stopRecordingAndService()
        }
        return START_STICKY
    }

    private fun startForegroundRecording() {
        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)

            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(5 * 60 * 1000L) // 5 minutes max
            }

            serviceScope.launch {
                val result = audioRecorder?.startRecording()
                Log.d(TAG, "Recording started: $result")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground recording", e)
            stopSelf()
        }
    }

    fun startRecording(callback: (Result<File>) -> Unit) {
        recordingCallback = callback
        val intent = Intent(this, AudioRecordingService::class.java).apply {
            action = ACTION_START_RECORDING
        }
        startService(intent)
    }

    fun stopRecording() {
        serviceScope.launch {
            try {
                val result = audioRecorder?.stopRecording()
                recordingCallback?.invoke(result ?: Result.failure(Exception("No result")))
                stopRecordingAndService()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording", e)
                recordingCallback?.invoke(Result.failure(e))
            }
        }
    }

    private fun stopRecordingAndService() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()

            Log.d(TAG, "Service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service", e)
        }
    }

    fun isRecording(): Boolean {
        return audioRecorder?.isRecording() ?: false
    }

    private fun createNotification(): Notification {
        createNotificationChannel()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Registrazione in corso")
            .setContentText("Registrazione audio attiva")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Registrazione Audio",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifiche per la registrazione audio"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up
        serviceScope.launch {
            audioRecorder?.cancelRecording()
        }

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        wakeLock = null
        audioRecorder = null
        recordingCallback = null

        Log.d(TAG, "Service destroyed")
    }

    companion object {
        private const val TAG = "AudioRecordingService"
        private const val CHANNEL_ID = "audio_recording_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_RECORDING = "com.base.aihelperwearos.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.base.aihelperwearos.STOP_RECORDING"
    }
}
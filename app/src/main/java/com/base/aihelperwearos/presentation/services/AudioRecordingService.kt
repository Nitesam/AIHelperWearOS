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
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var recordingCallback: ((Result<File>) -> Unit)? = null

    inner class LocalBinder : Binder() {
        /**
         * Exposes the service instance to bound clients.
         *
         * @return `AudioRecordingService` instance.
         */
        fun getService(): AudioRecordingService = this@AudioRecordingService
    }

    /**
     * Returns the binder for clients that bind to the service.
     *
     * @param intent binding intent.
     * @return `IBinder` for the local service.
     */
    override fun onBind(intent: Intent): IBinder = binder

    /**
     * Initializes the recorder and wake lock when the service is created.
     *
     * @return `Unit` after initialization.
     */
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

    /**
     * Handles start commands to begin or stop foreground recording.
     *
     * @param intent command intent with action.
     * @param flags start flags supplied by the system.
     * @param startId unique start request id.
     * @return `Int` start mode for the service.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startForegroundRecording()
            ACTION_STOP_RECORDING -> stopRecordingAndService()
        }
        return START_STICKY
    }

    /**
     * Starts the service in the foreground and begins recording.
     *
     * @return `Unit` after recording has been requested.
     */
    private fun startForegroundRecording() {
        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)

            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(5 * 60 * 1000L)
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

    /**
     * Starts recording and returns the result via callback.
     *
     * @param callback callback receiving the recording result.
     * @return `Unit` after start is requested.
     */
    fun startRecording(callback: (Result<File>) -> Unit) {
        recordingCallback = callback
        val intent = Intent(this, AudioRecordingService::class.java).apply {
            action = ACTION_START_RECORDING
        }
        startService(intent)
    }

    /**
     * Stops recording and delivers the result to the callback.
     *
     * @return `Unit` after stop is initiated.
     */
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

    /**
     * Stops foreground execution and releases wake locks.
     *
     * @return `Unit` after service shutdown is requested.
     */
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

    /**
     * Reports whether the recorder is actively recording.
     *
     * @return `Boolean` indicating recording state.
     */
    fun isRecording(): Boolean {
        return audioRecorder?.isRecording() ?: false
    }

    /**
     * Builds the foreground notification for audio recording.
     *
     * @return `Notification` used for foreground service.
     */
    private fun createNotification(): Notification {
        createNotificationChannel()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Registrazione in corso")
            .setContentText("Registrazione audio attiva")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    /**
     * Creates the notification channel for recording status.
     *
     * @return `Unit` after the channel is registered.
     */
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

    /**
     * Cleans up recording resources when the service is destroyed.
     *
     * @return `Unit` after cleanup is scheduled.
     */
    override fun onDestroy() {
        super.onDestroy()

        // Cancel all coroutines
        serviceJob.cancel()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        wakeLock = null
        audioRecorder = null
        recordingCallback = null
    }

    companion object {
        private const val TAG = "AudioRecordingService"
        private const val CHANNEL_ID = "audio_recording_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_RECORDING = "com.base.aihelperwearos.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.base.aihelperwearos.STOP_RECORDING"
    }
}

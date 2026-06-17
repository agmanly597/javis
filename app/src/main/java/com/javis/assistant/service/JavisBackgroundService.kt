package com.javis.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.javis.assistant.MainActivity
import com.javis.assistant.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Foreground service that keeps JAVIS alive in the background.
 * Shows a persistent notification with a one-tap "Speak to JAVIS" button —
 * this is how the user activates JAVIS from outside the app without overlay permission.
 */
@AndroidEntryPoint
class JavisBackgroundService : Service() {

    companion object {
        const val CHANNEL_ID = "javis_persistent"
        const val NOTIF_ID = 1001
        const val ACTION_ACTIVATE = "com.javis.assistant.ACTION_ACTIVATE_VOICE"
        const val ACTION_STOP = "com.javis.assistant.ACTION_STOP_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, JavisBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, JavisBackgroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACTIVATE -> {
                // Bring JAVIS to foreground with voice-active flag
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    action = ACTION_ACTIVATE
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(mainIntent)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JAVIS Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "JAVIS is running in the background"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val activatePi = PendingIntent.getService(
            this, 0,
            Intent(this, JavisBackgroundService::class.java).apply { action = ACTION_ACTIVATE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, JavisBackgroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JAVIS is standing by")
            .setContentText("Tap the mic to speak")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_btn_speak_now,
                "🎤 Speak to JAVIS",
                activatePi
            )
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPi
            )
            .build()
    }
}

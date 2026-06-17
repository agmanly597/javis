package com.javis.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.javis.assistant.notifications.NotificationPersistenceWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class JavisApplication : Application() {

    // Eagerly injected so it starts listening for notifications at app launch
    @Inject
    lateinit var notificationPersistenceWorker: NotificationPersistenceWorker

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val voiceChannel = NotificationChannel(
                CHANNEL_VOICE,
                getString(R.string.notification_channel_voice),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_voice_desc)
                setSound(null, null)
            }
            manager.createNotificationChannel(voiceChannel)
        }
    }

    companion object {
        const val CHANNEL_VOICE = "javis_voice"
    }
}

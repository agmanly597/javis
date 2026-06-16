package com.javis.assistant.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javis.assistant.domain.model.NotificationItem
import com.javis.assistant.domain.repository.NotificationRepository
import com.javis.assistant.voice.VoiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val voiceManager: VoiceManager
) : ViewModel() {

    val notifications = notificationRepository.getNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markAsRead(notification: NotificationItem) = viewModelScope.launch {
        notificationRepository.markAsRead(notification.id)
    }

    fun clearAll() = viewModelScope.launch {
        notificationRepository.clearAll()
    }

    fun readAloud(notification: NotificationItem) {
        val text = "From ${notification.appName}: ${notification.title}. ${notification.text}"
        voiceManager.speak(text)
        markAsRead(notification)
    }

    fun readAllUnread() {
        val unread = notifications.value.filter { !it.isRead }
        if (unread.isEmpty()) {
            voiceManager.speak("You have no unread notifications.")
            return
        }
        val summary = buildString {
            append("You have ${unread.size} unread notification${if (unread.size > 1) "s" else ""}. ")
            unread.take(5).forEach { notif ->
                append("${notif.appName}: ${notif.title}. ")
            }
        }
        voiceManager.speak(summary)
        unread.forEach { notif ->
            viewModelScope.launch { notificationRepository.markAsRead(notif.id) }
        }
    }
}

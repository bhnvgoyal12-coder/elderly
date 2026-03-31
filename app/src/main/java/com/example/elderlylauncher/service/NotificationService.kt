package com.example.elderlylauncher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Service that listens for notifications to show a badge indicator.
 * Requires user to grant Notification Access permission in Settings.
 */
class NotificationService : NotificationListenerService() {

    companion object {
        private var instance: NotificationService? = null
        private var notificationCount = 0
        private var listener: NotificationCountListener? = null

        fun getNotificationCount(): Int = notificationCount

        fun setListener(l: NotificationCountListener?) {
            listener = l
            // Immediately notify with current count
            l?.onNotificationCountChanged(notificationCount)
        }

        fun isServiceConnected(): Boolean = instance != null
    }

    interface NotificationCountListener {
        fun onNotificationCountChanged(count: Int)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        updateNotificationCount()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        notificationCount = 0
        listener?.onNotificationCountChanged(0)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        updateNotificationCount()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateNotificationCount()
    }

    private fun updateNotificationCount() {
        try {
            val notifications = activeNotifications
            // Filter out ongoing/persistent notifications (like music players)
            notificationCount = notifications?.count {
                !it.isOngoing && it.notification.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE == 0
            } ?: 0
            listener?.onNotificationCountChanged(notificationCount)
        } catch (e: Exception) {
            notificationCount = 0
        }
    }
}

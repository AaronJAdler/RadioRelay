package com.radiorelay

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/** Android calls this for every notification once "Notification access" is granted. */
class RelayNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        try {
            val snapshot = NotificationReader.read(n, sbn.postTime) ?: return
            Relay.onChatMessages(this, snapshot, sbn.packageName)
        } catch (e: Exception) {
            Log.w("RadioRelay", "Couldn't handle notification from ${sbn.packageName}", e)
        }
    }
}

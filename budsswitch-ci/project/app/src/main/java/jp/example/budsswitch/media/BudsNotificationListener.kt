package jp.example.budsswitch.media

import android.service.notification.NotificationListenerService

/**
 * Enabling this service grants the app permission to query active MediaSessions.
 * No notification contents are stored or transmitted.
 */
class BudsNotificationListener : NotificationListenerService()

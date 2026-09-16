package dev.midroid.app.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.midroid.app.MainActivity
import dev.midroid.app.R
import dev.midroid.app.config.InstanceConfig

object PushNotifications {
    const val CHANNEL_MENTIONS = "midroid_push_mentions"
    const val CHANNEL_GENERAL = "midroid_push_general"
    const val REQUEST_OPEN_PUSH = "open_push"

    fun needsRuntimePermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun hasPermission(context: Context): Boolean {
        if (!needsRuntimePermission()) return true
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MENTIONS,
                context.getString(R.string.push_channel_mentions),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.push_channel_mentions_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GENERAL,
                context.getString(R.string.push_channel_general),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.push_channel_general_description) },
        )
    }

    @SuppressLint("MissingPermission")
    fun showDecrypted(
        context: Context,
        accountId: String,
        instanceOrigin: String,
        authenticatedPlaintext: ByteArray,
        notificationId: Int = accountId.hashCode(),
    ): Boolean {
        if (!hasPermission(context)) return false
        val instance = InstanceConfig.parse(instanceOrigin).getOrNull() ?: return false
        val fallbackTitle = context.getString(R.string.push_notification_title, instanceOrigin)
        val content = PushNotificationRenderer.render(
            authenticatedPlaintext,
            fallbackTitle,
            instance.origin,
        )
        val targetUrl = content.targetUrl?.takeIf(instance::owns) ?: instance.origin
        val channelId = if (looksLikeMention(content)) CHANNEL_MENTIONS else CHANNEL_GENERAL

        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = REQUEST_OPEN_PUSH
            data = Uri.parse(targetUrl)
            putExtra(EXTRA_ACCOUNT_ID, accountId)
            putExtra(EXTRA_TARGET_URL, targetUrl)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(content.body)
                    .setBigContentTitle(content.title),
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }

    private fun looksLikeMention(content: PushNotificationContent): Boolean {
        val haystack = "${content.title} ${content.body.orEmpty()}"
        return haystack.contains('@') || haystack.contains("mention", ignoreCase = true)
    }

    const val EXTRA_ACCOUNT_ID = "dev.midroid.app.push.ACCOUNT_ID"
    const val EXTRA_TARGET_URL = "dev.midroid.app.push.TARGET_URL"
}

package com.clarion.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.clarion.app.FlareAlertActivity

/**
 * Posts the ringing, full-screen alert that is the whole point of Clarion — this must wake and
 * take over the screen even when the app is backgrounded or the phone is locked.
 */
object NotificationHelper {
    const val CHANNEL_ID = "flare_alerts"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Flare alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Rings when someone nearby sends a Flare"
            enableVibration(true)
            setBypassDnd(true)
            setSound(ringtoneUri, audioAttributes)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Fires a Flare alert with the sender's real location (so the map opens centered on the
     * incident, not the viewer's own position) and their avatar, if they have one set.
     */
    fun postFlareAlert(
        context: Context,
        senderName: String,
        distance: String,
        location: String,
        flareLat: Double,
        flareLng: Double,
        senderAvatarUrl: String?,
    ) {
        ensureChannel(context)

        val fullScreenIntent = Intent(context, FlareAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(FlareAlertActivity.EXTRA_SENDER, senderName)
            putExtra(FlareAlertActivity.EXTRA_DISTANCE, distance)
            putExtra(FlareAlertActivity.EXTRA_LOCATION, location)
            putExtra(FlareAlertActivity.EXTRA_LAT, flareLat)
            putExtra(FlareAlertActivity.EXTRA_LNG, flareLng)
            putExtra(FlareAlertActivity.EXTRA_AVATAR_URL, senderAvatarUrl)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("$senderName sent a Flare")
            .setContentText("$distance away · $location")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .build()
            .apply {
                // Keep ringing/vibrating until the user responds or dismisses it, like an
                // actual incoming call — not just a single notification chime.
                flags = flags or Notification.FLAG_INSISTENT
            }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }
}

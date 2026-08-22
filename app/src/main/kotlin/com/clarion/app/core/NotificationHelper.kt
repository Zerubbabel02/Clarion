package com.clarion.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.clarion.app.FlareAlertActivity

/**
 * Posts the ringing, full-screen alert that is the whole point of Clarion — this must wake and
 * take over the screen even when the app is backgrounded or the phone is locked.
 */
object NotificationHelper {
    const val CHANNEL_ID = "flare_alerts"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Flare alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Rings when someone nearby sends a Flare"
            enableVibration(true)
            setBypassDnd(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Fires a Flare alert. [senderName], [distance] and [location] are sample data today — once
     * the backend is wired up, this same function is what a push message handler will call with
     * real incident data.
     */
    fun postFlareAlert(context: Context, senderName: String, distance: String, location: String) {
        ensureChannel(context)

        val fullScreenIntent = Intent(context, FlareAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(FlareAlertActivity.EXTRA_SENDER, senderName)
            putExtra(FlareAlertActivity.EXTRA_DISTANCE, distance)
            putExtra(FlareAlertActivity.EXTRA_LOCATION, location)
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

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }
}

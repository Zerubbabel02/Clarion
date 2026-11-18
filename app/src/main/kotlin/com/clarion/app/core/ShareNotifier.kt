package com.clarion.app.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.clarion.app.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * "X started sharing their location with you": a normal notification (not the ringing Flare
 * alert). Tapping it opens Clarion on the Map tab, zoomed onto that person. Posted from the push
 * (app closed) and from the in-app check (app open) — the same id per person, so never twice.
 */
object ShareNotifier {
    private const val CHANNEL_ID = "clarion_shares"
    const val EXTRA_FOCUS_SHARER = "extra_focus_sharer"

    /** Someone to zoom onto in the Map tab, set when a share notification is tapped. */
    val pendingFocus = MutableStateFlow<String?>(null)

    fun takeFocus(intent: Intent?) {
        intent?.getStringExtra(EXTRA_FOCUS_SHARER)?.let { pendingFocus.value = it }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Location sharing", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When a friend starts sharing their live location with you"
                },
            )
        }
    }

    fun postShareStarted(context: Context, ownerId: String, ownerName: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) return
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_FOCUS_SHARER, ownerId)
        }
        val pi = PendingIntent.getActivity(context, ownerId.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val first = ownerName.trim().split(Regex("\\s+")).firstOrNull().orEmpty().ifEmpty { "A friend" }
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("$first is sharing their location with you")
            .setContentText("Tap to see them on the map")
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try { NotificationManagerCompat.from(context).notify("share-$ownerId".hashCode(), n) } catch (_: SecurityException) {}
    }
}

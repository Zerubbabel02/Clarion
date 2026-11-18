package com.clarion.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/**
 * Stops an unanswered Flare alert from ringing forever. Scheduled by
 * NotificationHelper.postFlareAlert and canceled the moment the user actually responds
 * (FlareAlertActivity's Open Map / Dismiss) — this only ever fires if neither happened.
 */
class FlareAlertTimeoutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancel(NotificationHelper.NOTIFICATION_ID)
        NotificationHelper.clearRingState(context)
    }
}

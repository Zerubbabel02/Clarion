package com.clarion.app.core

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.clarion.app.FlareAlertActivity

/**
 * Posts the ringing, full-screen alert that is the whole point of Clarion — this must wake and
 * take over the screen even when the app is backgrounded or the phone is locked.
 */
object NotificationHelper {
    const val NOTIFICATION_ID = 1001
    private const val CHANNEL_PREFIX = "flare_alerts_"

    // A call rings for a while and then gives up if unanswered — an unattended Flare alert
    // should behave the same way rather than ringing indefinitely. Canceled the instant the
    // user actually responds (FlareAlertActivity); this alarm is the fallback for when they
    // don't.
    private const val RING_DURATION_MS = 5 * 60 * 1000L

    private fun resolveSoundUri(context: Context): android.net.Uri {
        val stored = AlertSoundPrefs.get(context)
        if (stored != null) return android.net.Uri.parse(stored)
        return RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    /**
     * A channel's sound is frozen forever once created — Android's documented workaround is
     * to delete and recreate the same id, but that proved unreliable on a real test device
     * (this OEM's build kept serving the old sound after a delete+recreate of "flare_alerts_v2",
     * confirmed live: ensureChannel correctly detected the mismatch and rebuilt, yet
     * dumpsys still reported the stale sound immediately after). Deriving the id from the
     * sound itself sidesteps that: picking a different ringtone is a genuinely new,
     * never-before-seen channel id, so there's nothing stale for the OS to fall back to.
     */
    private fun channelIdFor(soundUri: android.net.Uri): String = CHANNEL_PREFIX + soundUri.toString().hashCode()

    fun ensureChannel(context: Context): String {
        val targetSound = resolveSoundUri(context)
        val channelId = channelIdFor(targetSound)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return channelId
        val manager = context.getSystemService(NotificationManager::class.java) ?: return channelId

        // Clean up channels from previously-picked sounds so switching ringtones doesn't
        // pile up abandoned channels in system settings.
        manager.notificationChannels
            .filter { it.id.startsWith(CHANNEL_PREFIX) && it.id != channelId }
            .forEach { manager.deleteNotificationChannel(it.id) }

        if (manager.getNotificationChannel(channelId) != null) return channelId

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            channelId,
            "Flare alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Rings when someone nearby sends a Flare"
            enableVibration(true)
            setBypassDnd(true)
            setSound(targetSound, audioAttributes)
        }
        manager.createNotificationChannel(channel)
        return channelId
    }

    /**
     * Fires a Flare alert with the sender's real location (so the map opens centered on the
     * incident, not the viewer's own position) and their avatar, if they have one set.
     */
    fun postFlareAlert(
        context: Context,
        flareId: String,
        senderName: String,
        distance: String,
        location: String,
        flareLat: Double,
        flareLng: Double,
        senderAvatarUrl: String?,
    ) {
        val channelId = ensureChannel(context)

        val fullScreenIntent = Intent(context, FlareAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(FlareAlertActivity.EXTRA_FLARE_ID, flareId)
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

        val notification = NotificationCompat.Builder(context, channelId)
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

        // Real push and the redundant foreground poll can both post for the SAME still-active
        // Flare independently — without this check, every redundant post pushed the 5-minute
        // deadline further out, so an unattended alert could ring well past 5 minutes. Only
        // arm a fresh window the first time this specific flareId is seen ringing; a repost of
        // the same one just refreshes the visible notification, not the deadline.
        val ringState = context.getSharedPreferences("clarion_ring_state", Context.MODE_PRIVATE)
        if (ringState.getString("flare_id", null) != flareId) {
            ringState.edit()
                .putString("flare_id", flareId)
                .putLong("posted_at", SystemClock.elapsedRealtime())
                .apply()
            scheduleAutoDismiss(context)
        }
    }

    /** Called once the alert is resolved — the user responded, or the timeout fired — so the
     * next genuinely new Flare is free to arm its own fresh window. */
    fun clearRingState(context: Context) {
        context.getSharedPreferences("clarion_ring_state", Context.MODE_PRIVATE)
            .edit().remove("flare_id").remove("posted_at").apply()
    }

    fun currentRingingFlareId(context: Context): String? =
        context.getSharedPreferences("clarion_ring_state", Context.MODE_PRIVATE).getString("flare_id", null)

    /**
     * Real, confirmed root cause of "opening the app randomly rings a Flare alert": the
     * 5-minute auto-dismiss below relies on AlarmManager waking a BroadcastReceiver, and this
     * OEM's power management (like most budget-Android skins) is known to kill background
     * alarms for apps that aren't actively in the foreground — so an unanswered Flare's
     * notification can outlive its intended 5-minute window by hours, sitting in the tray as a
     * still-active, category=call, INSISTENT notification. Reopening the app later then has
     * this OEM's own shell re-surface that stale notification as a heads-up and replay its
     * sound, which looks exactly like a brand new phantom Flare firing. Rather than trust the
     * alarm alone, MainActivity calls this on every cold start and every resume: if a ring has
     * been "active" longer than it should ever legitimately be, force it closed here instead
     * of waiting on an alarm that may never fire.
     */
    fun cancelStaleRing(context: Context) {
        val ringState = context.getSharedPreferences("clarion_ring_state", Context.MODE_PRIVATE)
        val flareId = ringState.getString("flare_id", null) ?: return
        val postedAt = ringState.getLong("posted_at", 0L)
        val age = SystemClock.elapsedRealtime() - postedAt
        if (age < RING_DURATION_MS) return
        cancelIfRinging(context, flareId)
    }

    /**
     * Handles an incoming "flare_cancel" push: stops the ring immediately (rather than
     * waiting for the 5-minute timeout) but ONLY if this device is actually ringing the exact
     * flare being cancelled — a cancel for some other, older flare (a stale/duplicate delivery)
     * must never silence a genuinely new one that started ringing in between.
     */
    fun cancelIfRinging(context: Context, flareId: String): Boolean {
        if (currentRingingFlareId(context) != flareId) return false
        androidx.core.app.NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        cancelAutoDismiss(context)
        clearRingState(context)
        return true
    }

    private fun timeoutPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, FlareAlertTimeoutReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleAutoDismiss(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        // Inexact is fine — being a few seconds late to stop ringing has no user impact, and
        // it avoids the SCHEDULE_EXACT_ALARM permission entirely.
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + RING_DURATION_MS,
            timeoutPendingIntent(context),
        )
    }

    /** Called from FlareAlertActivity once the user actually responds, so this alarm doesn't
     * fire a pointless cancel on a notification that's already gone. */
    fun cancelAutoDismiss(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(timeoutPendingIntent(context))
    }
}

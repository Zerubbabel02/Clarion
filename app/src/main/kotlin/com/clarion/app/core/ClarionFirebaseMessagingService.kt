package com.clarion.app.core

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Real background delivery: a data-only FCM message (never a "notification" message, which
 * the system would show generically and skip this callback while the app is backgrounded)
 * carries everything the alert needs, already resolved server-side by the send-flare-push
 * Edge Function — sender name, this recipient's actual distance, avatar. No network round
 * trip needed here, which matters where connectivity is weak: the alert can render the
 * instant the message arrives, app open, backgrounded, or fully killed alike.
 */
class ClarionFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        if (data["type"] == "flare_cancel") {
            val flareId = data["flareId"] ?: return
            if (NotificationHelper.cancelIfRinging(applicationContext, flareId)) {
                FlareCancelSignal.flow.tryEmit(flareId)
            }
            return
        }
        if (data["type"] == "share_started") {
            val ownerId = data["ownerId"] ?: return
            ShareNotifier.postShareStarted(applicationContext, ownerId, data["ownerName"] ?: "A friend")
            return
        }
        if (data["type"] != "flare_alert") return

        val flareLat = data["flareLat"]?.toDoubleOrNull() ?: return
        val flareLng = data["flareLng"]?.toDoubleOrNull() ?: return
        val flareId = data["flareId"] ?: return
        val senderName = data["senderName"] ?: "Someone nearby"

        NotificationHelper.postFlareAlert(
            context = applicationContext,
            flareId = flareId,
            senderName = senderName,
            distance = data["distance"] ?: "",
            location = data["location"] ?: "Nearby",
            flareLat = flareLat,
            flareLng = flareLng,
            senderAvatarUrl = data["avatarUrl"]?.takeIf { it.isNotBlank() },
        )
    }

    override fun onNewToken(token: String) {
        // Best-effort: if this fires before sign-in, or the write fails, the token is also
        // refreshed and saved once on every real login (see ClarionMainShell), so it's never
        // stuck unsaved for long.
        scope.launch {
            try {
                ClarionRepository.updateFcmToken(token)
            } catch (_: Exception) {
                // Retried on next app open.
            }
        }
    }
}

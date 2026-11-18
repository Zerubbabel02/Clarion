package com.clarion.app.core

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Lets ClarionFirebaseMessagingService tell a currently-open FlareAlertActivity "the flare
 * you're showing was just cancelled" without needing a broadcast receiver. Cancelling the
 * notification alone isn't enough — if the phone is already unlocked and showing the
 * full-screen ringing UI, that screen needs to close itself too, not just go quiet.
 */
object FlareCancelSignal {
    val flow = MutableSharedFlow<String>(extraBufferCapacity = 1)
}

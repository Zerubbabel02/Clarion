package com.clarion.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.clarion.app.core.FlareCancelSignal
import com.clarion.app.core.NotificationHelper
import com.clarion.app.ui.nav.ClarionDestinations
import com.clarion.app.ui.screens.IncomingFlareScreen
import com.clarion.app.ui.theme.ClarionTheme
import kotlinx.coroutines.launch

/**
 * The full-screen-intent target Android launches over the lock screen when a Flare alert fires.
 * This is a separate Activity (not just a screen in MainActivity's nav graph) because only a
 * dedicated Activity can carry the show-over-lock-screen / turn-screen-on flags.
 */
class FlareAlertActivity : ComponentActivity() {
    companion object {
        const val EXTRA_FLARE_ID = "extra_flare_id"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_DISTANCE = "extra_distance"
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        const val EXTRA_AVATAR_URL = "extra_avatar_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val flareId = intent.getStringExtra(EXTRA_FLARE_ID)
        // If the sender cancels while this exact flare is on screen (phone already unlocked,
        // actively ringing), close this screen too, not just silence the notification.
        lifecycleScope.launch {
            FlareCancelSignal.flow.collect { cancelledId ->
                if (flareId != null && cancelledId == flareId) finish()
            }
        }

        // Leaving via system back must stop the ring just like Dismiss — otherwise the
        // notification stays active in the tray, still INSISTENT, with nothing left on screen
        // to stop it short of the 5-minute timeout (which isn't reliable in the background on
        // this OEM — see NotificationHelper.cancelStaleRing).
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    NotificationManagerCompat.from(this@FlareAlertActivity).cancel(NotificationHelper.NOTIFICATION_ID)
                    NotificationHelper.cancelAutoDismiss(this@FlareAlertActivity)
                    NotificationHelper.clearRingState(this@FlareAlertActivity)
                    finish()
                }
            },
        )

        val sender = intent.getStringExtra(EXTRA_SENDER) ?: "A neighbor"
        val distance = intent.getStringExtra(EXTRA_DISTANCE) ?: ""
        val location = intent.getStringExtra(EXTRA_LOCATION) ?: ""
        val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
        val avatarUrl = intent.getStringExtra(EXTRA_AVATAR_URL)

        fun stopRing() {
            NotificationManagerCompat.from(this).cancel(NotificationHelper.NOTIFICATION_ID)
            NotificationHelper.cancelAutoDismiss(this)
            NotificationHelper.clearRingState(this)
        }

        setContent {
            ClarionTheme(darkTheme = true) {
                IncomingFlareScreen(
                    flareId = flareId,
                    senderName = sender,
                    distance = distance,
                    location = location,
                    avatarUrl = avatarUrl,
                    flareLat = if (lat.isNaN()) null else lat,
                    flareLng = if (lng.isNaN()) null else lng,
                    // Stop the insistent ring only once the user actually responds.
                    onStopRing = { stopRing() },
                    onOpenClarion = {
                        stopRing()
                        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP })
                        finish()
                    },
                    onClose = { stopRing(); finish() },
                )
            }
        }
    }
}

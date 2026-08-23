package com.clarion.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.clarion.app.ui.nav.ClarionDestinations
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.clarion.app.ui.screens.IncomingFlareScreen
import com.clarion.app.ui.theme.ClarionTheme

/**
 * The full-screen-intent target Android launches over the lock screen when a Flare alert fires.
 * This is a separate Activity (not just a screen in MainActivity's nav graph) because only a
 * dedicated Activity can carry the show-over-lock-screen / turn-screen-on flags.
 */
class FlareAlertActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_DISTANCE = "extra_distance"
        const val EXTRA_LOCATION = "extra_location"
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

        val sender = intent.getStringExtra(EXTRA_SENDER) ?: "A neighbor"
        val distance = intent.getStringExtra(EXTRA_DISTANCE) ?: ""
        val location = intent.getStringExtra(EXTRA_LOCATION) ?: ""

        setContent {
            ClarionTheme {
                IncomingFlareScreen(
                    senderName = sender,
                    distance = distance,
                    location = location,
                    onOpenMap = {
                        val openMapIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(MainActivity.EXTRA_NAVIGATE_TO, ClarionDestinations.FLARE_MAP)
                        }
                        startActivity(openMapIntent)
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }
}

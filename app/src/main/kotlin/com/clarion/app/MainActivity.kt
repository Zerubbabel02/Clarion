package com.clarion.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.clarion.app.core.NotificationHelper
import com.clarion.app.core.ThemeMode
import com.clarion.app.core.ThemePrefs
import com.clarion.app.ui.nav.ClarionNavHost
import com.clarion.app.ui.nav.PendingNav
import com.clarion.app.ui.theme.ClarionTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NAVIGATE_TO = "extra_navigate_to"
        const val EXTRA_FLARE_LAT = "extra_flare_lat"
        const val EXTRA_FLARE_LNG = "extra_flare_lng"
        const val EXTRA_FLARE_SENDER = "extra_flare_sender"
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    // Compose State (not a plain var) so onNewIntent can push a fresh navigation target into
    // the already-composed NavHost when the app is already running in the background.
    private val pendingNav = mutableStateOf<PendingNav?>(null)
    private val themeMode = mutableStateOf(ThemeMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Deliberately discarding savedInstanceState, not passing it through: Navigation-
        // Compose restores its backstack across process death by default, and this is a
        // safety app — SendFlareScreen auto-sends a real Flare the instant it composes
        // (that's the intended UX: navigating there IS the send-confirmation). If the OS
        // kills this process while that screen is on top (routine on a budget device) and
        // the user later reopens the app, restoring the backstack recomposes straight back
        // into SendFlareScreen and fires a brand new real Flare with zero user action — this
        // was the "opening the app randomly sends a Flare" bug, confirmed by tracing exactly
        // how that screen triggers a send. super.onCreate(null) forces every fresh process
        // to start clean from Splash's real auth check instead of resuming mid-flow.
        super.onCreate(null)
        enableEdgeToEdge()

        NotificationHelper.ensureChannel(this)
        // Belt-and-suspenders against a real, confirmed bug: the 5-minute auto-dismiss alarm
        // depends on AlarmManager waking a background receiver, which OEM power management can
        // (and on this device, does) simply kill — leaving a stale, still-active "call"-style
        // Flare notification stuck in the tray long past its 5 minutes. Reopening the app then
        // has this OEM's own shell re-surface that stale notification as a heads-up and replay
        // its ring, which looks exactly like a brand new phantom Flare. Checking here means the
        // app itself clears it out the moment it's genuinely overdue, instead of trusting an
        // alarm that may never fire.
        NotificationHelper.cancelStaleRing(this)
        requestNotificationPermissionIfNeeded()

        pendingNav.value = pendingNavFrom(intent)
        themeMode.value = ThemePrefs.get(this)

        setContent {
            val mode = themeMode.value
            val systemDark = isSystemInDarkTheme()
            val isDark = when (mode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            ClarionTheme(darkTheme = isDark) {
                ClarionNavHost(
                    pendingNav = pendingNav.value,
                    themeMode = mode,
                    onThemeModeChange = {
                        themeMode.value = it
                        ThemePrefs.set(this, it)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Catches the case where the process survived in the background (so onCreate doesn't
        // run again) and the user simply switched back to the app — the exact "leave and
        // reenter" repro for the stale-notification bug above.
        NotificationHelper.cancelStaleRing(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNav.value = pendingNavFrom(intent)
    }

    private fun pendingNavFrom(intent: Intent): PendingNav? {
        val route = intent.getStringExtra(EXTRA_NAVIGATE_TO) ?: return null
        val lat = intent.getDoubleExtra(EXTRA_FLARE_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(EXTRA_FLARE_LNG, Double.NaN)
        return PendingNav(
            route = route,
            flareLat = if (lat.isNaN()) null else lat,
            flareLng = if (lng.isNaN()) null else lng,
            flareSender = intent.getStringExtra(EXTRA_FLARE_SENDER),
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

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
import com.clarion.app.ui.theme.ClarionTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NAVIGATE_TO = "extra_navigate_to"
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    // Compose State (not a plain var) so onNewIntent can push a fresh navigation target into
    // the already-composed NavHost when the app is already running in the background.
    private val pendingRoute = mutableStateOf<String?>(null)
    private val themeMode = mutableStateOf(ThemeMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.ensureChannel(this)
        requestNotificationPermissionIfNeeded()

        pendingRoute.value = intent.getStringExtra(EXTRA_NAVIGATE_TO)
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
                    startRoute = pendingRoute.value,
                    themeMode = mode,
                    onThemeModeChange = {
                        themeMode.value = it
                        ThemePrefs.set(this, it)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_NAVIGATE_TO)
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

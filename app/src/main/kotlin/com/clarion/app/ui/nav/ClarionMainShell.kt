package com.clarion.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.LocationHelper
import com.clarion.app.core.NotificationHelper
import com.clarion.app.core.ThemeMode
import com.clarion.app.ui.screens.CirclesSettingsScreen
import com.clarion.app.ui.screens.FlareMapScreen
import com.clarion.app.ui.screens.HomeScreen
import com.clarion.app.ui.screens.ProfileScreen
import kotlinx.coroutines.delay

private data class Tab(val label: String, val icon: @Composable (Color) -> Unit)

private val tabs = listOf(
    Tab("Home") { c -> HomeTabIcon(c) },
    Tab("Map") { c -> MapTabIcon(c) },
    Tab("Circles") { c -> CirclesTabIcon(c) },
    Tab("Profile") { c -> ProfileTabIcon(c) },
)

@Composable
fun ClarionMainShell(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSendFlare: () -> Unit,
    onShareLocation: () -> Unit,
    onOpenNightMode: () -> Unit,
    onSignedOut: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    // The real (not demo) receiving path: while the app is open, poll for other users'
    // active Flares within their own broadcast radius of my last-known location, and ring
    // the exact same alert a push notification would trigger. Needs the app alive — true
    // "even if closed" delivery is still pending Firebase, deferred earlier in this project.
    LaunchedEffect(Unit) {
        val alreadyAlerted = mutableSetOf<String>()
        while (true) {
            try {
                if (LocationHelper.hasPermission(context)) {
                    @Suppress("MissingPermission")
                    val here = LocationHelper.getCurrentLocation(context)
                    if (here != null) {
                        val matches = ClarionRepository.nearbyActiveFlares(here.lat, here.lng)
                        for ((flare, sender) in matches) {
                            val id = flare.id ?: continue
                            if (alreadyAlerted.add(id)) {
                                // Flat-earth approximation for a short display distance — fine at this scale.
                                val dLatKm = (flare.lat - here.lat) * 111.0
                                val dLngKm = (flare.lng - here.lng) * 111.0 * kotlin.math.cos(Math.toRadians(here.lat))
                                val distanceM = kotlin.math.sqrt(dLatKm * dLatKm + dLngKm * dLngKm) * 1000
                                val distanceLabel = if (distanceM < 950) "${distanceM.toInt()}m" else "${"%.1f".format(distanceM / 1000)}km"
                                NotificationHelper.postFlareAlert(
                                    context = context,
                                    senderName = sender.displayName,
                                    distance = distanceLabel,
                                    location = flare.locationLabel ?: "Nearby",
                                    flareLat = flare.lat,
                                    flareLng = flare.lng,
                                    senderAvatarUrl = sender.avatarUrl,
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // A failed poll just tries again next cycle.
            }
            delay(20_000)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selected == index
                    val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selected = index },
                        icon = { tab.icon(tint) },
                        label = { Text(tab.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            when (selected) {
                0 -> HomeScreen(onSendFlare = onSendFlare, onShareLocation = onShareLocation)
                1 -> FlareMapScreen(targetLat = null, targetLng = null, targetSenderName = null, onBack = { selected = 0 })
                2 -> CirclesSettingsScreen(onOpenNightMode = onOpenNightMode)
                3 -> ProfileScreen(themeMode = themeMode, onThemeModeChange = onThemeModeChange, onSignedOut = onSignedOut)
            }
        }
    }
}

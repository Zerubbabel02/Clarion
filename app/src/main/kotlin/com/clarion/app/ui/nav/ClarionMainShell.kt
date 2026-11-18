package com.clarion.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.HoldPrefs
import com.clarion.app.core.LatLng
import com.clarion.app.core.LocationHelper
import com.clarion.app.core.NotificationHelper
import com.clarion.app.core.SharedLocation
import com.clarion.app.core.ThemeMode
import com.clarion.app.ui.screens.CIcon
import com.clarion.app.ui.screens.FriendsMapScreen
import com.clarion.app.ui.screens.HomeScreen
import com.clarion.app.ui.screens.ShareSheet
import com.clarion.app.ui.screens.T
import com.clarion.app.ui.screens.YouScreen
import com.clarion.app.ui.screens.glass
import com.clarion.app.ui.screens.tk
import kotlinx.coroutines.delay

/**
 * Tabs: Home · Share · Map · You (mockup). Home, Map and You are full pages; Share is a pop-up.
 * The tab bar floats at the bottom; its measured height keeps every island just above it.
 */
@Composable
fun ClarionMainShell(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSendFlare: () -> Unit,
    onShareLocation: () -> Unit,
    onFlareTrustedCircle: () -> Unit,
    onOpenNightMode: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var selected by remember { mutableIntStateOf(0) } // 0 Home, 1 Map, 2 You
    var showShare by remember { mutableStateOf(false) }
    var focusId by remember { mutableStateOf<String?>(null) }
    var youSub by remember { mutableStateOf<String?>(null) }
    var myLoc by remember { mutableStateOf<LatLng?>(null) }
    var sharers by remember { mutableStateOf<List<SharedLocation>>(emptyList()) }
    var trustedMode by remember { mutableStateOf(false) }
    var holdSeconds by remember { mutableFloatStateOf(HoldPrefs.get(context)) }
    var tabBarH by remember { mutableStateOf(86.dp) }
    val dockBottom = tabBarH + 12.dp + 10.dp

    fun sendFlare(trusted: Boolean) = if (trusted) onFlareTrustedCircle() else onSendFlare()

    // Save/refresh this device's FCM token every real login — push reaches the app even when closed.
    LaunchedEffect(Unit) { ClarionRepository.fetchAndSaveFcmToken() }

    // Every 20 s while open: refresh your location (so people you share with see you move), who is
    // sharing with you, and a foreground fast-path for nearby Flares from the last 2 hours.
    LaunchedEffect(Unit) {
        val alreadyAlerted = mutableSetOf<String>()
        while (true) {
            try {
                if (LocationHelper.hasPermission(context)) {
                    @Suppress("MissingPermission")
                    val here = LocationHelper.getCurrentLocation(context)
                    if (here != null) {
                        myLoc = here
                        try { ClarionRepository.updateLocation(here.lat, here.lng) } catch (_: Exception) {}
                        for ((flare, sender) in ClarionRepository.nearbyActiveFlares(here.lat, here.lng)) {
                            val id = flare.id ?: continue
                            if (alreadyAlerted.add(id)) {
                                val dLatKm = (flare.lat - here.lat) * 111.0
                                val dLngKm = (flare.lng - here.lng) * 111.0 * kotlin.math.cos(Math.toRadians(here.lat))
                                val distanceM = kotlin.math.sqrt(dLatKm * dLatKm + dLngKm * dLngKm) * 1000
                                val distanceLabel = if (distanceM < 950) "${distanceM.toInt()}m" else "${"%.1f".format(distanceM / 1000)}km"
                                NotificationHelper.postFlareAlert(
                                    context = context, flareId = id, senderName = sender.displayName, distance = distanceLabel,
                                    location = flare.locationLabel ?: "Nearby", flareLat = flare.lat, flareLng = flare.lng, senderAvatarUrl = sender.avatarUrl,
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            try { sharers = ClarionRepository.sharedWithMe() } catch (_: Exception) {}
            delay(20_000)
        }
    }

    Box(Modifier.fillMaxSize().background(tk().bg)) {
        when (selected) {
            0 -> HomeScreen(
                myLoc = myLoc, sharers = sharers, holdSeconds = holdSeconds, dockBottom = dockBottom, hideIsland = showShare,
                onHoldComplete = { sendFlare(it) }, onModeChange = { trustedMode = it },
                onOpenYou = { youSub = null; selected = 2 },
                onSeeFriend = { focusId = it; selected = 1 },
                onOpenShare = { showShare = true },
                onOpenCircles = { youSub = "people"; selected = 2 },
            )
            1 -> FriendsMapScreen(
                myLoc = myLoc, sharers = sharers, focusId = focusId, dockBottom = dockBottom, hideControls = showShare,
                onFocus = { focusId = it }, holdSeconds = holdSeconds, onHoldComplete = { sendFlare(trustedMode) },
            )
            else -> YouScreen(
                themeMode = themeMode, onThemeModeChange = onThemeModeChange, onSignedOut = onSignedOut,
                holdSeconds = holdSeconds, onHoldSecondsChange = { holdSeconds = it; HoldPrefs.set(context, it) },
                sub = youSub, onSub = { youSub = it }, dockBottom = dockBottom,
            )
        }

        if (showShare) {
            ShareSheet(
                myLoc = myLoc, sharers = sharers, dockBottom = dockBottom,
                onDismiss = { showShare = false },
                onSeeFriend = { focusId = it; selected = 1; showShare = false },
            )
        }

        TabBar(
            current = if (showShare) "share" else when (selected) { 0 -> "home"; 1 -> "map"; else -> "you" },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)
                .onSizeChanged { tabBarH = with(density) { it.height.toDp() } },
        ) { key ->
            when (key) {
                "home" -> { showShare = false; selected = 0 }
                "share" -> showShare = !showShare
                "map" -> { showShare = false; selected = 1 }
                else -> { showShare = false; youSub = null; selected = 2 }
            }
        }
    }
}

@Composable
private fun TabBar(current: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val t = tk()
    Row(
        modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(Triple("home", "Home", "home"), Triple("share", "Share", "share"), Triple("map", "Map", "map"), Triple("you", "You", "user")).forEach { (key, label, icon) ->
            val on = current == key
            Column(
                Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null) { onSelect(key) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(48.dp).background(if (on) t.primary else t.surface2.copy(alpha = 0.78f), CircleShape), contentAlignment = Alignment.Center) {
                    CIcon(icon, 21.dp, if (on) t.onPrimary else t.ink)
                }
                Spacer(Modifier.height(6.dp))
                T(label, 11.5.sp, if (on) t.ink else t.muted, FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

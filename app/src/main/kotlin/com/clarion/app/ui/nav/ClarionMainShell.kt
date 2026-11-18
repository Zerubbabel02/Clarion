package com.clarion.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
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
import com.clarion.app.ui.screens.CirclesSettingsScreen
import com.clarion.app.ui.screens.FriendsMapScreen
import com.clarion.app.ui.screens.HomeScreen
import com.clarion.app.ui.screens.ProfileScreen
import com.clarion.app.ui.screens.ScreenHeader
import com.clarion.app.ui.screens.ShareSheet
import com.clarion.app.ui.screens.glass
import kotlinx.coroutines.delay

/**
 * Tabs: Home · Share · Map · You. Home, Map and You are full pages; Share is a pop-up.
 * The tab bar floats over the content so the map runs behind it.
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
    var selected by remember { mutableIntStateOf(0) } // 0 Home, 1 Map, 2 You
    var showShare by remember { mutableStateOf(false) }
    var focusId by remember { mutableStateOf<String?>(null) }
    var youSub by remember { mutableStateOf<String?>(null) }
    var myLoc by remember { mutableStateOf<LatLng?>(null) }
    var sharers by remember { mutableStateOf<List<SharedLocation>>(emptyList()) }
    var trustedMode by remember { mutableStateOf(false) }
    var holdSeconds by remember { mutableFloatStateOf(HoldPrefs.get(context)) }

    fun sendFlare(trusted: Boolean) = if (trusted) onFlareTrustedCircle() else onSendFlare()

    // Save/refresh this device's FCM token every real login — the send-flare-push Edge
    // Function delivers to it directly, including when this app is fully closed.
    LaunchedEffect(Unit) {
        ClarionRepository.fetchAndSaveFcmToken()
    }

    // Every 20 s while the app is open: refresh your location (so friends you share with see
    // you move), refresh who is sharing with you, and a foreground fast-path for nearby Flares
    // alongside real push. NOTIFICATION_ID is shared with push, so a duplicate just replaces it.
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
                        val matches = ClarionRepository.nearbyActiveFlares(here.lat, here.lng)
                        for ((flare, sender) in matches) {
                            val id = flare.id ?: continue
                            if (alreadyAlerted.add(id)) {
                                val dLatKm = (flare.lat - here.lat) * 111.0
                                val dLngKm = (flare.lng - here.lng) * 111.0 * kotlin.math.cos(Math.toRadians(here.lat))
                                val distanceM = kotlin.math.sqrt(dLatKm * dLatKm + dLngKm * dLngKm) * 1000
                                val distanceLabel = if (distanceM < 950) "${distanceM.toInt()}m" else "${"%.1f".format(distanceM / 1000)}km"
                                NotificationHelper.postFlareAlert(
                                    context = context,
                                    flareId = id,
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
            try { sharers = ClarionRepository.sharedWithMe() } catch (_: Exception) {}
            delay(20_000)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (selected) {
            0 -> HomeScreen(
                myLoc = myLoc,
                sharers = sharers,
                holdSeconds = holdSeconds,
                onHoldComplete = { trusted -> sendFlare(trusted) },
                onModeChange = { trustedMode = it },
                onOpenYou = { youSub = null; selected = 2 },
                onSeeFriend = { focusId = it; selected = 1 },
                onOpenShare = { showShare = true },
                onOpenCircles = { youSub = "circles"; selected = 2 },
            )
            1 -> FriendsMapScreen(
                myLoc = myLoc,
                sharers = sharers,
                focusId = focusId,
                onFocus = { focusId = it },
                holdSeconds = holdSeconds,
                onHoldComplete = { sendFlare(trustedMode) },
            )
            else -> if (youSub == "circles") {
                Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(bottom = 96.dp)) {
                    ScreenHeader(title = "Who gets my Flare", onBack = { youSub = null })
                    CirclesSettingsScreen()
                }
            } else {
                ProfileScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onOpenNightMode = onOpenNightMode,
                    onSignedOut = onSignedOut,
                    onOpenCircles = { youSub = "circles" },
                    holdSeconds = holdSeconds,
                    onHoldSecondsChange = { holdSeconds = it; HoldPrefs.set(context, it) },
                )
            }
        }

        TabBar(
            current = if (showShare) "share" else when (selected) { 0 -> "home"; 1 -> "map"; else -> "you" },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
        ) { key ->
            when (key) {
                "home" -> { showShare = false; selected = 0 }
                "share" -> showShare = !showShare
                "map" -> { showShare = false; selected = 1 }
                else -> { showShare = false; youSub = null; selected = 2 }
            }
        }

        if (showShare) {
            ShareSheet(
                myLoc = myLoc,
                sharers = sharers,
                onDismiss = { showShare = false },
                onSeeFriend = { focusId = it; selected = 1; showShare = false },
                onShareWithSomeone = { showShare = false; onShareLocation() },
            )
        }
    }
}

@Composable
private fun TabBar(current: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp)).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf("home" to "Home", "share" to "Share", "map" to "Map", "you" to "You").forEach { (key, label) ->
            val on = current == key
            val tint = if (on) c.onPrimary else c.onBackground.copy(alpha = 0.75f)
            Column(
                modifier = Modifier.weight(1f).clickable { onSelect(key) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(46.dp).background(if (on) c.primary else c.onBackground.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    when (key) {
                        "home" -> HomeTabIcon(tint)
                        "share" -> Icon(Icons.Default.Share, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                        "map" -> MapTabIcon(tint)
                        else -> ProfileTabIcon(tint)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (on) c.onBackground else c.onBackground.copy(alpha = 0.6f))
            }
        }
    }
}

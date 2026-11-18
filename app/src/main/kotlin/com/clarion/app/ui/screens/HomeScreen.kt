package com.clarion.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.ProfileCache
import com.clarion.app.core.SharedLocation
import com.clarion.app.ui.theme.Flare
import com.clarion.app.ui.theme.Safe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

private fun timeOfDayGreeting(): String {
    // java.time works natively at minSdk 26. LocalTime.now() reads the phone's current timezone.
    return when (java.time.LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
}

private const val MIN_KM = 0.3f
private const val MAX_KM = 5f
private fun kmToT(km: Float) = (ln(km / MIN_KM) / ln(MAX_KM / MIN_KM)).coerceIn(0f, 1f)
private fun tToKm(t: Float) = MIN_KM * exp(ln(MAX_KM / MIN_KM) * t)

/**
 * Night Map home: the map is the home screen. Greeting island and "Find my circle" float on top;
 * the Flare island (hold button, who it reaches, radius) floats above the tab bar.
 */
@Composable
fun HomeScreen(
    myLoc: com.clarion.app.core.LatLng?,
    sharers: List<SharedLocation>,
    holdSeconds: Float,
    onHoldComplete: (trusted: Boolean) -> Unit,
    onModeChange: (trusted: Boolean) -> Unit,
    onOpenYou: () -> Unit,
    onSeeFriend: (String) -> Unit,
    onOpenShare: () -> Unit,
    onOpenCircles: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var displayName by remember { mutableStateOf(ProfileCache.getDisplayName(context)) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf("radius") }
    var radiusKm by remember { mutableFloatStateOf(1.2f) }
    var menuOpen by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var centered by remember { mutableStateOf(false) }
    val greeting = remember { timeOfDayGreeting() }
    val handle = remember { MapHandle() }

    LaunchedEffect(Unit) {
        try {
            ClarionRepository.getMyProfile()?.let { p ->
                displayName = p.displayName
                ProfileCache.setDisplayName(context, p.displayName)
                avatarUrl = p.avatarUrl
                mode = if (p.broadcastMode == "circle") "circle" else "radius"
                onModeChange(mode == "circle")
                radiusKm = p.radiusKm.toFloat().coerceIn(MIN_KM, MAX_KM)
            }
        } catch (_: Exception) {
            // Cached name stays on screen; never crash on a network hiccup.
        }
    }
    LaunchedEffect(myLoc) {
        val l = myLoc ?: return@LaunchedEffect
        if (!centered) {
            centered = true
            handle.withMap { it.cameraPosition = CameraPosition.Builder().target(LatLng(l.lat, l.lng)).zoom(14.0).build() }
        }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2600); toast = null } }

    fun setMode(m: String) {
        mode = m; menuOpen = false; onModeChange(m == "circle")
        scope.launch { try { ClarionRepository.updateBroadcastMode(m) } catch (_: Exception) {} }
    }

    fun findCircle() {
        val pts = sharers.mapNotNull { s -> if (s.lat != null && s.lng != null) LatLng(s.lat, s.lng) else null }
        if (pts.isEmpty()) { toast = "Nobody is sharing their location with you right now"; return }
        val all = pts + listOfNotNull(myLoc?.let { LatLng(it.lat, it.lng) })
        val pad = with(density) { 90.dp.toPx() }.toInt()
        handle.withMap { m ->
            val spread = all.maxOf { a -> all.maxOf { b -> distanceKm(a.latitude, a.longitude, b.latitude, b.longitude) } }
            if (all.size == 1 || spread < 0.03) m.animateCamera(CameraUpdateFactory.newLatLngZoom(all[0], 15.5))
            else m.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(all).build(), pad, pad * 2, pad, pad * 4), 700)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ClarionMapBox(handle, start = myLoc?.let { LatLng(it.lat, it.lng) } ?: CLARION_FALLBACK, zoom = 14.0)

        // Map overlay: radius ring, friends sharing with you, and your own dot.
        Box(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val moved = handle.tick
            val me = myLoc?.let { handle.screen(it.lat, it.lng) }
            if (me != null && myLoc != null && mode == "radius") {
                val edge = handle.screen(myLoc.lat + radiusKm / 111.0, myLoc.lng)
                if (edge != null) {
                    val r = abs(edge.y - me.y)
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Flare.copy(alpha = 0.10f), r, me)
                        drawCircle(Flare, r, me, style = Stroke(2.dp.toPx()))
                    }
                    val half = with(density) { 40.dp.toPx() }
                    val lh = with(density) { 10.dp.toPx() }
                    Box(
                        modifier = Modifier.offset { IntOffset((me.x - half).roundToInt(), (me.y - r - lh).roundToInt()) }.width(80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            formatKm(radiusKm.toDouble()), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.background(Flare, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            sharers.forEach { s ->
                if (s.lat != null && s.lng != null) handle.screen(s.lat, s.lng)?.let { p -> MapPin(p, s.displayName, s.avatarUrl, false) { onSeeFriend(s.id) } }
            }
            if (me != null) MeDot(me)
        }

        // Top: greeting island, then Find my circle beside the "is sharing" pill.
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.glass(RoundedCornerShape(28.dp)).padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(displayName, avatarUrl, 44, modifier = Modifier.clickable { onOpenYou() })
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(
                        if (displayName != null) "$greeting, ${firstName(displayName)}" else greeting,
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.onBackground,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(Safe, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("All quiet in your circle", fontSize = 12.5.sp, color = c.onBackground.copy(alpha = 0.65f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "◎  Find my circle", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = c.onBackground,
                    modifier = Modifier.glass(RoundedCornerShape(50)).clickable { findCircle() }.padding(horizontal = 14.dp, vertical = 13.dp),
                )
                if (sharers.isNotEmpty()) {
                    val last = sharers.last()
                    val more = sharers.size - 1
                    Row(
                        modifier = Modifier.weight(1f).glass(RoundedCornerShape(50))
                            .clickable { if (more > 0) onOpenShare() else onSeeFriend(last.id) }
                            .padding(start = 6.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PersonAvatar(last.displayName, last.avatarUrl, 30, live = true)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (more > 0) "${firstName(last.displayName)} +$more" else "${firstName(last.displayName)} · Live",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onBackground,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Text(
                            "See", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = c.onPrimary,
                            modifier = Modifier.background(c.primary, RoundedCornerShape(50)).padding(horizontal = 13.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // Flare island: on its own, above the tab bar.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 100.dp)
                .glass(RoundedCornerShape(30.dp))
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HoldPillButton(holdSeconds, onComplete = { onHoldComplete(mode == "circle") })
            Box {
                Text(
                    if (mode == "radius") "⌖  Reaches everyone within ${formatKm(radiusKm.toDouble())}  ▾" else "Reaches your trusted circle  ▾",
                    fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = c.onBackground,
                    modifier = Modifier
                        .background(c.surface, RoundedCornerShape(50))
                        .border(1.dp, c.onBackground.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .clickable { menuOpen = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Column { Text("By distance", fontWeight = FontWeight.Bold); Text("Everyone with Clarion within ${formatKm(radiusKm.toDouble())}", fontSize = 12.5.sp) } },
                        onClick = { setMode("radius") },
                    )
                    DropdownMenuItem(
                        text = { Column { Text("Trusted circle", fontWeight = FontWeight.Bold); Text("Only the people you chose", fontSize = 12.5.sp) } },
                        onClick = { setMode("circle") },
                    )
                    DropdownMenuItem(
                        text = { Text("Choose who is in your circle  ›", color = c.primary, fontWeight = FontWeight.Bold) },
                        onClick = { menuOpen = false; onOpenCircles() },
                    )
                }
            }
            if (mode == "radius") {
                Slider(
                    value = kmToT(radiusKm),
                    onValueChange = { radiusKm = tToKm(it) },
                    onValueChangeFinished = { scope.launch { try { ClarionRepository.updateRadiusKm(radiusKm.toDouble()) } catch (_: Exception) {} } },
                    colors = SliderDefaults.colors(thumbColor = c.onBackground, activeTrackColor = c.onBackground, inactiveTrackColor = c.onBackground.copy(alpha = 0.18f)),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("300 m", fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.6f))
                    Text("5 km", fontSize = 12.sp, color = c.onBackground.copy(alpha = 0.6f))
                }
            }
        }

        toast?.let {
            Text(
                it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.background,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 140.dp)
                    .background(c.onBackground, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
    }
}

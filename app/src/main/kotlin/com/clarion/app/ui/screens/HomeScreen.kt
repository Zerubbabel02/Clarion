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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.ProfileCache
import com.clarion.app.core.SharedLocation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import kotlin.math.abs
import kotlin.math.roundToInt

private fun timeOfDayGreeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..20 -> "Good evening"
    else -> "Good night"
}

/**
 * Night Map home (mockup 1): the map is home. Greeting island, then Find my circle beside the
 * "is sharing" pill; the Flare island floats on its own just above the tab bar.
 */
@Composable
fun HomeScreen(
    myLoc: com.clarion.app.core.LatLng?,
    sharers: List<SharedLocation>,
    dots: List<com.clarion.app.core.NearbyDot>,
    holdSeconds: Float,
    dockBottom: Dp,
    hideIsland: Boolean,
    onHoldComplete: (trusted: Boolean) -> Unit,
    onModeChange: (trusted: Boolean) -> Unit,
    onOpenYou: () -> Unit,
    onSeeFriend: (String) -> Unit,
    onOpenShare: () -> Unit,
    onOpenCircles: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = tk()
    val density = LocalDensity.current
    var displayName by remember { mutableStateOf(ProfileCache.getDisplayName(context)) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var trusted by remember { mutableStateOf(false) }
    var trustedCount by remember { mutableStateOf<Int?>(null) }
    var radiusKm by remember { mutableFloatStateOf(1.2f) }
    var toast by remember { mutableStateOf<String?>(null) }
    var fit by remember { mutableStateOf(false) }
    var centered by remember { mutableStateOf(false) }
    val greeting = remember { timeOfDayGreeting() }
    val handle = remember { MapHandle() }

    LaunchedEffect(Unit) {
        try {
            ClarionRepository.getMyProfile()?.let { p ->
                displayName = p.displayName
                ProfileCache.setDisplayName(context, p.displayName)
                avatarUrl = p.avatarUrl
                trusted = p.broadcastMode == "circle"
                onModeChange(trusted)
                radiusKm = p.radiusKm.toFloat().coerceIn(MIN_KM, MAX_KM)
            }
            trustedCount = ClarionRepository.listTrustedMembers().size
        } catch (_: Exception) {}
    }
    LaunchedEffect(myLoc) {
        val l = myLoc ?: return@LaunchedEffect
        if (!centered) {
            centered = true
            handle.withMap { it.cameraPosition = CameraPosition.Builder().target(LatLng(l.lat, l.lng)).zoom(14.0).build() }
        }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2800); toast = null } }

    fun backToMe() {
        fit = false
        myLoc?.let { l -> handle.withMap { it.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(l.lat, l.lng), 14.0), 700) } }
    }

    fun findCircle() {
        if (fit) { backToMe(); return }
        val pts = sharers.mapNotNull { s -> if (s.lat != null && s.lng != null) LatLng(s.lat, s.lng) else null }
        if (pts.isEmpty()) { toast = "Nobody is sharing their location with you right now"; return }
        val all = pts + listOfNotNull(myLoc?.let { LatLng(it.lat, it.lng) })
        val side = with(density) { 70.dp.toPx() }.toInt()
        val top = with(density) { 170.dp.toPx() }.toInt()
        val bottom = with(density) { (dockBottom + 230.dp).toPx() }.toInt()
        fit = true
        toast = "Showing ${pts.size} ${if (pts.size == 1) "person" else "people"} sharing with you"
        handle.withMap { m ->
            val spread = all.maxOf { a -> all.maxOf { b -> distanceKm(a.latitude, a.longitude, b.latitude, b.longitude) } }
            if (all.size == 1 || spread < 0.03) m.animateCamera(CameraUpdateFactory.newLatLngZoom(all[0], 15.5), 700)
            else m.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(all).build(), side, top, side, bottom), 700)
        }
    }

    fun setMode(tr: Boolean) {
        trusted = tr; onModeChange(tr)
        scope.launch { try { ClarionRepository.updateBroadcastMode(if (tr) "circle" else "radius") } catch (_: Exception) {} }
    }

    Box(Modifier.fillMaxSize()) {
        ClarionMapBox(handle, start = myLoc?.let { LatLng(it.lat, it.lng) } ?: CLARION_FALLBACK, zoom = 14.0)

        // Radius ring, friends sharing with you, and you.
        Box(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val moved = handle.tick
            val me = myLoc?.let { handle.screen(it.lat, it.lng) }
            if (me != null && myLoc != null && !trusted) {
                val edge = handle.screen(myLoc.lat + radiusKm / 111.0, myLoc.lng)
                if (edge != null) {
                    val r = abs(edge.y - me.y)
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(t.flare.copy(alpha = 0.10f), r, me)
                        drawCircle(t.flare, r, me, style = Stroke(2.dp.toPx()))
                    }
                    val half = with(density) { 40.dp.toPx() }
                    val lh = with(density) { 10.dp.toPx() }
                    Box(Modifier.offset { IntOffset((me.x - half).roundToInt(), (me.y - r - lh).roundToInt()) }.width(80.dp), contentAlignment = Alignment.Center) {
                        T(formatKm(radiusKm.toDouble()), 11.sp, t.onFlare, FontWeight.Bold, modifier = Modifier.background(t.flare, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            // Neighbour dots (approximate, anonymous): orange when inside your Flare distance.
            val dotHalf = with(density) { 3.5.dp.toPx() }
            dots.forEach { d ->
                handle.screen(d.lat, d.lng)?.let { p ->
                    val inside = !trusted && myLoc != null && distanceKm(myLoc.lat, myLoc.lng, d.lat, d.lng) <= radiusKm
                    Box(
                        Modifier.offset { IntOffset((p.x - dotHalf).roundToInt(), (p.y - dotHalf).roundToInt()) }.size(7.dp)
                            .then(if (inside) Modifier.border(3.dp, t.flare.copy(alpha = 0.26f), CircleShape) else Modifier)
                            .background(if (inside) t.flare else t.muted.copy(alpha = 0.38f), CircleShape),
                    )
                }
            }
            sharers.forEach { s -> if (s.lat != null && s.lng != null) handle.screen(s.lat, s.lng)?.let { p -> MapPin(p, s.displayName, s.avatarUrl, false) { onSeeFriend(s.id) } } }
            if (me != null) MeDot(me)
        }

        // Top: greeting island, then Find my circle + the sharing pill side by side.
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.glass(RoundedCornerShape(28.dp)).padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(displayName, avatarUrl, 44.dp, modifier = Modifier.clip(CircleShape).clickable { onOpenYou() })
                Spacer(Modifier.width(11.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    T(if (displayName != null) "$greeting, ${firstName(displayName)}" else greeting, 17.sp, t.ink, FontWeight.ExtraBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(t.safeDot, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        T("All quiet in your circle", 12.5.sp, t.muted)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50))
                        .then(if (fit) Modifier.background(t.primary, RoundedCornerShape(50)) else Modifier.glass(RoundedCornerShape(50)))
                        .clickable { findCircle() }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CIcon("target", 18.dp, if (fit) t.onPrimary else t.ink)
                    Spacer(Modifier.width(8.dp))
                    T(if (fit) "Back to me" else "Find my circle", 13.5.sp, if (fit) t.onPrimary else t.ink, FontWeight.Bold, maxLines = 1)
                }
                if (sharers.isNotEmpty()) {
                    val last = sharers.last()
                    val more = sharers.size - 1
                    Row(
                        Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(50)).glass(RoundedCornerShape(50))
                            .clickable { if (more > 0) onOpenShare() else onSeeFriend(last.id) }
                            .padding(start = 7.dp, end = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(last.displayName, last.avatarUrl, 22.dp, live = true)
                        Spacer(Modifier.width(8.dp))
                        Row(Modifier.weight(1f)) {
                            T(firstName(last.displayName) + if (more > 0) " +$more" else "", 13.sp, t.ink, FontWeight.Bold, maxLines = 1)
                            if (more == 0) T(" · Live", 13.sp, t.safe, FontWeight.Bold, maxLines = 1)
                        }
                        T("See", 12.5.sp, t.onPrimary, FontWeight.Bold, modifier = Modifier.background(t.primary, RoundedCornerShape(50)).padding(horizontal = 13.dp, vertical = 9.dp))
                    }
                }
            }
        }

        // The Flare island, on its own, just above the tab bar.
        if (!hideIsland) {
            GlassColumn(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = dockBottom)) {
                HoldPill(holdSeconds, onComplete = { onHoldComplete(trusted) })
                ReachChip(trusted, radiusKm, trustedCount, onMode = { setMode(it) }, onChooseCircle = onOpenCircles)
                if (!trusted) {
                    RadiusBar(radiusKm, onChange = { radiusKm = it }, onDone = { scope.launch { try { ClarionRepository.updateRadiusKm(radiusKm.toDouble()) } catch (_: Exception) {} } })
                }
            }
        }

        Toast(toast, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp))
    }
}

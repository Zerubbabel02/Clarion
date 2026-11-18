package com.clarion.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.LocationHelper
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import kotlin.math.roundToInt

/** "350m" → ("350", "metres away"), "1.2km" → ("1.2", "km away"). */
private fun splitDistance(d: String): Pair<String, String> {
    val s = d.trim().lowercase().replace(" ", "")
    return when {
        s.endsWith("km") -> s.removeSuffix("km") to "km away"
        s.endsWith("m") -> s.removeSuffix("m") to "metres away"
        s.isEmpty() -> "" to "nearby"
        else -> s to "away"
    }
}

/**
 * Receiving a Flare (mockup, Night Watch style): the alert with the map on top, then the map
 * with "I'm on my way", or "Alert dismissed". Replies are saved so the sender sees them.
 */
@Composable
fun IncomingFlareScreen(
    flareId: String?,
    senderName: String,
    distance: String,
    location: String,
    avatarUrl: String? = null,
    flareLat: Double?,
    flareLng: Double?,
    onStopRing: () -> Unit,
    onOpenClarion: () -> Unit,
    onClose: () -> Unit,
) {
    val t = tk()
    var view by remember { mutableStateOf("alert") }
    var onWay by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val (num, unit) = splitDistance(distance)
    val place = location.takeIf { it.isNotBlank() && it != "Nearby" }

    LaunchedEffect(flareId) { if (flareId != null) try { ClarionRepository.respondToFlare(flareId, "seen") } catch (_: Exception) {} }

    when (view) {
        "alert" -> Column(Modifier.fillMaxSize().background(t.bg)) {
            Box(Modifier.fillMaxWidth().height(372.dp)) {
                if (flareLat != null && flareLng != null) {
                    val handle = remember { MapHandle() }
                    ClarionMapBox(handle, LatLng(flareLat, flareLng), 15.5)
                    Box(Modifier.fillMaxSize()) {
                        @Suppress("UNUSED_VARIABLE") val moved = handle.tick
                        handle.screen(flareLat, flareLng)?.let { FlareDot(it, 40) }
                    }
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Transparent, t.bg))))
            }
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("FLARE ALERT", 12.sp, t.flareText, mono = true, letter = 2.4.sp)
                T("$senderName sent a Flare", 30.sp, t.ink, FontWeight.ExtraBold, lineHeight = 34.sp)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    if (num.isNotEmpty()) { T(num, 60.sp, t.ink, FontWeight.Medium, mono = true, letter = (-2).sp); Spacer(Modifier.width(10.dp)) }
                    T(unit + (place?.let { " · $it" } ?: ""), 15.sp, t.muted, modifier = Modifier.padding(bottom = 10.dp), lineHeight = 21.sp)
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CIcon("bell", 16.dp, t.muted); Spacer(Modifier.width(8.dp)); T("Ringing until you respond", 13.sp, t.muted, FontWeight.SemiBold)
                }
            }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 66.dp).clip(RoundedCornerShape(50)).background(t.flare).clickable { onStopRing(); view = "map" },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) { CIcon("pin", 20.dp, t.onFlare); Spacer(Modifier.width(8.dp)); T("Open map", 18.sp, t.onFlare, FontWeight.ExtraBold) }
                Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onStopRing(); view = "dismissed" }, contentAlignment = Alignment.Center) {
                    T("Dismiss", 15.sp, t.muted, FontWeight.Bold)
                }
            }
        }

        "map" -> FlareMapView(
            senderName, avatarUrl, num, unit, place, flareLat, flareLng, onWay,
            onBack = onOpenClarion,
            onToggleWay = {
                onWay = !onWay
                if (flareId != null) scope.launch { try { ClarionRepository.respondToFlare(flareId, if (onWay) "on_way" else "seen") } catch (_: Exception) {} }
            },
        )

        else -> Column(Modifier.fillMaxSize().background(Color(0xFF070A12)).statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                T("Alert dismissed", 26.sp, t.ink, FontWeight.ExtraBold)
                T("Clarion won’t ring you again for ${firstName(senderName)}’s Flare.", 14.5.sp, t.muted, lineHeight = 21.sp)
            }
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(50)).background(t.primary).clickable { onOpenClarion() }, contentAlignment = Alignment.Center) {
                    T("Open Clarion", 16.sp, t.onPrimary, FontWeight.ExtraBold)
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { T("Close", 15.sp, t.muted, FontWeight.Bold) }
            }
        }
    }
}

/** Orange pulsing dot with a white rim, centred on [p] (screen pixels). */
@Composable
fun FlareDot(p: Offset, sizeDp: Int) {
    val t = tk()
    val half = with(LocalDensity.current) { (sizeDp / 2).dp.toPx() }
    val f by rememberInfiniteTransition(label = "dot").animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "f")
    Box(Modifier.offset { IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt()) }.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().scale(0.9f + 1.2f * f).alpha(0.55f * (1f - f)).background(t.flare, CircleShape))
        Box(Modifier.fillMaxSize().background(t.flare, CircleShape).border(3.dp, Color.White, CircleShape))
    }
}

@Composable
private fun FlareMapView(
    sender: String, avatarUrl: String?, num: String, unit: String, place: String?,
    flareLat: Double?, flareLng: Double?, onWay: Boolean, onBack: () -> Unit, onToggleWay: () -> Unit,
) {
    val t = tk()
    val context = LocalContext.current
    val density = LocalDensity.current
    val handle = remember { MapHandle() }
    var me by remember { mutableStateOf<com.clarion.app.core.LatLng?>(null) }
    LaunchedEffect(Unit) {
        if (LocationHelper.hasPermission(context)) try { @Suppress("MissingPermission") me = LocationHelper.getCurrentLocation(context) } catch (_: Exception) {}
        val f = if (flareLat != null && flareLng != null) LatLng(flareLat, flareLng) else null
        val m = me?.let { LatLng(it.lat, it.lng) }
        if (f != null && m != null && distanceKm(f.latitude, f.longitude, m.latitude, m.longitude) > 0.03) {
            val side = with(density) { 70.dp.toPx() }.toInt(); val top = with(density) { 140.dp.toPx() }.toInt(); val bottom = with(density) { 260.dp.toPx() }.toInt()
            handle.withMap { it.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().include(f).include(m).build(), side, top, side, bottom), 700) }
        }
    }
    Box(Modifier.fillMaxSize().background(t.bg)) {
        if (flareLat != null && flareLng != null) ClarionMapBox(handle, LatLng(flareLat, flareLng), 16.0)
        Box(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val moved = handle.tick
            me?.let { l -> handle.screen(l.lat, l.lng)?.let { MeDot(it) } }
            if (flareLat != null && flareLng != null) handle.screen(flareLat, flareLng)?.let { FlareDot(it, 34) }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(t.surface).border(1.dp, t.line, CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) { CIcon("back", 20.dp, t.ink, 2.2f) }
            Spacer(Modifier.width(12.dp))
            Row(Modifier.heightIn(min = 46.dp).background(t.surface, RoundedCornerShape(50)).border(1.dp, t.line, RoundedCornerShape(50)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(t.flare, CircleShape)); Spacer(Modifier.width(10.dp))
                T("$sender’s Flare", 13.5.sp, t.ink, FontWeight.Bold, maxLines = 1)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp)
                .background(t.surface, RoundedCornerShape(30.dp)).border(1.dp, t.line, RoundedCornerShape(30.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(t.flare), contentAlignment = Alignment.Center) {
                    if (avatarUrl != null) AsyncImage(avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else T((sender.firstOrNull() ?: '?').uppercase(), 20.sp, t.onFlare, FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    T(sender, 17.sp, t.ink, FontWeight.Bold, maxLines = 1)
                    T((place?.let { "$it · " } ?: "") + "active now", 13.sp, t.muted, maxLines = 1)
                }
                if (num.isNotEmpty()) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(num, 26.sp, t.ink, FontWeight.Medium, mono = true)
                    T(unit.uppercase(), 10.5.sp, t.muted, mono = true, letter = 1.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(50)).background(if (onWay) Color(0xFF12301F) else t.surface2).clickable { onToggleWay() },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onWay) { CIcon("check", 18.dp, Color(0xFF4CC38A), 2.6f); Spacer(Modifier.width(8.dp)) }
                T(if (onWay) "${firstName(sender)} can see you’re coming" else "I’m on my way", 15.sp, if (onWay) Color(0xFF4CC38A) else t.ink, FontWeight.ExtraBold)
            }
        }
    }
}

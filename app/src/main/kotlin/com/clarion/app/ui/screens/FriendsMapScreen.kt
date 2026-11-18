package com.clarion.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarion.app.core.SharedLocation
import com.clarion.app.ui.theme.Safe
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import kotlin.math.roundToInt

/**
 * The Map tab: a clean map with only you and the friends sharing their location with you.
 * Tap a friend to see the way to them; the small Flare button still works here.
 */
@Composable
fun FriendsMapScreen(
    myLoc: com.clarion.app.core.LatLng?,
    sharers: List<SharedLocation>,
    focusId: String?,
    onFocus: (String?) -> Unit,
    holdSeconds: Float,
    onHoldComplete: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val handle = remember { MapHandle() }
    var centered by remember { mutableStateOf(false) }
    val focus = sharers.firstOrNull { it.id == focusId && it.lat != null && it.lng != null }

    fun frame(f: SharedLocation?) {
        val me = myLoc?.let { LatLng(it.lat, it.lng) }
        val fp = f?.let { LatLng(it.lat!!, it.lng!!) }
        val side = with(density) { 70.dp.toPx() }.toInt()
        val top = with(density) { 150.dp.toPx() }.toInt()
        val bottom = with(density) { 300.dp.toPx() }.toInt()
        handle.withMap { m ->
            when {
                fp != null && me != null && distanceKm(me.latitude, me.longitude, fp.latitude, fp.longitude) > 0.03 ->
                    m.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().include(me).include(fp).build(), side, top, side, bottom), 700)
                fp != null -> m.animateCamera(CameraUpdateFactory.newLatLngZoom(fp, 16.0), 700)
                me != null -> m.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 15.0), 700)
            }
        }
    }

    LaunchedEffect(focusId, myLoc != null, focus != null) {
        if (focus != null) frame(focus)
        else if (!centered && myLoc != null) { centered = true; frame(null) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ClarionMapBox(handle, start = myLoc?.let { LatLng(it.lat, it.lng) } ?: CLARION_FALLBACK, zoom = 14.5)

        Box(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val moved = handle.tick
            val me = myLoc?.let { handle.screen(it.lat, it.lng) }
            val fpt = focus?.let { handle.screen(it.lat!!, it.lng!!) }
            if (me != null && fpt != null && myLoc != null && focus != null) {
                val primary = c.primary
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(
                        primary, me, fpt, strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 9.dp.toPx())),
                    )
                }
                val km = distanceKm(myLoc.lat, myLoc.lng, focus.lat!!, focus.lng!!)
                val mid = Offset((me.x + fpt.x) / 2, (me.y + fpt.y) / 2)
                val half = with(density) { 85.dp.toPx() }
                val lh = with(density) { 13.dp.toPx() }
                Box(
                    modifier = Modifier.offset { IntOffset((mid.x - half).roundToInt(), (mid.y - lh).roundToInt()) }.width(170.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${formatKm(km)} · ${walkLabel(km)}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = c.onPrimary,
                        modifier = Modifier.background(c.primary, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            sharers.forEach { s ->
                if (s.lat != null && s.lng != null) handle.screen(s.lat, s.lng)?.let { p -> MapPin(p, s.displayName, s.avatarUrl, s.id == focusId) { onFocus(s.id) } }
            }
            if (me != null) MeDot(me)
        }

        // Everyone sharing with you, one chip each. Swipe sideways when there are many.
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (sharers.isEmpty()) {
                Text(
                    "Nobody is sharing with you right now", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.onBackground,
                    modifier = Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            sharers.forEach { s ->
                val on = s.id == focusId
                val dist = if (myLoc != null && s.lat != null && s.lng != null) formatKm(distanceKm(myLoc.lat, myLoc.lng, s.lat, s.lng)) else "Live"
                Row(
                    modifier = Modifier
                        .then(if (on) Modifier.background(c.primary, RoundedCornerShape(50)) else Modifier.glass(RoundedCornerShape(50)))
                        .clickable { onFocus(s.id) }
                        .padding(start = 5.dp, end = 13.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonAvatar(s.displayName, s.avatarUrl, 30, live = true)
                    Spacer(Modifier.width(8.dp))
                    Text("${firstName(s.displayName)} · $dist", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) c.onPrimary else c.onBackground)
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 72.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapButton("+") { handle.withMap { it.animateCamera(CameraUpdateFactory.zoomIn()) } }
            MapButton("−") { handle.withMap { it.animateCamera(CameraUpdateFactory.zoomOut()) } }
            MapButton("◎") { onFocus(null); frame(null) }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                HoldRoundButton(holdSeconds, onComplete = onHoldComplete)
            }
            if (focus != null) {
                val km = myLoc?.let { distanceKm(it.lat, it.lng, focus.lat!!, focus.lng!!) }
                Row(
                    modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonAvatar(focus.displayName, focus.avatarUrl, 48, live = true)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(focus.displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.onBackground)
                        Text(
                            if (km != null) "Live · ${formatKm(km)} away · ${walkLabel(km)}" else "Live now",
                            fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Safe,
                        )
                        Text("Sharing for ${timeLeftLabel(focus.expiresAt)}", fontSize = 12.5.sp, color = c.onBackground.copy(alpha = 0.6f))
                    }
                    Text(
                        "✕", fontSize = 16.sp, color = c.onBackground,
                        modifier = Modifier.size(38.dp).background(c.onBackground.copy(alpha = 0.08f), CircleShape).clickable { onFocus(null) }.padding(top = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun MapButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(46.dp).glass(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
}

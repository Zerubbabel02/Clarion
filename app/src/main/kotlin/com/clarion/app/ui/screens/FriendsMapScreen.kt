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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarion.app.core.SharedLocation
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import kotlin.math.roundToInt

/** The Map tab (mockup): only you and the friends sharing with you; tap one to see the way there. */
@Composable
fun FriendsMapScreen(
    myLoc: com.clarion.app.core.LatLng?,
    sharers: List<SharedLocation>,
    focusId: String?,
    dockBottom: Dp,
    hideControls: Boolean,
    onFocus: (String?) -> Unit,
    holdSeconds: Float,
    onHoldComplete: () -> Unit,
) {
    val t = tk()
    val density = LocalDensity.current
    val handle = remember { MapHandle() }
    var centered by remember { mutableStateOf(false) }
    val focus = sharers.firstOrNull { it.id == focusId && it.lat != null && it.lng != null }

    fun frame(f: SharedLocation?) {
        val me = myLoc?.let { LatLng(it.lat, it.lng) }
        val fp = f?.let { LatLng(it.lat!!, it.lng!!) }
        val side = with(density) { 70.dp.toPx() }.toInt()
        val top = with(density) { 150.dp.toPx() }.toInt()
        val bottom = with(density) { (dockBottom + 210.dp).toPx() }.toInt()
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

    Box(Modifier.fillMaxSize()) {
        ClarionMapBox(handle, start = myLoc?.let { LatLng(it.lat, it.lng) } ?: CLARION_FALLBACK, zoom = 14.5)

        Box(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val moved = handle.tick
            val me = myLoc?.let { handle.screen(it.lat, it.lng) }
            val fpt = focus?.let { handle.screen(it.lat!!, it.lng!!) }
            if (me != null && fpt != null && myLoc != null && focus != null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(t.primary, me, fpt, strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 9.dp.toPx())))
                }
                val km = distanceKm(myLoc.lat, myLoc.lng, focus.lat!!, focus.lng!!)
                val mid = Offset((me.x + fpt.x) / 2, (me.y + fpt.y) / 2)
                val half = with(density) { 85.dp.toPx() }
                val lh = with(density) { 13.dp.toPx() }
                Box(Modifier.offset { IntOffset((mid.x - half).roundToInt(), (mid.y - lh).roundToInt()) }.width(170.dp), contentAlignment = Alignment.Center) {
                    T("${formatKm(km)} · ${walkLabel(km)}", 11.5.sp, t.onPrimary, FontWeight.Bold, modifier = Modifier.background(t.primary, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            sharers.forEach { s -> if (s.lat != null && s.lng != null) handle.screen(s.lat, s.lng)?.let { p -> MapPin(p, s.displayName, s.avatarUrl, s.id == focusId) { onFocus(s.id) } } }
            if (me != null) MeDot(me, label = true)
        }

        // One chip per friend sharing with you; swipe sideways when there are more than fit.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (sharers.isEmpty()) {
                T("Nobody is sharing with you right now", 12.5.sp, t.ink, FontWeight.Bold, modifier = Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 12.dp))
            }
            sharers.forEach { s ->
                val on = s.id == focusId
                val dist = if (myLoc != null && s.lat != null && s.lng != null) formatKm(distanceKm(myLoc.lat, myLoc.lng, s.lat, s.lng)) else "Live"
                Row(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50))
                        .then(if (on) Modifier.background(t.primary, RoundedCornerShape(50)) else Modifier.glass(RoundedCornerShape(50)))
                        .clickable { onFocus(s.id) }.padding(start = 8.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(s.displayName, s.avatarUrl, 22.dp, live = true)
                    Spacer(Modifier.width(8.dp))
                    T("${firstName(s.displayName)} · $dist", 12.5.sp, if (on) t.onPrimary else t.ink, FontWeight.Bold, maxLines = 1)
                }
            }
        }

        Column(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 72.dp, end = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundButton("plus") { handle.withMap { it.animateCamera(CameraUpdateFactory.zoomIn()) } }
            RoundButton("minus") { handle.withMap { it.animateCamera(CameraUpdateFactory.zoomOut()) } }
            RoundButton("target") { onFocus(null); frame(null) }
        }

        if (!hideControls) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = dockBottom),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { HoldRound(holdSeconds, onComplete = onHoldComplete) }
                if (focus != null) {
                    val km = myLoc?.let { distanceKm(it.lat, it.lng, focus.lat!!, focus.lng!!) }
                    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(focus.displayName, focus.avatarUrl, 46.dp, live = true)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T(focus.displayName, 16.sp, t.ink, FontWeight.Bold, maxLines = 1)
                            T(if (km != null) "Live · ${formatKm(km)} away · ${walkLabel(km)}" else "Live now", 12.5.sp, t.safe, FontWeight.Bold, maxLines = 1)
                            T("Sharing for ${timeLeftLabel(focus.expiresAt)}" + if (focus.expiresAt != null) " more" else "", 12.5.sp, t.muted, maxLines = 1)
                        }
                        RoundButton("x", small = true, glassy = false) { onFocus(null) }
                    }
                }
            }
        }
    }
}

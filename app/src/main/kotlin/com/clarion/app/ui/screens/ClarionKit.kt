package com.clarion.app.ui.screens

import android.os.Bundle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.clarion.app.ui.theme.Flare
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

val CLARION_FALLBACK = LatLng(10.3158, 9.8442) // Bauchi, Nigeria — only until a GPS fix arrives
private const val STYLE_LIGHT = "https://api.maptiler.com/maps/streets-v4/style.json?key=528ExeDeaAf9kydS89ek"
private const val STYLE_DARK = "https://api.maptiler.com/maps/streets-v2-dark/style.json?key=528ExeDeaAf9kydS89ek"

/* ---------------- distance helpers ---------------- */

fun distanceKm(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val dLat = (bLat - aLat) * 111.0
    val dLng = (bLng - aLng) * 111.0 * cos(Math.toRadians(aLat))
    return sqrt(dLat * dLat + dLng * dLng)
}

fun formatKm(km: Double): String =
    if (km < 0.95) "${((km * 1000) / 10).roundToInt() * 10} m" else {
        val t = "%.1f".format(km)
        (if (t.endsWith(".0")) t.dropLast(2) else t) + " km"
    }

fun walkLabel(km: Double): String = "${maxOf(1, (km * 12).roundToInt())} min walk"

/** "1 h 42 min more" / "until they stop" from an ISO expiry time. */
fun timeLeftLabel(expiresAt: String?): String {
    if (expiresAt == null) return "until they stop"
    return try {
        val end = java.time.OffsetDateTime.parse(expiresAt).toInstant()
        val mins = java.time.Duration.between(java.time.Instant.now(), end).toMinutes().coerceAtLeast(0)
        if (mins >= 60) "${mins / 60} h ${mins % 60} min more" else "$mins min more"
    } catch (_: Exception) {
        "a while longer"
    }
}

fun firstName(name: String?): String = name?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() } ?: "Friend"

/* ---------------- map ---------------- */

/** Holds the live map and a counter that ticks whenever the camera moves, so overlays re-place pins. */
class MapHandle {
    var map: MapLibreMap? = null
    var tick by mutableIntStateOf(0)
    private val pending = mutableListOf<(MapLibreMap) -> Unit>()
    fun withMap(block: (MapLibreMap) -> Unit) { map?.let(block) ?: pending.add(block) }
    internal fun ready(m: MapLibreMap) { map = m; pending.forEach { it(m) }; pending.clear(); tick++ }
    fun screen(lat: Double, lng: Double): Offset? =
        map?.projection?.toScreenLocation(LatLng(lat, lng))?.let { Offset(it.x, it.y) }
}

@Composable
fun ClarionMapBox(handle: MapHandle, start: LatLng, zoom: Double, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Same crash-safe setup as FlareMapScreen: created once in remember, textureMode on.
    val mapView = remember {
        MapView(context, MapLibreMapOptions.createFromAttributes(context).apply { textureMode(true) }).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                map.setStyle(if (dark) STYLE_DARK else STYLE_LIGHT)
                map.uiSettings.isCompassEnabled = false
                map.uiSettings.isAttributionEnabled = false
                map.uiSettings.isLogoEnabled = false
                map.cameraPosition = CameraPosition.Builder().target(start).zoom(zoom).build()
                map.addOnCameraMoveListener { handle.tick++ }
                map.addOnCameraIdleListener { handle.tick++ }
                handle.ready(map)
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onStart(); mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
            handle.map = null
        }
    }
    AndroidView(modifier = modifier.fillMaxSize(), factory = { mapView })
}

/* ---------------- small pieces ---------------- */

@Composable
fun Modifier.glass(shape: RoundedCornerShape = RoundedCornerShape(26.dp)): Modifier {
    val c = MaterialTheme.colorScheme
    return this
        .background(c.surface.copy(alpha = 0.9f), shape)
        .border(1.dp, c.onBackground.copy(alpha = 0.12f), shape)
}

@Composable
fun PersonAvatar(name: String?, url: String?, size: Int, live: Boolean = false, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size.dp)
            .then(if (live) Modifier.border(2.dp, Color(0xFF3FAE83), CircleShape) else Modifier)
            .padding(if (live) 3.dp else 0.dp)
            .clip(CircleShape)
            .background(c.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text((name?.trim()?.firstOrNull() ?: '?').uppercase(), fontWeight = FontWeight.Bold, fontSize = (size * 0.38f).sp, color = c.primary)
        }
    }
}

/** A friend's pin placed at a screen point on the map. */
@Composable
fun MapPin(p: Offset, name: String, url: String?, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val d = LocalDensity.current
    val halfW = with(d) { 60.dp.toPx() }
    val halfH = with(d) { 20.dp.toPx() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset { IntOffset((p.x - halfW).roundToInt(), (p.y - halfH).roundToInt()) }
            .width(120.dp)
            .pointerInput(name) { detectTapGestures { onClick() } },
    ) {
        PersonAvatar(name, url, 40, live = true, modifier = Modifier.border(if (selected) 3.dp else 0.dp, c.primary, CircleShape))
        Spacer(Modifier.height(3.dp))
        Text(
            "${firstName(name)} · LIVE",
            fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF06231A), maxLines = 1,
            modifier = Modifier.background(Color(0xFF3FAE83), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

/** Your own blue dot. */
@Composable
fun MeDot(p: Offset) {
    val c = MaterialTheme.colorScheme
    val half = with(LocalDensity.current) { 22.dp.toPx() }
    Box(
        modifier = Modifier
            .offset { IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt()) }
            .size(44.dp)
            .background(c.primary.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(18.dp).background(Color.White, CircleShape).padding(3.dp).background(c.primary, CircleShape))
    }
}

/* ---------------- hold to send ---------------- */

class HoldState {
    var holding by mutableStateOf(false)
    var hint by mutableStateOf<String?>(null)
    val progress = Animatable(0f)
}

/** Press-and-hold logic shared by the big pill and the small round Flare buttons. */
@Composable
fun Modifier.holdToSend(state: HoldState, holdSeconds: Float, onComplete: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.hint) { if (state.hint != null) { delay(2600); state.hint = null } }
    return this.pointerInput(holdSeconds) {
        detectTapGestures(onPress = {
            state.hint = null
            state.holding = true
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            var done = false
            val job = scope.launch {
                state.progress.snapTo(0f)
                state.progress.animateTo(1f, tween((holdSeconds * 1000).toInt(), easing = LinearEasing))
                done = true
                state.holding = false
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onComplete()
                state.progress.snapTo(0f)
            }
            tryAwaitRelease()
            if (!done) {
                job.cancel()
                state.holding = false
                state.hint = "Let go early. Nothing was sent."
                scope.launch { state.progress.animateTo(0f, tween(300)) }
            }
        })
    }
}

@Composable
private fun HoldTag(state: HoldState, holdSeconds: Float, modifier: Modifier) {
    val left = holdSeconds * (1f - state.progress.value)
    val text = when {
        state.holding -> "Keep holding · sending in ${"%.1f".format(left)} s"
        state.hint != null -> state.hint!!
        else -> return
    }
    Text(
        text,
        fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        color = if (state.holding) Color.White else Flare,
        modifier = modifier
            .wrapContentSize(unbounded = true)
            .background(if (state.holding) Flare else MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** Big orange "Hold to send a Flare" pill with fill, countdown and ripples. */
@Composable
fun HoldPillButton(holdSeconds: Float, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val state = remember { HoldState() }
    val ripple = rememberInfiniteTransition(label = "ripple")
    val phase by ripple.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val press by animateFloatAsState(if (state.holding) 0.97f else 1f, label = "press")
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .drawBehind {
                    if (state.holding) for (i in 0..2) {
                        val t = (phase + i / 3f) % 1f
                        val w = size.width * (1f + 0.1f * t); val h = size.height * (1f + 0.75f * t)
                        drawRoundRect(
                            color = Flare.copy(alpha = 0.75f * (1f - t)),
                            topLeft = Offset((size.width - w) / 2, (size.height - h) / 2), size = Size(w, h),
                            cornerRadius = CornerRadius(h / 2, h / 2), style = Stroke(2.dp.toPx()),
                        )
                    }
                }
                .scale(press)
                .clip(RoundedCornerShape(50))
                .background(Flare)
                .holdToSend(state, holdSeconds, onComplete),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().drawBehind {
                drawRect(Color.Black.copy(alpha = 0.22f), size = Size(size.width * state.progress.value, size.height))
            })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("✳", color = Color.White, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Text(if (state.holding) "Keep holding…" else "Hold to send a Flare", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${"%.1f".format(if (state.holding) holdSeconds * (1f - state.progress.value) else holdSeconds)} s",
                    color = Color.White, fontSize = 12.5.sp,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        HoldTag(state, holdSeconds, Modifier.align(Alignment.TopCenter).offset(y = (-58).dp))
    }
}

/** Small round Flare button for the Map tab: ring fills, waves pulse out, it grows a little. */
@Composable
fun HoldRoundButton(holdSeconds: Float, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val state = remember { HoldState() }
    val ripple = rememberInfiniteTransition(label = "wave")
    val phase by ripple.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val grow by animateFloatAsState(if (state.holding) 1.08f else 1f, label = "grow")
    val ink = MaterialTheme.colorScheme.onBackground
    Box(modifier = modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(80.dp)) {
            val r = 32.dp.toPx()
            if (state.holding) for (i in 0..2) {
                val t = (phase + i / 3f) % 1f
                drawCircle(Flare.copy(alpha = 0.5f * (1f - t)), radius = r * (1f + 1.7f * t))
            }
            drawCircle(ink.copy(alpha = 0.18f), radius = 39.dp.toPx(), style = Stroke(4.dp.toPx()))
            drawArc(
                ink, startAngle = -90f, sweepAngle = 360f * state.progress.value, useCenter = false,
                topLeft = Offset(center.x - 39.dp.toPx(), center.y - 39.dp.toPx()), size = Size(78.dp.toPx(), 78.dp.toPx()),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Box(
            modifier = Modifier.size(64.dp).scale(grow).background(Flare, CircleShape).holdToSend(state, holdSeconds, onComplete),
            contentAlignment = Alignment.Center,
        ) { Text("✳", color = Color.White, fontSize = 26.sp) }
        HoldTag(state, holdSeconds, Modifier.align(Alignment.TopEnd).offset(x = 0.dp, y = (-50).dp))
    }
}

@Composable
fun SmallLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

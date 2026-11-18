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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.clarion.app.ui.theme.DmMono
import com.clarion.app.ui.theme.Manrope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

val CLARION_FALLBACK = LatLng(10.3158, 9.8442) // Bauchi, Nigeria — only until a GPS fix arrives
private const val STYLE_LIGHT = "https://api.maptiler.com/maps/streets-v4/style.json?key=528ExeDeaAf9kydS89ek"
private const val STYLE_DARK = "https://api.maptiler.com/maps/streets-v2-dark/style.json?key=528ExeDeaAf9kydS89ek"

/* ================= tokens (the mockup's colours) ================= */

data class Tk(
    val dark: Boolean, val bg: Color, val surface: Color, val surface2: Color, val ink: Color, val muted: Color, val line: Color,
    val primary: Color, val onPrimary: Color, val primarySoft: Color, val flare: Color, val onFlare: Color, val flareText: Color,
    val safe: Color, val safeDot: Color,
)

@Composable
fun tk(): Tk {
    val c = MaterialTheme.colorScheme
    val dark = c.background.luminance() < 0.5f
    return Tk(
        dark, c.background, c.surface, c.surfaceVariant, c.onBackground, c.onSurfaceVariant, c.outline,
        c.primary, c.onPrimary, c.primaryContainer, Color(0xFFFF7A45), Color(0xFF1A0E08),
        if (dark) Color(0xFFFF9A70) else Color(0xFFB5400F),
        if (dark) Color(0xFF4CC38A) else Color(0xFF1F7A57), Color(0xFF3FAE83),
    )
}

/* ================= icons (the mockup's own SVG paths) ================= */

private val iconCache = HashMap<String, ImageVector>()
private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"
private val ICON_PATHS: Map<String, List<String>> = mapOf(
    "home" to listOf("M4 10.6 12 4l8 6.6V20h-5.6v-5.6H9.6V20H4z"),
    "share" to listOf("M20.5 3.5 3.5 10.6l6.6 2.8 2.8 6.6z"),
    "map" to listOf("M9 4 3.5 6v14L9 18l6 2 5.5-2V4L15 6z", "M9 4v14M15 6v14"),
    "user" to listOf(circle(12f, 8.2f, 3.7f), "M4.5 20c0-3.9 3.2-6.4 7.5-6.4s7.5 2.5 7.5 6.4"),
    "people" to listOf(circle(9f, 8.5f, 3.2f), "M3 19.5c0-3.2 2.6-5.3 6-5.3s6 2.1 6 5.3", circle(17.3f, 9.5f, 2.5f), "M16.8 14.4c2.5.2 4.2 2 4.2 4.6"),
    "flare" to listOf("M12 3v4.2M12 16.8V21M3 12h4.2M16.8 12H21M5.6 5.6l3 3M15.4 15.4l3 3M5.6 18.4l3-3M15.4 8.6l3-3", circle(12f, 12f, 1.9f)),
    "target" to listOf(circle(12f, 12f, 7f), circle(12f, 12f, 2.2f), "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3"),
    "pin" to listOf("M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11z", circle(12f, 10f, 2.3f)),
    "chev" to listOf("m6 9 6 6 6-6"), "chevR" to listOf("m9 6 6 6-6 6"), "back" to listOf("m15 5-7 7 7 7"),
    "x" to listOf("M6 6l12 12M18 6 6 18"), "plus" to listOf("M12 5v14M5 12h14"), "minus" to listOf("M5 12h14"),
    "check" to listOf("m5 12.5 4.5 4.5L19 7.5"),
    "moon" to listOf("M20 14.2A8 8 0 1 1 9.8 4a6.5 6.5 0 0 0 10.2 10.2z"),
    "sun" to listOf(circle(12f, 12f, 4f), "M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"),
    "camera" to listOf("M4 8.5h3.2L9 6h6l1.8 2.5H20V19H4z", circle(12f, 13.2f, 3.4f)),
    "at" to listOf(circle(12f, 12f, 3.8f), "M15.8 12v1.4a2.6 2.6 0 0 0 5.2 0V12a9 9 0 1 0-3.6 7.2"),
    "phone" to listOf("M5.5 3.5h3.6l1.8 4.6-2.3 1.5a11 11 0 0 0 5.8 5.8l1.5-2.3 4.6 1.8v3.6a2 2 0 0 1-2 2A16.5 16.5 0 0 1 3.5 5.5a2 2 0 0 1 2-2z"),
    "music" to listOf("M9 18V6.5L20 4.5v11.5", circle(6.5f, 18f, 2.5f), circle(17.5f, 16f, 2.5f)),
    "timer" to listOf(circle(12f, 13.5f, 7.5f), "M12 9.5v4l2.6 2M9.5 2.5h5"),
    "play" to listOf("M8 5.5v13l10.5-6.5z"),
    "stop" to listOf("M8.5 6.5h7a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-7a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z"),
    "shield" to listOf("M12 3 5 6v5.5c0 4.4 3 7.6 7 9 4-1.4 7-4.6 7-9V6z"),
    "search" to listOf(circle(11f, 11f, 6.5f), "m16 16 4.5 4.5"),
    "bell" to listOf("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0"),
)

fun clarionIcon(name: String, stroke: Float = 1.9f): ImageVector = iconCache.getOrPut("$name/$stroke") {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    ICON_PATHS[name].orEmpty().forEach { d ->
        b.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
    b.build()
}

@Composable
fun CIcon(name: String, size: Dp, tint: Color, stroke: Float = 1.9f, modifier: Modifier = Modifier) =
    Icon(clarionIcon(name, stroke), contentDescription = null, tint = tint, modifier = modifier.size(size))

/* ================= helpers ================= */

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

fun timeLeftLabel(expiresAt: String?): String {
    if (expiresAt == null) return "until they stop"
    return try {
        val end = java.time.OffsetDateTime.parse(expiresAt).toInstant()
        val mins = java.time.Duration.between(java.time.Instant.now(), end).toMinutes().coerceAtLeast(0)
        if (mins >= 60) "${mins / 60} h ${mins % 60} min" else "$mins min"
    } catch (_: Exception) {
        "a while"
    }
}

fun firstName(name: String?): String = name?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() } ?: "Friend"
fun fmtSec(v: Float): String = (if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)) + " s"

const val MIN_KM = 0.3f
const val MAX_KM = 5f
fun kmToT(km: Float) = (ln(km / MIN_KM) / ln(MAX_KM / MIN_KM)).coerceIn(0f, 1f)
fun tToKm(t: Float) = MIN_KM * exp(ln(MAX_KM / MIN_KM) * t)

/** Text in the app font with the mockup's sizes. */
@Composable
fun T(
    text: String, size: TextUnit, color: Color, weight: FontWeight = FontWeight.Medium, modifier: Modifier = Modifier,
    mono: Boolean = false, maxLines: Int = Int.MAX_VALUE, letter: TextUnit = TextUnit.Unspecified, lineHeight: TextUnit = TextUnit.Unspecified,
) = Text(
    text, modifier = modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    style = TextStyle(fontFamily = if (mono) DmMono else Manrope, fontSize = size, fontWeight = weight, letterSpacing = letter, lineHeight = lineHeight),
)

/* ================= map ================= */

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
    val dark = tk().dark
    // Created once in remember with textureMode on — the crash-safe setup from FlareMapScreen.
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

/* ================= surfaces ================= */

/** The mockup's see-through "glass" panels. */
@Composable
fun Modifier.glass(shape: RoundedCornerShape = RoundedCornerShape(26.dp)): Modifier {
    val t = tk()
    return this.background(t.surface.copy(alpha = if (t.dark) 0.86f else 0.9f), shape).border(1.dp, t.ink.copy(alpha = 0.16f), shape)
}

@Composable
fun Avatar(name: String?, url: String?, size: Dp, live: Boolean = false, modifier: Modifier = Modifier) {
    val t = tk()
    Box(
        modifier = modifier
            .size(size + if (live) 8.dp else 0.dp)
            .then(if (live) Modifier.border(2.dp, t.safeDot, CircleShape).padding(4.dp) else Modifier)
            .clip(CircleShape)
            .background(t.primarySoft),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else T((name?.trim()?.firstOrNull() ?: '?').uppercase(), (size.value * 0.36f).sp, t.primary, FontWeight.Bold)
    }
}

/** A friend's pin on the map: circle with their initial, name pill underneath. */
@Composable
fun MapPin(p: Offset, name: String, url: String?, selected: Boolean, onClick: () -> Unit) {
    val t = tk()
    val d = LocalDensity.current
    val halfW = with(d) { 60.dp.toPx() }
    val halfH = with(d) { 17.dp.toPx() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset { IntOffset((p.x - halfW).roundToInt(), (p.y - halfH).roundToInt()) }
            .width(120.dp)
            .pointerInput(name) { detectTapGestures { onClick() } },
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(t.safeDot.copy(alpha = 0.32f), CircleShape).padding(4.dp)
                .background(if (selected) t.primary else t.surface, CircleShape).border(2.dp, t.safeDot, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(2.dp).clip(CircleShape))
            else T((name.trim().firstOrNull() ?: '?').uppercase(), 13.sp, if (selected) t.onPrimary else t.ink, FontWeight.Bold)
        }
        Spacer(Modifier.height(3.dp))
        T(
            "${firstName(name)} · LIVE", 10.5.sp, Color(0xFF06231A), FontWeight.Bold, maxLines = 1,
            modifier = Modifier.background(Color(0xFF3FAE83), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

/** Your own position: a 20 dp dot with a white rim and a soft halo. */
@Composable
fun MeDot(p: Offset, label: Boolean = false) {
    val t = tk()
    val half = with(LocalDensity.current) { 24.dp.toPx() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.offset { IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt()) }.width(48.dp),
    ) {
        Box(Modifier.size(48.dp).background(t.primary.copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(20.dp).background(Color.White, CircleShape).padding(3.dp).background(t.primary, CircleShape))
        }
        if (label) T("You", 10.5.sp, t.onPrimary, FontWeight.Bold, modifier = Modifier.offset(y = (-12).dp).background(t.primary, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    T(text.uppercase(), 12.sp, tk().muted, FontWeight.Bold, modifier = modifier, letter = 0.96.sp, maxLines = 1)

@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val t = tk()
    Column(modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(22.dp)).border(1.dp, t.line, RoundedCornerShape(22.dp)).padding(horizontal = 14.dp)) { content() }
}

@Composable
fun LineDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(tk().line))

@Composable
fun PrimaryButton(text: String, icon: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = tk()
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(50)).background(t.primary).clickable { onClick() }.padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { CIcon(icon, 18.dp, t.onPrimary); Spacer(Modifier.width(8.dp)) }
        T(text, 16.sp, t.onPrimary, FontWeight.Bold)
    }
}

@Composable
fun GhostButton(text: String, icon: String? = null, color: Color? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = tk()
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(50)).border(1.5.dp, t.line, RoundedCornerShape(50)).clickable { onClick() }.padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { CIcon(icon, 18.dp, color ?: t.ink, 2.2f); Spacer(Modifier.width(8.dp)) }
        T(text, 14.5.sp, color ?: t.ink, FontWeight.Bold)
    }
}

@Composable
fun MiniButton(text: String, filled: Boolean = false, onClick: () -> Unit) {
    val t = tk()
    T(
        text, 12.5.sp, if (filled) t.onPrimary else t.ink, FontWeight.Bold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(t.primary) else Modifier.border(1.5.dp, t.line, RoundedCornerShape(50)))
            .clickable { onClick() }.padding(horizontal = 13.dp, vertical = 10.dp),
    )
}

/** Round icon button: big glass (46) on maps, small (38) on pages. */
@Composable
fun RoundButton(icon: String, small: Boolean = false, glassy: Boolean = true, onClick: () -> Unit) {
    val t = tk()
    Box(
        modifier = Modifier.size(if (small) 38.dp else 46.dp)
            .then(if (glassy) Modifier.glass(CircleShape) else Modifier.background(t.surface2, CircleShape))
            .clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { CIcon(icon, if (small) 18.dp else 20.dp, t.ink, 2.2f) }
}

/** Segmented control (pill with options), as in the mockup. */
@Composable
fun Seg(options: List<String>, selected: Int, modifier: Modifier = Modifier, small: Boolean = false, onSelect: (Int) -> Unit) {
    val t = tk()
    Row(modifier.background(t.surface2, RoundedCornerShape(50)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = if (small) 34.dp else 40.dp).clip(RoundedCornerShape(50))
                    .background(if (on) t.primary else Color.Transparent).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { T(o, if (small) 12.5.sp else 13.5.sp, if (on) t.onPrimary else t.muted, FontWeight.Bold, maxLines = 1) }
        }
    }
}

@Composable
fun Toast(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    val t = tk()
    T(text, 13.sp, t.bg, FontWeight.SemiBold, modifier = modifier.background(t.ink, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 11.dp))
}

/* ================= radius bar (thin track, 26 dp thumb) ================= */

@Composable
fun RadiusBar(km: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val t = tk()
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 10.dp)) {
            val w = constraints.maxWidth.toFloat()
            val frac = kmToT(km)
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(w) { detectTapGestures { o -> onChange(tToKm((o.x / w).coerceIn(0f, 1f))); onDone() } }
                    .pointerInput(w) { detectHorizontalDragGestures(onDragEnd = onDone) { ch, _ -> onChange(tToKm((ch.position.x / w).coerceIn(0f, 1f))) } }
                    .drawBehind {
                        val y = size.height / 2; val h = 4.dp.toPx(); val ext = 10.dp.toPx()
                        drawRoundRect(t.line, Offset(-ext, y - h / 2), Size(size.width + 2 * ext, h), CornerRadius(h / 2))
                        drawRoundRect(t.ink, Offset(-ext, y - h / 2), Size(size.width * frac + ext, h), CornerRadius(h / 2))
                        val cx = size.width * frac
                        drawCircle(Color.Black.copy(alpha = 0.25f), 10.5.dp.toPx(), Offset(cx, y + 1.dp.toPx()))
                        drawCircle(t.bg, 9.5.dp.toPx(), Offset(cx, y))
                        drawCircle(t.ink, 7.dp.toPx(), Offset(cx, y))
                    },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            T("300 m", 11.sp, t.muted); T("5 km", 11.sp, t.muted)
        }
    }
}

/* ================= "Reaches…" chip and its menu ================= */

@Composable
fun ReachChip(trusted: Boolean, km: Float, trustedCount: Int?, onMode: (Boolean) -> Unit, onChooseCircle: () -> Unit) {
    val t = tk()
    var open by remember { mutableStateOf(false) }
    val chipH = with(LocalDensity.current) { 42.dp.toPx() }.toInt()
    Box {
        Row(
            Modifier.heightIn(min = 34.dp).clip(RoundedCornerShape(50)).background(t.surface).border(1.dp, t.line, RoundedCornerShape(50))
                .clickable { open = !open }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CIcon(if (trusted) "people" else "pin", 15.dp, t.primary)
            Spacer(Modifier.width(6.dp))
            T(if (trusted) "Reaches your trusted circle" + (trustedCount?.let { " · $it" } ?: "") else "Reaches everyone within ${formatKm(km.toDouble())}", 12.5.sp, t.ink, FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            CIcon("chev", 14.dp, t.muted, 2.2f)
        }
        if (open) {
            Popup(alignment = Alignment.BottomCenter, offset = IntOffset(0, -chipH), onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                Column(
                    Modifier.width(306.dp).background(t.surface, RoundedCornerShape(20.dp)).border(1.dp, t.line, RoundedCornerShape(20.dp)).padding(6.dp),
                ) {
                    MenuOption(!trusted, "By distance", "Everyone with Clarion within ${formatKm(km.toDouble())}") { open = false; onMode(false) }
                    MenuOption(trusted, "Trusted circle", trustedCount?.let { if (it > 0) "Only the $it people you chose" else "Nobody added yet" } ?: "Only the people you chose") { open = false; onMode(true) }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open = false; onChooseCircle() }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T("Choose who is in your circle", 13.5.sp, t.primary, FontWeight.Bold, modifier = Modifier.weight(1f))
                        CIcon("chevR", 16.dp, t.primary, 2.2f)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuOption(on: Boolean, title: String, sub: String, onClick: () -> Unit) {
    val t = tk()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) t.primarySoft else Color.Transparent).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Radio(on)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { T(title, 14.5.sp, t.ink, FontWeight.Bold); T(sub, 12.5.sp, t.muted) }
    }
}

@Composable
fun Radio(on: Boolean) {
    val t = tk()
    Box(Modifier.size(20.dp).border(if (on) 6.dp else 2.dp, if (on) t.primary else t.muted, CircleShape))
}

/* ================= hold to send ================= */

class HoldState {
    var holding by mutableStateOf(false)
    var hint by mutableStateOf<String?>(null)
    val progress = Animatable(0f)
}

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

/** "● Keep holding · sending in 0.8 s" — [alignEnd] keeps it inside the screen for the corner button. */
@Composable
private fun HoldTag(state: HoldState, holdSeconds: Float, alignEnd: Boolean, modifier: Modifier) {
    val t = tk()
    val text = when {
        state.holding -> "Keep holding · sending in ${"%.1f".format(holdSeconds * (1f - state.progress.value))} s"
        state.hint != null -> state.hint!!
        else -> return
    }
    val blink = rememberInfiniteTransition(label = "blink").animateFloat(1f, 0.3f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a")
    Row(
        modifier = modifier
            .wrapContentSize(align = if (alignEnd) Alignment.BottomEnd else Alignment.BottomCenter, unbounded = true)
            .background(if (state.holding) t.flare else t.surface, RoundedCornerShape(50))
            .then(if (state.holding) Modifier else Modifier.border(1.dp, t.line, RoundedCornerShape(50)))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.holding) { Box(Modifier.size(8.dp).alpha(blink.value).background(t.onFlare, CircleShape)); Spacer(Modifier.width(8.dp)) }
        T(text, 13.sp, if (state.holding) t.onFlare else t.flareText, FontWeight.Bold, maxLines = 1)
    }
}

/** The big orange "Hold to send a Flare" pill (also the "Test the hold" button when [test]). */
@Composable
fun HoldPill(holdSeconds: Float, onComplete: () -> Unit, test: Boolean = false) {
    val t = tk()
    val state = remember { HoldState() }
    val phase by rememberInfiniteTransition(label = "rip").animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "p")
    val press by animateFloatAsState(if (state.holding) 0.97f else 1f, label = "press")
    val h = if (test) 52.dp else 66.dp
    val fg = if (test) t.ink else t.onFlare
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(h)
                .drawBehind {
                    if (state.holding) for (i in 0..2) {
                        val f = (phase + i / 3f) % 1f
                        val w = size.width * (1f + 0.1f * f); val hh = size.height * (1f + 0.75f * f)
                        drawRoundRect(t.flare.copy(alpha = 0.75f * (1f - f)), Offset((size.width - w) / 2, (size.height - hh) / 2), Size(w, hh), CornerRadius(hh / 2), style = Stroke(2.dp.toPx()))
                    }
                }
                .scale(press).clip(RoundedCornerShape(50)).background(if (test) t.surface2 else t.flare)
                .holdToSend(state, holdSeconds, onComplete),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().drawBehind {
                drawRect(if (test) t.flare.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.24f), size = Size(size.width * state.progress.value, size.height))
            })
            Row(verticalAlignment = Alignment.CenterVertically) {
                CIcon("flare", if (test) 18.dp else 22.dp, fg, 2.2f)
                Spacer(Modifier.width(10.dp))
                T(if (state.holding) "Keep holding…" else if (test) "Test the hold" else "Hold to send a Flare", if (test) 15.sp else 18.sp, fg, FontWeight.ExtraBold, maxLines = 1)
                Spacer(Modifier.width(10.dp))
                T(
                    fmtSec(if (state.holding) holdSeconds * (1f - state.progress.value) else holdSeconds).let { if (state.holding) "%.1f s".format(holdSeconds * (1f - state.progress.value)) else it },
                    12.5.sp, fg, mono = true,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
        if (!test) HoldTag(state, holdSeconds, false, Modifier.align(Alignment.TopCenter).offset(y = (-62).dp))
        else if (state.hint != null) T(state.hint!!, 12.5.sp, t.flareText, FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomCenter).offset(y = 22.dp))
    }
}

/** The small round Flare button on the Map tab: ring fills, waves pulse out, it grows a little. */
@Composable
fun HoldRound(holdSeconds: Float, onComplete: () -> Unit) {
    val t = tk()
    val state = remember { HoldState() }
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "p")
    val grow by animateFloatAsState(if (state.holding) 1.08f else 1f, label = "grow")
    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(80.dp)) {
            val r = 32.dp.toPx()
            if (state.holding) for (i in 0..2) {
                val f = (phase + i / 3f) % 1f
                drawCircle(t.flare.copy(alpha = 0.5f * (1f - f)), radius = r * (1f + 1.7f * f))
            }
            val rr = 38.dp.toPx(); val sw = 3.6.dp.toPx()
            drawCircle(t.ink.copy(alpha = 0.18f), radius = rr, style = Stroke(sw))
            drawArc(t.ink, -90f, 360f * state.progress.value, false, Offset(center.x - rr, center.y - rr), Size(2 * rr, 2 * rr), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Box(Modifier.size(64.dp).scale(grow).background(t.flare, CircleShape).holdToSend(state, holdSeconds, onComplete), contentAlignment = Alignment.Center) {
            CIcon("flare", 26.dp, t.onFlare, 2.3f)
        }
        HoldTag(state, holdSeconds, true, Modifier.align(Alignment.TopEnd).offset(y = (-48).dp))
    }
}

/** Glass panel with a column layout, used for the Flare island and cards. */
@Composable
fun GlassColumn(modifier: Modifier = Modifier, radius: Dp = 30.dp, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(radius)).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
}

@Suppress("unused")
private fun RowScope.unusedMarker() = Unit

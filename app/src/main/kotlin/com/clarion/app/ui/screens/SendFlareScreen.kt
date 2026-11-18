package com.clarion.app.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.FlareResponse
import com.clarion.app.core.LocationHelper
import com.clarion.app.core.Profile
import com.clarion.app.ui.theme.Manrope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed class SendState {
    object Locating : SendState()
    object NoPermission : SendState()
    data class Error(val message: String) : SendState()
    data class Out(val flareId: String) : SendState()
    object Cancelled : SendState()
}

/** Sending a Flare (mockup): locating → "Your Flare is out" with replies → "I'm safe" → cancelled. */
@Composable
fun SendFlareScreen(onCancelFlare: () -> Unit, trustedOnly: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = tk()
    var state by remember { mutableStateOf<SendState>(SendState.Locating) }
    var attempt by remember { mutableIntStateOf(0) }
    var elapsed by remember { mutableIntStateOf(0) }
    var cancelling by remember { mutableStateOf(false) }
    var reach by remember { mutableStateOf("") }
    var replies by remember { mutableStateOf<List<Pair<FlareResponse, Profile?>>>(emptyList()) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { state = SendState.Locating; attempt++ }
    }

    LaunchedEffect(attempt) {
        reach = try {
            if (trustedOnly) "Sent to your trusted circle · ${ClarionRepository.listTrustedMembers().size} people"
            else "Sent to everyone within ${formatKm(ClarionRepository.getMyProfile()?.radiusKm ?: 1.2)}"
        } catch (_: Exception) { if (trustedOnly) "Sent to your trusted circle" else "Sent to everyone nearby" }
        val has = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!has) { state = SendState.NoPermission; return@LaunchedEffect }
        try {
            @Suppress("MissingPermission")
            val loc = LocationHelper.getFastestLocation(context)
            if (loc == null) { state = SendState.Error("Couldn't get a location fix. Move somewhere with a clearer sky and try again."); return@LaunchedEffect }
            // Real lodge name from onboarding: map accuracy is poor here, so the building matters as much as the pin.
            val lodge = try { ClarionRepository.getMyLodgeName() } catch (_: Exception) { null }
            val id = ClarionRepository.sendFlare(loc.lat, loc.lng, locationLabel = lodge, targetMode = if (trustedOnly) "trusted" else "radius")
            state = SendState.Out(id)
        } catch (e: Exception) {
            state = SendState.Error(e.message ?: "Couldn't send your Flare. Try again.")
        }
    }

    LaunchedEffect(state) {
        val s = state
        if (s is SendState.Out) {
            var n = 0
            while (true) {
                if (n % 4 == 0) try { replies = ClarionRepository.flareResponses(s.flareId) } catch (_: Exception) {}
                delay(1000); elapsed++; n++
            }
        }
    }

    // While a Flare is out, only "I'm safe" ends it — Back must not silently leave it running unseen.
    BackHandler(enabled = state is SendState.Out) {}

    Box(Modifier.fillMaxSize().background(t.bg).statusBarsPadding().navigationBarsPadding()) {
        when (val s = state) {
            SendState.Locating -> Centered {
                Spinner()
                T("Getting your location…", 20.sp, t.ink, FontWeight.ExtraBold)
            }
            SendState.NoPermission -> Centered {
                Box(Modifier.size(64.dp).background(t.primarySoft, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) { CIcon("pin", 30.dp, t.primary) }
                T("Location permission needed", 28.sp, t.ink, FontWeight.ExtraBold, lineHeight = 32.sp, modifier = Modifier.fillMaxWidth(), )
                T("Clarion can’t send a Flare without your location. Allow it once and you’re set.", 14.5.sp, t.muted, lineHeight = 21.sp)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Allow location") { permission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                T("Not now", 14.sp, t.muted, FontWeight.SemiBold, modifier = Modifier.clickable { onCancelFlare() }.padding(12.dp))
            }
            is SendState.Error -> Centered {
                Box(Modifier.size(64.dp).background(t.primarySoft, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) { CIcon("flare", 30.dp, t.flare) }
                T("Couldn’t send your Flare", 28.sp, t.ink, FontWeight.ExtraBold)
                T(s.message, 14.5.sp, t.muted, lineHeight = 21.sp)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Try again") { state = SendState.Locating; attempt++ }
                T("Back", 14.sp, t.muted, FontWeight.SemiBold, modifier = Modifier.clickable { onCancelFlare() }.padding(12.dp))
            }
            SendState.Cancelled -> Centered {
                Box(Modifier.size(76.dp).background(if (t.dark) Color(0xFF12301F) else Color(0xFFE3F3EC), CircleShape), contentAlignment = Alignment.Center) { CIcon("check", 36.dp, t.safe, 2.4f) }
                T("Flare cancelled", 28.sp, t.ink, FontWeight.ExtraBold)
                T("You’re marked safe. Nobody will be alerted further.", 14.5.sp, t.muted, lineHeight = 21.sp)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Back to Clarion") { onCancelFlare() }
            }
            is SendState.Out -> Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Beacon()
                    T("FLARE ACTIVE", 12.sp, t.flareText, mono = true, letter = 2.2.sp)
                    T("Your Flare is out", 28.sp, t.ink, FontWeight.ExtraBold)
                    T(reach, 14.5.sp, t.muted)
                    T("%02d:%02d".format(elapsed / 60, elapsed % 60), 54.sp, t.ink, FontWeight.Medium, mono = true)
                    Column(Modifier.fillMaxWidth().heightIn(min = 150.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        replies.sortedByDescending { it.first.status == "on_way" }.forEach { (r, p) ->
                            val name = p?.displayName ?: "Someone"
                            Row(
                                Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(16.dp)).border(1.dp, t.line, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(name, p?.avatarUrl, 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
                                        append(if (r.status == "on_way") " is on the way" else " has seen your Flare")
                                    },
                                    style = TextStyle(fontFamily = Manrope, fontSize = 13.5.sp, color = t.ink),
                                )
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { CIcon("bell", 15.dp, t.muted); Spacer(Modifier.width(6.dp)); T("Your phone stays silent", 12.5.sp, t.muted, FontWeight.SemiBold) }
                        Row(verticalAlignment = Alignment.CenterVertically) { CIcon("shield", 15.dp, t.muted); Spacer(Modifier.width(6.dp)); T("Only you can cancel this", 12.5.sp, t.muted, FontWeight.SemiBold) }
                    }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 62.dp).clip(RoundedCornerShape(50)).background(Color(0xFF3FAE83))
                            .clickable(enabled = !cancelling) {
                                cancelling = true
                                scope.launch {
                                    // Best effort: even if this fails, leaving the screen beats being stuck on it.
                                    try { ClarionRepository.resolveFlare(s.flareId) } catch (_: Exception) {}
                                    state = SendState.Cancelled
                                }
                            },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CIcon("check", 20.dp, Color(0xFF06231A), 2.6f)
                        Spacer(Modifier.width(8.dp))
                        T(if (cancelling) "Cancelling…" else "I’m safe", 17.sp, Color(0xFF06231A), FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        content()
    }
}

@Composable
private fun Spinner() {
    val t = tk()
    val turn by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "r")
    Canvas(Modifier.size(46.dp).rotate(turn)) {
        val w = 4.dp.toPx()
        drawCircle(t.line, radius = size.minDimension / 2 - w / 2, style = Stroke(w))
        drawArc(t.flare, -90f, 90f, false, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Butt))
    }
}

/** Two orange rings pulsing out from the Flare button, as in the mockup. */
@Composable
fun Beacon() {
    val t = tk()
    val p by rememberInfiniteTransition(label = "beacon").animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
        for (i in 0..1) {
            val f = (p + i * 0.5f) % 1f
            Box(Modifier.size(88.dp).scale(0.9f + 1.2f * f).alpha(0.55f * (1f - f)).background(t.flare, CircleShape))
        }
        Box(Modifier.size(64.dp).background(t.flare, CircleShape), contentAlignment = Alignment.Center) { CIcon("flare", 30.dp, t.onFlare, 2.2f) }
    }
}

@Suppress("unused") private val keepTextAlign = TextAlign.Center

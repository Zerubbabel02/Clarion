package com.clarion.app.ui.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.LocationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Night = Color(0xFF0F1420)
private val NightCard = Color(0xFF1B2233)
private val NightTextSoft = Color(0xFFB7C0D6)
private val Safe = Color(0xFF3FAE83)
private val Warn = Color(0xFFE8562E)

private sealed interface SendState {
    data object Sending : SendState
    data class Sent(val flareId: String) : SendState
    data object NoPermission : SendState
    data class Error(val message: String) : SendState
    data object Cancelled : SendState
}

@Composable
fun SendFlareScreen(onCancelFlare: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<SendState>(SendState.Sending) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var cancelling by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            state = SendState.NoPermission
            return@LaunchedEffect
        }

        try {
            @Suppress("MissingPermission")
            val location = LocationHelper.getCurrentLocation(context)
            if (location == null) {
                state = SendState.Error("Couldn't get a location fix. Move somewhere with clearer sky and try again.")
                return@LaunchedEffect
            }
            val flareId = ClarionRepository.sendFlare(location.lat, location.lng, locationLabel = null)
            state = SendState.Sent(flareId)
        } catch (e: Exception) {
            state = SendState.Error(e.message ?: "Couldn't send your Flare. Try again.")
        }
    }

    LaunchedEffect(state) {
        if (state is SendState.Sent) {
            while (true) {
                delay(1000)
                elapsedSeconds += 1
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Night) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            Spacer(modifier = Modifier.height(58.dp))
            Text("DISCREET MODE", color = NightTextSoft, fontSize = 12.sp, letterSpacing = 0.6.sp)

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (val s = state) {
                    is SendState.Sending -> {
                        CircularProgressIndicator(color = NightTextSoft, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.height(18.dp))
                        Text("Getting your location…", color = NightTextSoft, fontSize = 14.sp)
                    }
                    is SendState.NoPermission -> {
                        Text("Location permission needed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Clarion can't send a Flare without your location — grant it in Settings, then come back.",
                            color = NightTextSoft,
                            fontSize = 13.5.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    is SendState.Error -> {
                        Text("Couldn't send your Flare", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(s.message, color = NightTextSoft, fontSize = 13.5.sp, textAlign = TextAlign.Center)
                    }
                    is SendState.Cancelled -> {
                        Text("Flare cancelled", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("You're marked safe. Nobody will be alerted further.", color = NightTextSoft, fontSize = 13.5.sp, textAlign = TextAlign.Center)
                    }
                    is SendState.Sent -> {
                        Box(modifier = Modifier.size(60.dp).background(NightCard, CircleShape))
                        Spacer(modifier = Modifier.height(26.dp))
                        Text("Your Flare is out", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        val mins = elapsedSeconds / 60
                        val secs = elapsedSeconds % 60
                        Text(
                            "Active for %02d:%02d".format(mins, secs),
                            color = NightTextSoft,
                            fontSize = 14.sp,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NightCard, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                        ) {
                            Text(
                                "Your phone stays silent — only you can cancel this",
                                color = NightTextSoft,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            val sentState = state as? SendState.Sent
            if (sentState != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NightCard, RoundedCornerShape(16.dp))
                        .clickable(enabled = !cancelling) {
                            cancelling = true
                            scope.launch {
                                try {
                                    ClarionRepository.resolveFlare(sentState.flareId)
                                } catch (_: Exception) {
                                    // Best-effort: even if this fails, leave discreet mode locally —
                                    // being stuck on this screen is worse than a stale server row.
                                }
                                onCancelFlare()
                            }
                        }
                        .padding(vertical = 17.dp)
                        .padding(bottom = 34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cancelling) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Safe, strokeWidth = 2.dp)
                    } else {
                        Text("I'm Safe — Cancel Flare", color = Safe, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NightCard, RoundedCornerShape(16.dp))
                        .clickable { onCancelFlare() }
                        .padding(vertical = 17.dp)
                        .padding(bottom = 34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Back", color = Warn, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

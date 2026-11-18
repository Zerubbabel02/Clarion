package com.clarion.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarion.app.core.ClarionRepository
import com.clarion.app.core.Profile
import com.clarion.app.core.SharedLocation
import kotlinx.coroutines.launch

/** The Share tab: a pop-up. "Sharing with you" first; tapping a person opens the Map tab on them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    myLoc: com.clarion.app.core.LatLng?,
    sharers: List<SharedLocation>,
    onDismiss: () -> Unit,
    onSeeFriend: (String) -> Unit,
    onShareWithSomeone: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var sharingOut by remember { mutableStateOf<List<Profile>?>(null) }
    LaunchedEffect(Unit) {
        sharingOut = try { ClarionRepository.listActiveShares() } catch (_: Exception) { emptyList() }
    }
    val muted = c.onBackground.copy(alpha = 0.6f)
    val card = Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Share my location", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = c.onBackground)

            SmallLabel("Sharing with you")
            if (sharers.isEmpty()) {
                Text("Nobody is sharing their location with you right now.", fontSize = 13.5.sp, color = muted, modifier = card.padding(vertical = 14.dp))
            } else {
                Column(modifier = card) {
                    sharers.forEach { s ->
                        val dist = if (myLoc != null && s.lat != null && s.lng != null) formatKm(distanceKm(myLoc.lat, myLoc.lng, s.lat, s.lng)) + " away · " else ""
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSeeFriend(s.id) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PersonAvatar(s.displayName, s.avatarUrl, 42, live = true)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onBackground)
                                Text("Live · $dist${timeLeftLabel(s.expiresAt)}", fontSize = 12.5.sp, color = muted)
                            }
                            Text("◎", fontSize = 20.sp, color = c.primary)
                        }
                    }
                }
                Text("Tap someone to see the way from you to them on the map.", fontSize = 12.5.sp, color = muted)
            }

            Spacer(Modifier.height(4.dp))
            SmallLabel("You’re sharing with")
            val out = sharingOut
            when {
                out == null -> Text("Loading…", fontSize = 13.sp, color = muted)
                out.isEmpty() -> Text("You’re not sharing with anyone right now.", fontSize = 13.5.sp, color = muted, modifier = card.padding(vertical = 14.dp))
                else -> Column(modifier = card) {
                    out.forEach { p ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            PersonAvatar(p.displayName, p.avatarUrl, 38)
                            Spacer(Modifier.width(12.dp))
                            Text(p.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onBackground, modifier = Modifier.weight(1f))
                            Text(
                                "Stop", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onBackground,
                                modifier = Modifier.background(c.onBackground.copy(alpha = 0.08f), RoundedCornerShape(50))
                                    .clickable {
                                        scope.launch {
                                            try {
                                                ClarionRepository.stopSharingWith(p.id)
                                                sharingOut = out.filter { it.id != p.id }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().background(c.primary, RoundedCornerShape(50)).clickable { onShareWithSomeone() }.padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Share my location with someone", color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            Text("Separate from Flare alerts. Sharing never rings anyone.", fontSize = 12.5.sp, color = muted)
        }
    }
}

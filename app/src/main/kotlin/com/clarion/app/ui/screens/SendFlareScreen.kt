package com.clarion.app.ui.screens

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF0F1420)
private val NightCard = Color(0xFF1B2233)
private val NightTextSoft = Color(0xFFB7C0D6)
private val Safe = Color(0xFF3FAE83)

@Composable
fun SendFlareScreen(onCancelFlare: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Night) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            Spacer(modifier = Modifier.height(58.dp))
            Text("DISCREET MODE", color = NightTextSoft, fontSize = 12.sp, letterSpacing = 0.6.sp)

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.size(60.dp).background(NightCard, CircleShape),
                )
                Spacer(modifier = Modifier.height(26.dp))
                Text("Your Flare is out", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sent just now", color = NightTextSoft, fontSize = 14.sp)

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
                        textAlign = TextAlign.Start,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NightCard, RoundedCornerShape(16.dp))
                    .clickable { onCancelFlare() }
                    .padding(vertical = 17.dp)
                    .padding(bottom = 34.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("I'm Safe — Cancel Flare", color = Safe, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

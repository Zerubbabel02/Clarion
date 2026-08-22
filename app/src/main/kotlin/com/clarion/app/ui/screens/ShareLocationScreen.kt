package com.clarion.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Warm = Color(0xFFE87A46)
private val WarmSoft = Color(0xFFFBEDE3)

@Composable
fun ShareLocationScreen(onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
            Spacer(modifier = Modifier.height(58.dp))
            ScreenHeader(title = "Share My Location", onBack = onBack)
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WarmSoft, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(44.dp).background(Warm, CircleShape))
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    "Separate from Flare alerts — sharing here never rings anyone.",
                    fontSize = 12.5.sp,
                    color = Color(0xFF7A3F1C),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "SHARING WITH",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .padding(6.dp),
            ) {
                ShareRow(name = "Mum", status = "Live · updated 2 min ago")
                ShareRow(name = "Dara", status = "Live · updated just now")
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Warm, RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp)
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Start Sharing", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            }
        }
    }
}

@Composable
private fun ShareRow(name: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(42.dp).background(Warm, CircleShape))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(status, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
    }
}

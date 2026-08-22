package com.clarion.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val steps = listOf(
    "Verify it's really you" to "Phone verification keeps every Flare trustworthy — no fake alerts, no spam.",
    "Tell us where you are" to "This shapes your close circle — the people who share your building or block.",
    "Two permissions we need" to "Location, so others can be routed to you. Notifications, so a Flare can ring even when Clarion isn't open.",
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp)) {
            Spacer(modifier = Modifier.height(58.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                steps.indices.forEach { i ->
                    Spacer(modifier = Modifier.width(6.dp))
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            Text(steps[step].first, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                steps[step].second,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.weight(1f))

            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    .clickable {
                        if (step < steps.lastIndex) step += 1 else onDone()
                    }
                    .padding(vertical = 16.dp)
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (step < steps.lastIndex) "Continue" else "Enable & Continue",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                )
            }
        }
    }
}

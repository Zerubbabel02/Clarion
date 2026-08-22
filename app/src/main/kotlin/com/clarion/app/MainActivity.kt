package com.clarion.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.clarion.app.ui.screens.HomeScreen
import com.clarion.app.ui.theme.ClarionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClarionTheme {
                HomeScreen()
            }
        }
    }
}

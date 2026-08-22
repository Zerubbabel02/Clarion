package com.clarion.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clarion.app.core.NotificationHelper
import com.clarion.app.ui.screens.CirclesSettingsScreen
import com.clarion.app.ui.screens.HomeScreen
import com.clarion.app.ui.screens.NightModeScreen
import com.clarion.app.ui.screens.OnboardingScreen
import com.clarion.app.ui.screens.SendFlareScreen
import com.clarion.app.ui.screens.ShareLocationScreen
import androidx.compose.ui.platform.LocalContext

@Composable
fun ClarionNavHost(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = ClarionDestinations.HOME) {
        composable(ClarionDestinations.HOME) {
            HomeScreen(
                onSendFlare = { navController.navigate(ClarionDestinations.SEND_FLARE) },
                onShareLocation = { navController.navigate(ClarionDestinations.SHARE_LOCATION) },
                onCirclesSettings = { navController.navigate(ClarionDestinations.CIRCLES_SETTINGS) },
                onSendTestAlert = {
                    NotificationHelper.postFlareAlert(
                        context = context,
                        senderName = "Tunde",
                        distance = "340m",
                        location = "Ridgeway Lodge, Block C",
                    )
                },
            )
        }
        composable(ClarionDestinations.ONBOARDING) {
            OnboardingScreen(onDone = { navController.popBackStack() })
        }
        composable(ClarionDestinations.SEND_FLARE) {
            SendFlareScreen(onCancelFlare = { navController.popBackStack() })
        }
        composable(ClarionDestinations.CIRCLES_SETTINGS) {
            CirclesSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenNightMode = { navController.navigate(ClarionDestinations.NIGHT_MODE) },
            )
        }
        composable(ClarionDestinations.NIGHT_MODE) {
            NightModeScreen(onBack = { navController.popBackStack() })
        }
        composable(ClarionDestinations.SHARE_LOCATION) {
            ShareLocationScreen(onBack = { navController.popBackStack() })
        }
    }
}

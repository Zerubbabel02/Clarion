package com.clarion.app.ui.nav

data class PendingNav(
    val route: String,
    val flareLat: Double? = null,
    val flareLng: Double? = null,
    val flareSender: String? = null,
)

object ClarionDestinations {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val SEND_FLARE = "send_flare"
    const val NIGHT_MODE = "night_mode"
    const val SHARE_LOCATION = "share_location"
    const val FLARE_MAP = "flare_map"
}

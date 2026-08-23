package com.clarion.app.core

import android.content.Context

/**
 * Whether the user has completed onboarding. Plain SharedPreferences is enough for a single
 * boolean flag — no need for DataStore until there's more than one setting living here.
 */
object OnboardingPrefs {
    private const val PREFS_NAME = "clarion_onboarding"
    private const val KEY_COMPLETE = "complete"

    fun isComplete(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_COMPLETE, false)

    fun setComplete(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPLETE, true)
            .apply()
    }
}

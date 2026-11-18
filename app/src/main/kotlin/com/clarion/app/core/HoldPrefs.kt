package com.clarion.app.core

import android.content.Context

/** How long the Flare button must be held before a Flare sends (seconds). */
object HoldPrefs {
    private const val PREFS_NAME = "clarion_hold"
    private const val KEY = "hold_seconds"
    val CHOICES = listOf(1.0f, 1.3f, 1.5f, 2.0f)

    fun get(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY, 1.5f)

    fun set(context: Context, seconds: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putFloat(KEY, seconds).apply()
    }
}

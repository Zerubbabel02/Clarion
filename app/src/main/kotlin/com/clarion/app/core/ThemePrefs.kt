package com.clarion.app.core

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object ThemePrefs {
    private const val PREFS_NAME = "clarion_theme"
    private const val KEY_MODE = "mode"

    fun get(context: Context): ThemeMode {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_MODE, null)
        return ThemeMode.entries.find { it.name == raw } ?: ThemeMode.SYSTEM
    }

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}

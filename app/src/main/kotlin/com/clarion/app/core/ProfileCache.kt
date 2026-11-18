package com.clarion.app.core

import android.content.Context

/**
 * A tiny local cache for the one piece of profile data that needs to render instantly, with
 * zero network wait: the display name shown in Home's greeting. Without this, the greeting
 * visibly flashed "Good evening" alone for a moment on every single app open before the name
 * popped in once the network fetch finished.
 */
object ProfileCache {
    private const val PREFS_NAME = "clarion_profile_cache"
    private const val KEY_NAME = "display_name"

    fun getDisplayName(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_NAME, null)

    fun setDisplayName(context: Context, name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_NAME, name).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

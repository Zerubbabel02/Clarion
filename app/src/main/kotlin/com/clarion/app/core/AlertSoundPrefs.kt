package com.clarion.app.core

import android.content.Context

object AlertSoundPrefs {
    private const val PREFS_NAME = "clarion_alert_sound"
    private const val KEY_URI = "uri"

    /** Null means "use the phone's default ringtone". */
    fun get(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_URI, null)

    fun set(context: Context, uri: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URI, uri)
            .apply()
    }
}

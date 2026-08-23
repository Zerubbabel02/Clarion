package com.clarion.app

import android.app.Application
import org.maplibre.android.MapLibre

class ClarionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}

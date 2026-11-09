package dev.scyde.scycher

import android.app.Application
import dev.scyde.scycher.data.AppRepository
import dev.scyde.scycher.data.ConfigStore
import dev.scyde.scycher.data.WallpaperController
import dev.scyde.scycher.data.WeatherRepository
import dev.scyde.scycher.widgets.WidgetManager

class ScycherApp : Application() {
    lateinit var store: ConfigStore; private set
    val apps by lazy { AppRepository(this, store) }
    val wallpaper by lazy { WallpaperController(this, store) }
    val weather by lazy { WeatherRepository(this, store) }
    val widgets by lazy { WidgetManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = ConfigStore(this)
        // HyperOS likes to switch accessibility services off; with the ADB permission we turn ours back on
        val nav = store.value.nav
        if (nav.keepServiceOn && (nav.enabled || nav.homeRescue) && !dev.scyde.scycher.gestures.SystemActions.isAccessibilityEnabled(this))
            dev.scyde.scycher.gestures.SystemActions.enableServiceDirect(this)
    }

    companion object {
        lateinit var instance: ScycherApp; private set
    }
}

package soko.ekibun.stitch

import android.app.Application

class App : Application() {
    private val bitmapCache by lazy { BitmapCache(this) }
    val stitchInfo by lazy { mutableListOf<Stitch.StitchInfo>() }

    override fun onCreate() {
        super.onCreate()
        bitmapCache.clear()
        app = this
    }

    companion object {
        private lateinit var app: App
        val bitmapCache get() = app.bitmapCache
        val stitchInfo get() = app.stitchInfo
        var foreground = false
    }
}
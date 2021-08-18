package soko.ekibun.stitch

import android.app.Application

class App : Application() {
    private val bitmapCache by lazy { BitmapCache(this) }
    val stitchInfo by lazy { mutableListOf<Stitch.StitchInfo>() }
    val stitchInfo2 by lazy { mutableListOf<Stitch.StitchInfo>() }

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

        fun updateUndo() {
            app.stitchInfo2.clear()
            app.stitchInfo2.addAll(app.stitchInfo.map {
                it.clone()
            })
        }

        fun undo() {
            val last = app.stitchInfo.map { it }
            app.stitchInfo.clear()
            app.stitchInfo.addAll(app.stitchInfo2.map {
                it.clone()
            })
            app.stitchInfo2.clear()
            app.stitchInfo2.addAll(last)
        }
    }
}
package soko.ekibun.stitch

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class QuickTileService : TileService() {

    //从Edit栏添加到快速设定
    override fun onTileAdded() {
        refreshState()
    }

    //打开快速设置
    override fun onStartListening() {
        refreshState()
    }

    // 点击
    override fun onClick() {
        if (qsTile.state == Tile.STATE_ACTIVE) {
            CaptureService.stopService(applicationContext)
            qsTile.state = Tile.STATE_INACTIVE
        } else {
            App.stitchInfo.clear()
            CaptureService.startService(applicationContext)
            qsTile.state = Tile.STATE_ACTIVE
            collapseStatusBar(applicationContext)
        }
        qsTile.updateTile() //更新Tile
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            refreshState()
        }catch (e: Exception) {
            e.printStackTrace()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun refreshState() {
        if (App.foreground) {
            qsTile.state = Tile.STATE_ACTIVE
            qsTile.label = "Stitching"
        } else {
            qsTile.state = Tile.STATE_INACTIVE
            qsTile.label = "Stitch"
        }
        qsTile.updateTile() //更新Tile
    }

    private fun collapseStatusBar(context: Context) {
        try {
            @SuppressLint("WrongConstant")
            val statusBarManager = context.getSystemService("statusbar")
            val collapse = statusBarManager.javaClass.getMethod("collapsePanels")
            collapse.invoke(statusBarManager)
        } catch (localException: Exception) {
            localException.printStackTrace()
        }
    }
}
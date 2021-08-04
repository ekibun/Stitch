@file:Suppress("DEPRECATION")

package soko.ekibun.stitch

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class EditActivity : AppCompatActivity() {
    private val editView by lazy { findViewById<RecyclerView>(R.id.edit) }
    private val guidanceView by lazy { findViewById<View>(R.id.guidance) }
    private val selectInfo by lazy { findViewById<TextView>(R.id.select_info) }
    private val seekbarX by lazy { findViewById<SeekBar>(R.id.seek_x) }
    private val seekbarY by lazy { findViewById<SeekBar>(R.id.seek_y) }
    private val seekbarTrim by lazy { findViewById<SeekBar>(R.id.seek_trim) }
    private val layoutManager by lazy { EditLayoutManager() }
    private val decorator by lazy { EditDecorator(this) }

    val selected by lazy {
        mutableSetOf<Int>()
    }

    private fun updateSelectInfo() {
        guidanceView.visibility = if (App.stitchInfo.isEmpty()) View.VISIBLE else View.INVISIBLE
        selectInfo.text = getString(R.string.label_select, selected.size, App.stitchInfo.size)
        editView.invalidate()
        val selected = App.stitchInfo.filterIndexed { i, it -> i > 0 && selected.contains(it.key) }
        if (selected.isNotEmpty()) {
            seekbarX.progress =
                (selected.map { it.dx.toFloat() / it.width }.average() * 10 + 10).roundToInt()
            seekbarY.progress =
                (selected.map { it.dy.toFloat() / it.height }.average() * 10 + 10).roundToInt()
            seekbarTrim.progress = (selected.map { it.trim }.average() * 20).roundToInt()
        }
    }

    private fun getVersion(context: Context): String {
        var versionName = ""
        var versionCode = 0
        var isApkInDebug = false
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            versionName = pi.versionName
            versionCode = pi.versionCode
            val info = context.applicationInfo
            isApkInDebug = info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return versionName + "-" + (if (isApkInDebug) "debug" else "release") + "(" + versionCode + ")"
    }

    private fun selectAll() {
        selected.clear()
        selected.addAll(App.stitchInfo.map { it.key })
        updateSelectInfo()
    }

    private fun selectClear() {
        selected.clear()
        updateSelectInfo()
    }

    fun selectToggle(info: Stitch.StitchInfo) {
        if (!selected.remove(info.key)) selected.add(info.key)
        updateSelectInfo()
    }

    private fun addImage(uri: Uri) {
        try {
            val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
            val key = App.bitmapCache.saveBitmap(bitmap) ?: return
            val info = Stitch.StitchInfo(key, bitmap.width, bitmap.height)
            App.stitchInfo.add(info)
            selected.add(info.key)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        editView.layoutManager = layoutManager
        editView.adapter = EmptyAdapter()
        editView.addItemDecoration(decorator)

        var beginScale = 0f
        val scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
                    beginScale = layoutManager.scale
                    return super.onScaleBegin(detector)
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val oldScale = layoutManager.scale
                    layoutManager.scale = 0.6f.coerceAtLeast(beginScale * detector.scaleFactor)
                    editView.scrollBy(
                        (detector.focusX * (layoutManager.scale - oldScale) / oldScale).toInt(),
                        (detector.focusY * (layoutManager.scale - oldScale) / oldScale).toInt(),
                    )
                    return super.onScale(detector)
                }
            })
        var tapOnScroll = false
        editView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                tapOnScroll = editView.scrollState != RecyclerView.SCROLL_STATE_IDLE
            }
            if (event.pointerCount > 1) tapOnScroll = false
            if (!tapOnScroll)
                scaleGestureDetector.onTouchEvent(event)
            decorator.onTouch(editView, event)
        }
        val reg = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            selected.clear()
            if (it.resultCode == RESULT_OK) {
                val progress = ProgressDialog.show(this, null, getString(R.string.alert_stitching))
                GlobalScope.launch(Dispatchers.Default) {
                    val clipData = it.data?.clipData
                    if (clipData != null) {
                        val count: Int =
                            clipData.itemCount
                        for (i in 0 until count) {
                            addImage(clipData.getItemAt(i).uri)
                        }
                    } else it.data?.data?.let { path ->
                        addImage(path)
                    }
                    runOnUiThread {
                        progress.cancel()
                        layoutManager.updateRange()
                        editView.invalidate()
                        updateSelectInfo()
                    }
                }
            }
        }
        findViewById<View>(R.id.menu_import).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            reg.launch(Intent.createChooser(intent, "Stitch"))
        }
        findViewById<View>(R.id.menu_capture).setOnClickListener {
            this.startActivity(Intent(this, StartCaptureActivity::class.java))
        }
        findViewById<View>(R.id.menu_select_all).setOnClickListener {
            selectAll()
        }
        findViewById<View>(R.id.menu_select_clear).setOnClickListener {
            selectClear()
        }
        findViewById<View>(R.id.menu_swap).setOnClickListener {
            if (selected.size != 2) {
                Toast.makeText(this, R.string.please_select_swap, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val (i, j) = selected.map { App.stitchInfo.indexOfFirst { info -> info.key == it } }
                .sorted()
            if (i < 0 || j < 0) return@setOnClickListener
            val a = App.stitchInfo[i]
            val b = App.stitchInfo.set(j, App.stitchInfo[i])
            App.stitchInfo[i] = b
            val adx = a.dx
            val ady = a.dy
            a.dx = a.x - b.x + b.dx
            a.dy = a.y - b.y + b.dy
            App.stitchInfo.getOrNull(j + 1)?.let {
                it.dx = it.x - a.x
                it.dy = it.y - a.y
            }
            b.dx = b.x - a.x + adx
            b.dy = b.y - a.y + ady
            App.stitchInfo.getOrNull(i + 1)?.let {
                it.dx = it.x - b.x
                it.dy = it.y - b.y
            }
            layoutManager.updateRange()
            editView.invalidate()
        }
        findViewById<View>(R.id.menu_remove).setOnClickListener {
            if (selected.isEmpty()) {
                Toast.makeText(this, R.string.please_select_image, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.alert_delete, selected.size))
                .setPositiveButton(getString(R.string.alert_ok)) { _: DialogInterface, _: Int ->
                    App.stitchInfo.removeAll { selected.contains(it.key) }
                    selected.clear()
                    layoutManager.updateRange()
                    editView.invalidate()
                    updateSelectInfo()
                }.show()
        }
        findViewById<View>(R.id.menu_save).setOnClickListener {
            if (App.stitchInfo.isEmpty()) {
                Toast.makeText(this, R.string.please_add_image, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val progress = ProgressDialog.show(this, null, getString(R.string.alert_stitching))
            GlobalScope.launch(Dispatchers.Default) {
                val bitmap = decorator.drawToBitmap(layoutManager)
                runOnUiThread {
                    progress.cancel()
                    App.bitmapCache.shareBitmap(this@EditActivity, bitmap)
                }
            }
        }
        findViewById<View>(R.id.menu_auto_stitch).setOnClickListener {
            if (selected.isEmpty()) {
                Toast.makeText(this, R.string.please_select_image, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val progress = ProgressDialog.show(this, null, getString(R.string.alert_computing))
            GlobalScope.launch(Dispatchers.Default) {
                App.stitchInfo.reduceOrNull { acc, it ->
                    if (selected.contains(it.key)) {
                        val img0 = App.bitmapCache.getBitmap(acc.image)
                        val img1 = App.bitmapCache.getBitmap(it.image)
                        if (img0 != null && img1 != null) {
                            Stitch.combine(img0, img1, it)
                        }
                    }
                    it
                }
                runOnUiThread {
                    layoutManager.updateRange()
                    editView.invalidate()
                    updateSelectInfo()
                    progress.cancel()
                }
            }
        }
        val str = getString(R.string.guidance_info, getVersion(this))
        findViewById<TextView>(R.id.guidance_info).text = Html.fromHtml(str)
        findViewById<TextView>(R.id.menu_support).setOnClickListener {
            val intentFullUrl = "intent://platformapi/startapp?saId=10000007&" +
                    "qrcode=https%3A%2F%2Fqr.alipay.com%2Ffkx14754b1r4mkbh6gfgg24#Intent;" +
                    "scheme=alipayqr;package=com.eg.android.AlipayGphone;end"
            try {
                val intent = Intent.parseUri(intentFullUrl, Intent.URI_INTENT_SCHEME)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.support_error, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<TextView>(R.id.menu_github).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://github.com/ekibun/Stitch")
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.open_error, Toast.LENGTH_SHORT).show()
            }
        }

        seekbarX.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                App.stitchInfo.forEach {
                    if (selected.contains(it.key)) it.dx = (progress - 10) * it.width / 10
                }
                layoutManager.updateRange()
                editView.invalidate()
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        seekbarY.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                App.stitchInfo.forEach {
                    if (selected.contains(it.key)) it.dy = (progress - 10) * it.height / 10
                }
                layoutManager.updateRange()
                editView.invalidate()
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        seekbarTrim.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                App.stitchInfo.forEach {
                    if (selected.contains(it.key)) it.trim = progress / 20f
                }
                layoutManager.updateRange()
                editView.invalidate()
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        if (selected.isEmpty()) selectAll()
        layoutManager.updateRange()
        editView.invalidate()
    }
}
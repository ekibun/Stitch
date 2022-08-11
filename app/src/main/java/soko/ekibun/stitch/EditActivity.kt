@file:Suppress("DEPRECATION")

package soko.ekibun.stitch

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class EditActivity : Activity() {
  companion object {
    private const val REQUEST_IMPORT = 1
    private const val REQUEST_IMPORT_NEW = 4
    private const val REQUEST_SAVE = 2
    private const val REQUEST_CAPTURE = 3

    fun startActivity(context: Context, projectKey: String, importGallery: Boolean = false) {
      context.startActivity(Intent(context, EditActivity::class.java).apply {
        putExtra("project", projectKey)
        putExtra("gallery", importGallery)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      })
    }
  }

  private val editView by lazy { findViewById<EditView>(R.id.edit) }
  private val selectInfo by lazy { findViewById<TextView>(R.id.select_info) }
  private val seekDx by lazy { findViewById<RangeSeekbar>(R.id.seek_x) }
  private val seekDy by lazy { findViewById<RangeSeekbar>(R.id.seek_y) }
  private val seekTrim by lazy { findViewById<RangeSeekbar>(R.id.seek_trim) }
  private val seekXRange by lazy { findViewById<RangeSeekbar>(R.id.seek_xrange) }
  private val seekYRange by lazy { findViewById<RangeSeekbar>(R.id.seek_yrange) }
  private val seekRotate by lazy { findViewById<RangeSeekbar>(R.id.seek_rotate) }
  private val seekScale by lazy { findViewById<RangeSeekbar>(R.id.seek_scale) }

  private val projectKey by lazy { intent.extras!!.getString("project")!! }
  val project by lazy { App.getProject(projectKey) }

  private fun invalidateView() {
    editView.update()
    editView.dirty.set(true)
    editView.postInvalidate()
  }

  fun updateSelectInfo() {
    invalidateView()
    selectInfo.text =
      getString(R.string.label_select, project.selected.size, project.stitchInfo.size)
    val selected = project.stitchInfo.filter { project.selected.contains(it.imageKey) }
    if (selected.isNotEmpty()) {
      seekDx.a = selected.map { (it.dx / it.width + 1) / 2 }.average().toFloat()
      seekDy.a = selected.map { (it.dy / it.height + 1) / 2 }.average().toFloat()
      seekTrim.a = selected.map { it.a }.average().toFloat()
      seekTrim.b = selected.map { it.b }.average().toFloat()
      seekXRange.a = selected.map { it.xa }.average().toFloat()
      seekXRange.b = selected.map { it.xb }.average().toFloat()
      seekYRange.a = selected.map { it.ya }.average().toFloat()
      seekYRange.b = selected.map { it.yb }.average().toFloat()
      seekRotate.a = selected.map { (it.drot / 360) + 0.5f }.average().toFloat()
      seekScale.a = selected.map { it.dscale / 2f }.average().toFloat()
      seekDx.invalidate()
      seekDy.invalidate()
      seekTrim.invalidate()
      seekXRange.invalidate()
      seekYRange.invalidate()
      seekRotate.invalidate()
      seekScale.invalidate()
    }
  }

  private fun selectAll() {
    project.selected.clear()
    project.selected.addAll(project.stitchInfo.map { it.imageKey })
    updateSelectInfo()
  }

  private fun selectClear() {
    project.selected.clear()
    updateSelectInfo()
  }

  fun selectToggle(info: Stitch.StitchInfo) {
    if (!project.selected.remove(info.imageKey)) project.selected.add(info.imageKey)
    updateSelectInfo()
  }

  private fun addImage(uri: Uri) {
    try {
      val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
      val key = App.bitmapCache.saveBitmap(projectKey, bitmap)
      val info = Stitch.StitchInfo(key, bitmap.width, bitmap.height)
      project.stitchInfo.add(info)
      project.selected.add(info.imageKey)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun updateSystemUI() {
    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION else 0)
    if (Build.VERSION.SDK_INT < 26) return
    val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    if (!night) window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
        View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or
        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
  }

  private fun importFromGallery(requesetCode: Int = REQUEST_IMPORT) {
    val intent = Intent(Intent.ACTION_GET_CONTENT)
    intent.type = "image/*"
    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    startActivityForResult(Intent.createChooser(intent, "Stitch"), requesetCode)
  }

  private fun stitch(method: Stitch.CombineMethod) {
    if (project.selected.isEmpty()) {
      Toast.makeText(this, R.string.please_select_image, Toast.LENGTH_SHORT).show()
      return
    }
    val progress = ProgressDialog(this)
    var done = 0
    progress.setMessage(getString(R.string.alert_computing, done, project.selected.size))
    progress.show()
    GlobalScope.launch(Dispatchers.IO) {
      project.updateUndo()
      project.stitchInfo.reduceOrNull { acc, it ->
        if (progress.isShowing && project.selected.contains(it.imageKey)) {
          Stitch.combine(method, acc, it)?.let { data ->
            if (progress.isShowing) {
              it.dx = data.dx
              it.dy = data.dy
              it.drot = data.drot
              it.dscale = data.dscale
            }
          }
          ++done
          runOnUiThread {
            progress.setMessage(
              getString(
                R.string.alert_computing,
                done,
                project.selected.size
              )
            )
          }
        }
        it
      }
      runOnUiThread {
        updateSelectInfo()
        progress.cancel()
      }
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_edit)

    if (intent.getBooleanExtra("gallery", false) == true) {
      intent.putExtra("gallery", false)
      importFromGallery(REQUEST_IMPORT_NEW)
      editView.visibility = View.INVISIBLE
    }

    window.decorView.setOnApplyWindowInsetsListener { _, windowInsets ->
      editView.setPadding(
        windowInsets.systemWindowInsetLeft,
        windowInsets.systemWindowInsetTop,
        windowInsets.systemWindowInsetRight,
        0
      )
      findViewById<View>(R.id.panel0).setPadding(
        windowInsets.systemWindowInsetLeft,
        0,
        windowInsets.systemWindowInsetRight,
        0
      )
      findViewById<View>(R.id.panel1).setPadding(
        windowInsets.systemWindowInsetLeft,
        0,
        windowInsets.systemWindowInsetRight,
        0
      )
      findViewById<View>(R.id.panel2).setPadding(
        windowInsets.systemWindowInsetLeft,
        0,
        windowInsets.systemWindowInsetRight,
        windowInsets.systemWindowInsetBottom
      )
      windowInsets.consumeSystemWindowInsets()
    }

    findViewById<View>(R.id.menu_undo).setOnClickListener {
      project.undo()
      project.selected.clear()
      updateSelectInfo()
    }
    findViewById<View>(R.id.menu_import).setOnClickListener {
      importFromGallery()
    }
    findViewById<View>(R.id.menu_capture).setOnClickListener {
      this.startActivityForResult(
        StartCaptureActivity.startActivityIntent(this, projectKey),
        REQUEST_CAPTURE
      )
    }
    findViewById<View>(R.id.menu_select_all).setOnClickListener {
      selectAll()
    }
    findViewById<View>(R.id.menu_select_clear).setOnClickListener {
      selectClear()
    }
    findViewById<View>(R.id.menu_swap).setOnClickListener {
      if (project.selected.size != 2) {
        Toast.makeText(this, R.string.please_select_swap, Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      project.updateUndo()
      val (i, j) = project.selected.map { project.stitchInfo.indexOfFirst { info -> info.imageKey == it } }
      if (i < 0 || j < 0) return@setOnClickListener
      val a = project.stitchInfo[i]
      val b = project.stitchInfo.set(j, a)
      project.stitchInfo[i] = b
      val adx = a.dx
      val ady = a.dy
      val adr = a.drot
      val ads = a.dscale
      a.dx = b.dx
      a.dy = b.dy
      a.drot = b.drot
      a.dscale = b.dscale
      b.dx = adx
      b.dy = ady
      b.drot = adr
      b.dscale = ads
      updateSelectInfo()
    }
    findViewById<View>(R.id.menu_remove).setOnClickListener {
      if (project.selected.isEmpty()) {
        Toast.makeText(this, R.string.please_select_image, Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      AlertDialog.Builder(this)
        .setMessage(getString(R.string.alert_delete, project.selected.size))
        .setNegativeButton(R.string.alert_cancel) { _, _ -> }
        .setPositiveButton(R.string.alert_ok) { _: DialogInterface, _: Int ->
          project.updateUndo()
          project.stitchInfo.removeAll { project.selected.contains(it.imageKey) }
          project.selected.clear()
          updateSelectInfo()
        }.show()
    }
    findViewById<View>(R.id.menu_share).setOnClickListener {
      if (project.stitchInfo.isEmpty()) {
        Toast.makeText(this, R.string.please_add_image, Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      val progress = ProgressDialog.show(this, null, getString(R.string.alert_stitching))
      GlobalScope.launch(Dispatchers.IO) {
        val intent = try {
          val bitmap = editView.drawToBitmap()
          App.bitmapCache.saveToCache(bitmap, "Stitch$projectKey.png")
        } catch (e: Throwable) {
          e
        }
        runOnUiThread {
          progress.cancel()
          val ctx = this@EditActivity
          if (intent is Intent) {
            startActivity(intent)
          } else {
            AlertDialog.Builder(ctx)
              .setTitle(R.string.throw_error)
              .setMessage(Log.getStackTraceString(intent as? Throwable))
              .setPositiveButton(R.string.alert_ok) { _, _ -> }
              .show()
          }
        }
      }
    }
    findViewById<View>(R.id.menu_save).setOnClickListener {
      if (project.stitchInfo.isEmpty()) {
        Toast.makeText(this, R.string.please_add_image, Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
      saveIntent.type = "image/png"
      saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
      saveIntent.putExtra(Intent.EXTRA_TITLE, "Stitch$projectKey.png")
      startActivityForResult(saveIntent, REQUEST_SAVE)
    }
    findViewById<View>(R.id.menu_auto_stitch).setOnLongClickListener {
      val popupMenu = PopupMenu(this, it)
      popupMenu.menuInflater.inflate(R.menu.menu_stitch, popupMenu.menu)
      popupMenu.show()
      popupMenu.setOnMenuItemClickListener { item ->
        when (item.itemId) {
          R.id.menu_find_homography -> stitch(Stitch.CombineMethod.FIND_HOMOGRAPHY)
          R.id.menu_phase_correlate -> stitch(Stitch.CombineMethod.PHASE_CORRELATE)
          R.id.menu_find_homography_diff -> stitch(Stitch.CombineMethod.FIND_HOMOGRAPHY_DIFF)
          R.id.menu_phase_correlate_diff -> stitch(Stitch.CombineMethod.PHASE_CORRELATE_DIFF)
        }
        true
      }
      true
    }
    findViewById<View>(R.id.menu_auto_stitch).setOnClickListener {
      stitch(Stitch.CombineMethod.PHASE_CORRELATE_DIFF)
    }

    seekDx.type = RangeSeekbar.TYPE_CENTER
    seekDx.a = 0.5f
    seekDx.onRangeChange = { a, _ ->
      project.stitchInfo.forEach {
        if (project.selected.contains(it.imageKey)) it.dx = (a * 2 - 1) * it.width
      }
      invalidateView()
    }
    seekDy.type = RangeSeekbar.TYPE_CENTER
    seekDy.a = 0.5f
    seekDy.onRangeChange = { a, _ ->
      project.stitchInfo.forEach {
        if (project.selected.contains(it.imageKey)) it.dy = (a * 2 - 1) * it.height
      }
      invalidateView()
    }
    seekRotate.type = RangeSeekbar.TYPE_CENTER
    seekRotate.a = 0.5f
    seekRotate.onRangeChange = { a, _ ->
      project.stitchInfo.forEach {
        if (project.selected.contains(it.imageKey)) it.drot = (a * 2 - 1) * 180
      }
      invalidateView()
    }
    seekScale.type = RangeSeekbar.TYPE_CENTER
    seekScale.a = 0.5f
    seekScale.onRangeChange = { a, _ ->
      project.stitchInfo.forEach {
        if (project.selected.contains(it.imageKey)) it.dscale = (a * 2)
      }
      invalidateView()
    }
    seekTrim.type = RangeSeekbar.TYPE_GRADIENT
    seekTrim.a = 0.4f
    seekTrim.b = 0.6f
    seekTrim.onRangeChange = { a, b ->
      project.stitchInfo.forEach {
        if (project.selected.contains(it.imageKey)) {
          it.a = a
          it.b = b
        }
      }
      invalidateView()
    }
    seekXRange.onRangeChange = { a, b ->
      project.stitchInfo.forEach {
        if (project.selected.contains(it.imageKey)) {
          it.xa = a
          it.xb = b
        }
      }
      invalidateView()
    }
    seekYRange.onRangeChange = { a, b ->
      project.stitchInfo.forEach {
        if (project.selected.contains(it.imageKey)) {
          it.ya = a
          it.yb = b
        }
      }
      invalidateView()
    }
    selectAll()
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    editView.update()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_IMPORT_NEW && resultCode != RESULT_OK) {
      finish()
    }
    if (resultCode != RESULT_OK) return
    when (requestCode) {
      REQUEST_CAPTURE -> finish()
      REQUEST_IMPORT, REQUEST_IMPORT_NEW -> {
        project.updateUndo()
        project.selected.clear()
        val progress = ProgressDialog.show(
          this, null,
          getString(R.string.alert_reading)
        )
        GlobalScope.launch(Dispatchers.IO) {
          val clipData = data?.clipData
          if (clipData != null) {
            val count: Int =
              clipData.itemCount
            for (i in 0 until count) {
              addImage(clipData.getItemAt(i).uri)
            }
          } else data?.data?.let { path ->
            addImage(path)
          }
          runOnUiThread {
            if (requestCode == REQUEST_IMPORT_NEW) {
              EditActivity.startActivity(this@EditActivity, projectKey)
              finish()
            }
            progress.cancel()
            updateSelectInfo()
          }
        }
      }
      REQUEST_SAVE -> {
        val progress = ProgressDialog.show(
          this, null,
          getString(R.string.alert_stitching)
        )
        GlobalScope.launch(Dispatchers.IO) {
          val err = try {
            val fileOutputStream = contentResolver.openOutputStream(data?.data!!)!!
            val bitmap = editView.drawToBitmap()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.close()
          } catch (e: Throwable) {
            e
          }
          runOnUiThread {
            progress.cancel()
            val ctx = this@EditActivity
            if (err !is Throwable) {
              Toast.makeText(
                ctx,
                R.string.save_success,
                Toast.LENGTH_SHORT
              ).show()
            } else {
              AlertDialog.Builder(ctx)
                .setTitle(R.string.throw_error)
                .setMessage(Log.getStackTraceString(err))
                .setPositiveButton(getString(R.string.alert_ok)) { _, _ -> }
                .show()
            }
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    updateSelectInfo()
    updateSystemUI()
  }

  override fun onBackPressed() {
    if (project.selected.isEmpty())
      super.onBackPressed()
    else selectClear()
  }
}
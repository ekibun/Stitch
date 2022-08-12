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
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

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
  private val seekbar by lazy { findViewById<RangeSeekbar>(R.id.menu_seekbar) }
  private val numberView by lazy { findViewById<View>(R.id.menu_number_picker) }
  private val numberDec by lazy { findViewById<View>(R.id.menu_decrement) }
  private val numberInc by lazy { findViewById<View>(R.id.menu_increment) }
  private val numberA by lazy { findViewById<EditText>(R.id.menu_edit_a) }
  private val numberB by lazy { findViewById<EditText>(R.id.menu_edit_b) }
  private val numberDiv by lazy { findViewById<View>(R.id.menu_edit_divider) }
  private val dropdown by lazy { findViewById<TextView>(R.id.menu_dropdown) }

  data class SelectItemInfo(
    val roundOf: Int,
    val showB: Boolean
  )

  private val selectItems = mapOf(
    R.string.label_dx to (0 to false),
    R.string.label_dy to (0 to false),
    R.string.label_trim to (2 to true),
    R.string.label_xrange to (0 to true),
    R.string.label_yrange to (0 to true),
    R.string.label_scale to (2 to false),
    R.string.label_rotate to (0 to false)
  )
  private var selectIndex = R.string.label_dy

  private val projectKey by lazy { intent.extras!!.getString("project")!! }
  val project by lazy { App.getProject(projectKey) }

  private fun invalidateView() {
    editView.update()
    editView.dirty.set(true)
    editView.postInvalidate()
  }

  private fun updateNumberView(a: Float? = null, b: Float? = null) {
    val (roundOf, showB) = selectItems[selectIndex] ?: (0 to false)
    numberB.visibility = if (showB) View.VISIBLE else View.GONE
    numberDiv.visibility = if (showB) View.VISIBLE else View.GONE
    numberDec.visibility = if (showB) View.GONE else View.VISIBLE
    numberInc.visibility = if (showB) View.GONE else View.VISIBLE
    if (roundOf == 0) {
      if (a != null) numberA.setText(a.roundToInt().toString())
      if (b != null) numberB.setText(b.roundToInt().toString())
    }
    if (a != null) numberA.setText(String.format("%.${roundOf}f", a))
    if (b != null) numberB.setText(String.format("%.${roundOf}f", b))
    if (numberA.isFocused) {
      numberA.clearFocus()
      (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
        numberA.windowToken,
        InputMethodManager.HIDE_NOT_ALWAYS
      )
    }
    if (numberB.isFocused) {
      numberB.clearFocus()
      (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
        numberB.windowToken,
        InputMethodManager.HIDE_NOT_ALWAYS
      )
    }
  }

  fun setNumber(a: Float? = null, b: Float? = null, relative: Boolean = false) {
    val selected = selectedStitchInfo
    if (selected.isNotEmpty()) selected.forEach {
      when (selectIndex) {
        R.string.label_dx -> if (a != null) it.dx = if (relative) (a * 2 - 1) * it.width else a
        R.string.label_dy -> if (a != null) it.dy = if (relative) (a * 2 - 1) * it.height else a
        R.string.label_trim -> {
          if (a != null) it.a = a
          if (b != null) it.b = b
        }
        R.string.label_xrange -> {
          if (a != null) it.xa = if (!relative && it.width > 0) a / it.width else a
          if (b != null) it.xb = if (!relative && it.width > 0) b / it.width else b
        }
        R.string.label_yrange -> {
          if (a != null) it.ya = if (!relative && it.height > 0) a / it.height else a
          if (b != null) it.yb = if (!relative && it.height > 0) b / it.height else b
        }
        R.string.label_scale -> if (a != null) it.dscale = if (relative) (a * 2) else a
        R.string.label_rotate -> if (a != null) it.drot = if (relative) (a * 2 - 1) * 180 else a
      }
    }
  }

  private val selectedStitchInfo
    get() = when (selectIndex) {
      R.string.label_dx,
      R.string.label_dy,
      R.string.label_trim -> project.stitchInfo.filterIndexed { i, v ->
        i > 0 && project.selected.contains(v.imageKey)
      }
      else -> project.stitchInfo.filter { project.selected.contains(it.imageKey) }
    }

  private fun updateNumber() {
    val selected = selectedStitchInfo
    if (selected.isNotEmpty()) {
      numberView.visibility = View.VISIBLE
      when (selectIndex) {
        R.string.label_dx -> {
          updateNumberView(selected.map { it.dx }.average().toFloat())
        }
        R.string.label_dy -> {
          updateNumberView(selected.map { it.dy }.average().toFloat())
        }
        R.string.label_trim -> {
          updateNumberView(
            selected.map { it.a }.average().toFloat(),
            selected.map { it.b }.average().toFloat()
          )
        }
        R.string.label_xrange -> {
          updateNumberView(
            selected.map { it.xa * it.width }.average().toFloat(),
            selected.map { it.xb * it.width }.average().toFloat()
          )
        }
        R.string.label_yrange -> {
          updateNumberView(
            selected.map { it.ya * it.height }.average().toFloat(),
            selected.map { it.yb * it.height }.average().toFloat()
          )
        }
        R.string.label_scale -> {
          updateNumberView(selected.map { it.dscale }.average().toFloat())
        }
        R.string.label_rotate -> {
          updateNumberView(selected.map { it.drot }.average().toFloat())
        }
      }
    } else {
      numberView.visibility = View.GONE
    }
  }

  fun updateSeekbar() {
    val selected = selectedStitchInfo
    if (selected.isNotEmpty()) {
      seekbar.isEnabled = true
      when (selectIndex) {
        R.string.label_dx -> {
          seekbar.type = RangeSeekbar.TYPE_CENTER
          seekbar.a = selected.map { (it.dx / it.width + 1) / 2 }.average().toFloat()
        }
        R.string.label_dy -> {
          seekbar.type = RangeSeekbar.TYPE_CENTER
          seekbar.a = selected.map { (it.dy / it.height + 1) / 2 }.average().toFloat()

          numberB.visibility = View.VISIBLE
          numberDiv.visibility = View.GONE
        }
        R.string.label_trim -> {
          seekbar.type = RangeSeekbar.TYPE_GRADIENT
          seekbar.a = selected.map { it.a }.average().toFloat()
          seekbar.b = selected.map { it.b }.average().toFloat()
        }
        R.string.label_xrange -> {
          seekbar.type = RangeSeekbar.TYPE_RANGE
          seekbar.a = selected.map { it.xa }.average().toFloat()
          seekbar.b = selected.map { it.xb }.average().toFloat()
        }
        R.string.label_yrange -> {
          seekbar.type = RangeSeekbar.TYPE_RANGE
          seekbar.a = selected.map { it.ya }.average().toFloat()
          seekbar.b = selected.map { it.yb }.average().toFloat()
        }
        R.string.label_scale -> {
          seekbar.type = RangeSeekbar.TYPE_CENTER
          seekbar.a = selected.map { it.dscale / 2f }.average().toFloat()
        }
        R.string.label_rotate -> {
          seekbar.type = RangeSeekbar.TYPE_CENTER
          seekbar.a = selected.map { (it.drot / 360) + 0.5f }.average().toFloat()
        }
      }
      seekbar.invalidate()
    } else {
      seekbar.isEnabled = false
    }
  }

  fun updateSelectInfo() {
    dropdown.setText(selectIndex)
    invalidateView()
    selectInfo.text =
      getString(R.string.label_select, project.selected.size, project.stitchInfo.size)
    updateSeekbar()
    updateNumber()
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

  private fun stitch(homo: Boolean, diff: Boolean) {
    if (project.selected.isEmpty()) {
      Toast.makeText(this, R.string.please_select_image, Toast.LENGTH_SHORT).show()
      return
    }
    val progress = ProgressDialog(this)
    var done = 0
    progress.setMessage(getString(R.string.alert_computing, done, project.selected.size))
    progress.show()
    MainScope().launch(Dispatchers.IO) {
      project.updateUndo()
      project.stitchInfo.reduceOrNull { acc, it ->
        if (progress.isShowing && project.selected.contains(it.imageKey)) {
          Stitch.combine(homo, diff, acc, it)?.let { data ->
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

    if (intent.getBooleanExtra("gallery", false)) {
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
      findViewById<View>(R.id.panel).setPadding(
        windowInsets.systemWindowInsetLeft,
        0,
        windowInsets.systemWindowInsetRight,
        0
      )
      windowInsets.consumeSystemWindowInsets()
    }

    findViewById<View>(R.id.menu_undo).setOnClickListener {
      project.undo()
      updateSelectInfo()
    }

    dropdown.setOnClickListener { view ->
      val popupMenu = PopupMenu(this, view)
      selectItems.forEach {
        popupMenu.menu.add(0, it.key, 0, it.key)
        popupMenu.show()
        popupMenu.setOnMenuItemClickListener { item ->
          selectIndex = item.itemId
          updateSelectInfo()
          true
        }
      }
    }

    findViewById<View>(R.id.menu_import).setOnClickListener {
      val popupMenu = PopupMenu(this, it)
      popupMenu.menu.add(0, 1, 0, R.string.import_from_gallery)
      popupMenu.menu.add(0, 2, 0, R.string.import_from_capture)
      popupMenu.show()
      popupMenu.setOnMenuItemClickListener { item ->
        when (item.itemId) {
          1 -> importFromGallery()
          2 -> this.startActivityForResult(
            StartCaptureActivity.startActivityIntent(this, projectKey),
            REQUEST_CAPTURE
          )
        }
        true
      }
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
      MainScope().launch(Dispatchers.IO) {
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
    findViewById<View>(R.id.menu_auto_stitch).setOnClickListener {
      stitch(
        findViewById<Switch>(R.id.switch_homography).isChecked,
        findViewById<Switch>(R.id.switch_diff).isChecked
      )
    }
    numberA.doAfterTextChanged {
      if (!numberA.isFocused) return@doAfterTextChanged
      val newNum = it?.toString()?.toFloatOrNull() ?: return@doAfterTextChanged
      project.updateUndo(numberA)
      setNumber(newNum)
      updateSeekbar()
      invalidateView()
    }
    numberB.doAfterTextChanged {
      if (!numberB.isFocused) return@doAfterTextChanged
      val newNum = it?.toString()?.toFloatOrNull() ?: return@doAfterTextChanged
      project.updateUndo(numberB)
      setNumber(null, newNum)
      updateSeekbar()
      invalidateView()
    }
    numberInc.setOnClickListener {
      val newNum = (numberA.text.toString().toFloatOrNull() ?: 0f) +
          10.0.pow(-(selectItems[selectIndex]?.first ?: 0).toDouble()).toFloat()
      project.updateUndo(numberInc)
      updateNumberView(newNum)
      setNumber(newNum)
      updateSeekbar()
      invalidateView()
    }
    numberDec.setOnClickListener {
      val newNum = (numberA.text.toString().toFloatOrNull() ?: 0f) -
          10.0.pow(-(selectItems[selectIndex]?.first ?: 0).toDouble()).toFloat()
      project.updateUndo(numberDec)
      updateNumberView(newNum)
      setNumber(newNum)
      updateSeekbar()
      invalidateView()
    }
    seekbar.onRangeChange = { a, b ->
      project.updateUndo(seekbar)
      setNumber(a, b, true)
      updateNumber()
      invalidateView()
    }
    seekbar.onTouchUp = {
      project.clearUndoTag()
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
        project.selected.clear()
        val progress = ProgressDialog.show(
          this, null,
          getString(R.string.alert_reading)
        )
        MainScope().launch(Dispatchers.IO) {
          val clipData = data?.clipData
          if (clipData != null) {
            val count: Int =
              clipData.itemCount
            for (i in 0 until count) {
              project.updateUndo(data)
              addImage(clipData.getItemAt(i).uri)
            }
          } else data?.data?.let { path ->
            project.updateUndo(data)
            addImage(path)
          }
          runOnUiThread {
            if (requestCode == REQUEST_IMPORT_NEW) {
              startActivity(this@EditActivity, projectKey)
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
        MainScope().launch(Dispatchers.IO) {
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
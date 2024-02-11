package soko.ekibun.stitch

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class MainActivity : Activity() {

  private data class OpenSource(
    val name: String,
    val desc: String,
    val license: String,
    val url: String
  )

  companion object {
    const val REQUEST_IMPORT = 1

    private val openSources = arrayOf(
      OpenSource(
        "Core Kotlin Extensions",
        "Kotlin extensions for 'core' artifact",
        "Apache License, Version 2.0",
        "https://developer.android.com/jetpack/androidx/releases/core"
      ),
      OpenSource(
        "Kotlinx Coroutines Android",
        "Coroutines support libraries for Kotlin",
        "Apache License, Version 2.0",
        "https://github.com/Kotlin/kotlinx.coroutines"
      ),
      OpenSource(
        "Gson",
        "A Java serialization/deserialization library to convert Java Objects into JSON and back",
        "Apache License, Version 2.0",
        "https://github.com/google/gson"
      ),
      OpenSource(
        "OpenCV",
        "Open Source Computer Vision Library",
        "Apache License, Version 2.0",
        "https://opencv.org"
      )
    )
  }

  private val headerView by lazy {
    @Suppress("InflateParams")
    LayoutInflater.from(this).inflate(R.layout.list_header, null, false)
  }
  private val projectList by lazy { findViewById<ListView>(R.id.proj_list) }
  private val adapter by lazy { ProjectListAdapter(headerView).apply { updateProjects() } }

  private fun getVersion(context: Context): String {
    var versionName = ""
    var versionCode = 0L
    var isApkInDebug = false
    try {
      val pi = context.packageManager.getPackageInfo(context.packageName, 0)
      versionName = pi.versionName
      versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pi.longVersionCode
      } else @Suppress("DEPRECATION") pi.versionCode.toLong()
      val info = context.applicationInfo
      isApkInDebug = info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    } catch (e: PackageManager.NameNotFoundException) {
      e.printStackTrace()
    }
    return versionName + "-" + (if (isApkInDebug) "debug" else "release") + "(" + versionCode + ")"
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    adapter.updateProjects()
  }

  private fun String.toHtml(): CharSequence = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
  } else @Suppress("DEPRECATION") Html.fromHtml(this)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_main)

    projectList.adapter = adapter

    val str = getString(R.string.guidance_info, getVersion(this))
    headerView.findViewById<TextView>(R.id.guidance_info).text = str.toHtml()
    headerView.findViewById<TextView>(R.id.menu_about).setOnClickListener {
      val aboutView = TextView(this)
      aboutView.text = getString(R.string.about_info, getVersion(this)).toHtml()
      val padding = (resources.displayMetrics.density * 24).roundToInt()
      aboutView.setPaddingRelative(padding, padding, padding, 0)
      aboutView.movementMethod = LinkMovementMethod.getInstance()
      AlertDialog.Builder(this)
        .setView(aboutView)
        .setNegativeButton(getString(R.string.menu_opensource)) { _, _ ->
          AlertDialog.Builder(this)
            .setTitle(R.string.menu_opensource)
            .setItems(
              openSources.map {
                "<b>${it.name}</b><br/>${it.license}<br/><i>${it.desc}</i><br/>".toHtml()
              }.toTypedArray()
            ) { _, i ->
              try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(openSources[i].url)
                startActivity(intent)
              } catch (e: Exception) {
                Toast.makeText(this, R.string.open_error, Toast.LENGTH_SHORT).show()
              }
            }
            .setPositiveButton(getString(R.string.alert_ok)) { _, _ -> }
            .show()
        }
        .setNeutralButton(getString(R.string.menu_support)) { _, _ ->
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
        .setPositiveButton(getString(R.string.menu_github)) { _, _ ->
          try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://github.com/ekibun/Stitch")
            startActivity(intent)
          } catch (e: Exception) {
            Toast.makeText(this, R.string.open_error, Toast.LENGTH_SHORT).show()
          }
        }
        .show()
    }
    headerView.findViewById<View>(R.id.guidance_from_gallery).setOnClickListener {
      EditActivity.startActivity(this, App.newProject(), true)
    }
    headerView.findViewById<View>(R.id.guidance_from_capture).setOnClickListener {
      this.startActivity(StartCaptureActivity.startActivityIntent(this, App.newProject()))
    }
    headerView.findViewById<View>(R.id.menu_clear_history).setOnClickListener {
      App.clearProjects()
      adapter.updateProjects()
    }
  }
}
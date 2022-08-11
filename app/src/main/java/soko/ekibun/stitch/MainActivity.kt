package soko.ekibun.stitch

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
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

  companion object {
    const val REQUEST_IMPORT = 1
  }

  private val headerView by lazy {
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
      versionCode = pi.longVersionCode
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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val policyVersion = getString(R.string.policy_version)
    if (App.sp.getString("policy_version", "") != policyVersion) {
      val policyView = TextView(this)
      policyView.text = Html.fromHtml(getString(R.string.policy, Html.FROM_HTML_MODE_LEGACY))
      val padding = (resources.displayMetrics.density * 24).roundToInt()
      policyView.setPaddingRelative(padding, padding, padding, 0)
      policyView.movementMethod = LinkMovementMethod.getInstance()
      AlertDialog.Builder(this).setCancelable(false).setView(policyView)
        .setPositiveButton(R.string.policy_accept) { _, _ ->
          App.sp.edit().putString("policy_version", policyVersion).apply()
        }.setNegativeButton(R.string.policy_dismiss) { _, _ ->
          finish()
        }.show()
    }

    setContentView(R.layout.activity_main)

    projectList.adapter = adapter

    val str = getString(R.string.guidance_info, getVersion(this))
    headerView.findViewById<TextView>(R.id.guidance_info).text = Html.fromHtml(str, Html.FROM_HTML_MODE_LEGACY)
    headerView.findViewById<TextView>(R.id.menu_privacy).setOnClickListener {
      try {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("https://ekibun.github.io/Stitch/$policyVersion/privacy")
        startActivity(intent)
      } catch (e: Exception) {
        Toast.makeText(this, R.string.open_error, Toast.LENGTH_SHORT).show()
      }
    }
    headerView.findViewById<TextView>(R.id.menu_about).setOnClickListener {
      val aboutView = TextView(this)
      aboutView.text = Html.fromHtml(getString(R.string.about_info, getVersion(this)))
      val padding = (resources.displayMetrics.density * 24).roundToInt()
      aboutView.setPaddingRelative(padding, padding, padding, 0)
      aboutView.movementMethod = LinkMovementMethod.getInstance()
      AlertDialog.Builder(this)
        .setView(aboutView)
        .setNegativeButton(getString(R.string.menu_opensource)) { _, _ ->
          val opensourceView = TextView(this)
          opensourceView.text = Html.fromHtml(getString(R.string.opensource))
          opensourceView.setPaddingRelative(padding, padding, padding, 0)
          opensourceView.movementMethod = LinkMovementMethod.getInstance()
          AlertDialog.Builder(this)
            .setView(opensourceView)
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
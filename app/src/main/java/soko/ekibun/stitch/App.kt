package soko.ekibun.stitch

import android.app.Application
import android.content.Context
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors

class App : Application() {
  val sp by lazy { getSharedPreferences(packageName + "_preferences", Context.MODE_PRIVATE)!! }
  private val bitmapCache by lazy { BitmapCache(this) }
  private val projects by lazy { HashMap<String, Stitch.StitchProject>() }

  override fun onCreate() {
    super.onCreate()
    app = this
    val dataVersion = sp.getInt("dataVersion", 0)
    if (dataVersion != DATA_VERSION) {
      clearProjects()
      sp.edit().putInt("dataVersion", DATA_VERSION).apply()
    }
  }

  val dataDirPath: String by lazy {
    applicationInfo.dataDir + File.separator + "Project"
  }

  companion object {
    const val DATA_VERSION = 1

    private lateinit var app: App
    val bitmapCache get() = app.bitmapCache
    var captureProject: String? = null
    val sp get() = app.sp
    val dispatcherIO = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    fun getProject(projectKey: String): Stitch.StitchProject {
      return app.projects.getOrPut(projectKey) {
        Stitch.StitchProject(projectKey)
      }
    }

    fun getProjects(): Array<File> {
      val file = File(app.dataDirPath)
      return file.listFiles { f, _ -> f.isDirectory } ?: emptyArray()
    }

    fun getProjectFile(projectKey: String): File {
      return File(app.dataDirPath + File.separator + projectKey + File.separator + ".project")
    }

    fun newProject(): String = System.currentTimeMillis().toString(16)

    fun clearProjects() {
      val file = File(app.dataDirPath)
      runBlocking(dispatcherIO) {
        file.deleteRecursively()
      }
      app.projects.clear()
    }

    fun deleteProject(projectKey: String) {
      val file = File(app.dataDirPath, projectKey)
      app.projects.remove(projectKey)
      runBlocking(dispatcherIO) {
        file.deleteRecursively()
      }
    }
  }
}
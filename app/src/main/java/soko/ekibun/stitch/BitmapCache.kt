package soko.ekibun.stitch

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class BitmapCache(var app: App) {

  fun saveToCache(bitmap: Bitmap, fileName: String): Intent {
    val imageFile = File(app.cacheDir.path, fileName)
    imageFile.parentFile?.mkdirs()
    val stream = imageFile.outputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    stream.close()

    val contentUri =
      FileProvider.getUriForFile(app, "soko.ekibun.stitch.fileprovider", imageFile)!!
    val shareIntent = Intent()
    shareIntent.action = Intent.ACTION_SEND
    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    shareIntent.setDataAndType(contentUri, "image/png")
    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri)
    return Intent.createChooser(shareIntent, "Stitch")
  }

  private val memoryCache: LruCache<String?, Bitmap?> by lazy {
    val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    LruCache(maxMemory / 8)
  }

  private fun addBitmapToMemoryCache(key: String?, bitmap: Bitmap) {
    memoryCache.put(key, bitmap)
  }

  private fun getBitmapFromDisk(key: String): Bitmap? {
    try {
      return BitmapFactory.decodeFile(app.dataDirPath + File.separator + key)
    } catch (e: IOException) {
      e.printStackTrace()
    }
    return null
  }

  fun getBitmap(key: String): Bitmap? {
    val bitmap = memoryCache[key]
    if (bitmap != null) return bitmap
    val bmp = getBitmapFromDisk(key)
    if (bmp != null) addBitmapToMemoryCache(key, bmp)
    return bmp
  }

  var lastKey: String = ""
  fun saveBitmap(project: String, bmp: Bitmap, saveToMemory: Boolean = true): String {
    val key = project + File.separator + System.currentTimeMillis().toString(16)
    if (lastKey == key) {
      Thread.sleep(1)
      return saveBitmap(project, bmp, saveToMemory)
    }
    lastKey = key
    GlobalScope.launch(Dispatchers.IO) {
      try {
        if (saveToMemory) addBitmapToMemoryCache(key, bmp)
        val file = File(app.dataDirPath, key)
        if (!file.exists()) {
          file.parentFile?.mkdirs()
          file.createNewFile()
        }
        val out = FileOutputStream(file)
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
        out.close()
      } catch (e: Throwable) {
        e.printStackTrace()
      }
    }
    return key
  }
}

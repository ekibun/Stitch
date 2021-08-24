package soko.ekibun.stitch

import android.content.Context
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

class BitmapCache(var context: Context) {

    fun createFileName(): String {
        return context.getString(R.string.app_name) + System.currentTimeMillis()
            .toString(16) + ".png"
    }

    fun saveToCache(bitmap: Bitmap): Intent {
        val fileName = createFileName()
        val imageFile = File(cacheDirPath, fileName)
        imageFile.parentFile?.mkdirs()
        val stream = imageFile.outputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val contentUri =
            FileProvider.getUriForFile(context, "soko.ekibun.stitch.fileprovider", imageFile)!!
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
    private val cacheDirPath: String by lazy {
        context.cacheDir.path + File.separator + "Bitmap"
    }

    fun clear() {
        val file = File(cacheDirPath)
        file.deleteRecursively()
    }

    private fun addBitmapToMemoryCache(key: String?, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
    }

    private fun getBitmapFromDisk(key: String): Bitmap? {
        try {
            return BitmapFactory.decodeFile(cacheDirPath + File.separator + key)
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

    fun saveBitmap(bmp: Bitmap, saveToMemory: Boolean = true): String {
        val key = System.currentTimeMillis().toString(16)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (saveToMemory) addBitmapToMemoryCache(key, bmp)
                val fileFolder = File(cacheDirPath)
                if (!fileFolder.exists()) fileFolder.mkdirs()
                val file = File(cacheDirPath, key)
                if (!file.exists()) file.createNewFile()
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

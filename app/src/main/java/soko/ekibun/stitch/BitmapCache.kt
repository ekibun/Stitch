package soko.ekibun.stitch

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.LruCache
import androidx.core.content.FileProvider
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class BitmapCache(var context: Context) {

    fun shareBitmap(context: Context, bitmap: Bitmap) {
        try {
            val fileName = System.currentTimeMillis().toString(16)
            val imageFile = File(cacheDirPath, fileName)
            imageFile.parentFile?.mkdirs()
            val stream = imageFile.outputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            val contentUri =
                FileProvider.getUriForFile(context, "soko.ekibun.stitch.fileprovider", imageFile)

            if (contentUri != null) {
                val shareIntent = Intent()
                shareIntent.action = Intent.ACTION_SEND
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // temp permission for receiving app to read this file
                shareIntent.setDataAndType(contentUri, "image/*")
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri)
                context.startActivity(Intent.createChooser(shareIntent, "Stitch"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val memoryCache: LruCache<String?, Bitmap?> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        LruCache(maxMemory / 8)
    }
    private val cacheDirPath: String by lazy {
        if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState() || !Environment.isExternalStorageRemovable()) {
            context.externalCacheDir!!.path
        } else {
            context.cacheDir.path
        } + File.separator + "Bitmap"
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

    private val defers = hashMapOf<String, Deferred<Bitmap?>>()

    fun tryGetBitmap(key: String, onGet: (Bitmap?) -> Unit): Bitmap? {
        val bitmap = memoryCache[key]
        if (bitmap == null) GlobalScope.launch {
            val bmp = defers.getOrPut(key) {
                GlobalScope.async {
                    getBitmapFromDisk(key)
                }
            }.await()
            if (bmp != null) addBitmapToMemoryCache(key, bmp)
            onGet(bmp)
            defers.remove(key)?.join()
        }
        return bitmap
    }

    fun getBitmap(key: String): Bitmap? {
        val bitmap = memoryCache[key]
        if (bitmap != null) return bitmap
        val bmp = getBitmapFromDisk(key)
        if (bmp != null) addBitmapToMemoryCache(key, bmp)
        return bmp
    }

    fun saveBitmap(bmp: Bitmap, saveToMemory: Boolean = true): String? {
        try {
            val key = System.currentTimeMillis().toString(16)
            if (saveToMemory) addBitmapToMemoryCache(key, bmp)
            val fileFolder = File(cacheDirPath)
            if (!fileFolder.exists()) fileFolder.mkdirs()
            val file = File(cacheDirPath, key)
            if (!file.exists()) file.createNewFile()
            val out = FileOutputStream(file)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            return key
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }
}

package com.localphoto360.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PhotoRepository(private val context: Context) {

    private val photosDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun list(): List<SpherePhoto> {
        val saved = photosDir.listFiles()
            ?.filter { it.extension.equals("jpg", ignoreCase = true) }
            ?.mapNotNull { file -> readPhoto(file) }
            .orEmpty()
            .sortedByDescending { it.createdAt }
        return listOf(samplePhoto()) + saved
    }

    fun get(id: String): SpherePhoto? {
        if (id == SAMPLE_ID) return samplePhoto()
        return File(photosDir, "$id.jpg").takeIf { it.exists() }?.let(::readPhoto)
    }

    fun saveBitmap(
        bitmap: Bitmap,
        displayName: String,
        photosphere: Boolean,
    ): SpherePhoto {
        val id = UUID.randomUUID().toString()
        val file = File(photosDir, "$id.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        val meta = JSONObject()
            .put("id", id)
            .put("displayName", displayName)
            .put("createdAt", System.currentTimeMillis())
            .put("photosphere", photosphere)
            .put("width", bitmap.width)
            .put("height", bitmap.height)
        File(photosDir, "$id.json").writeText(meta.toString())
        return readPhoto(file)!!
    }

    fun importFrom(uri: Uri): SpherePhoto {
        val id = UUID.randomUUID().toString()
        val file = File(photosDir, "$id.jpg")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open the selected image." }
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val looksLikeSphere = bounds.outWidth > 0 &&
            bounds.outHeight > 0 &&
            bounds.outWidth >= bounds.outHeight * 1.6
        val meta = JSONObject()
            .put("id", id)
            .put("displayName", "Imported 360")
            .put("createdAt", System.currentTimeMillis())
            .put("photosphere", looksLikeSphere)
            .put("width", bounds.outWidth)
            .put("height", bounds.outHeight)
        File(photosDir, "$id.json").writeText(meta.toString())
        return readPhoto(file)!!
    }

    fun delete(id: String) {
        if (id == SAMPLE_ID) return
        File(photosDir, "$id.jpg").delete()
        File(photosDir, "$id.json").delete()
    }

    fun shareUri(photo: SpherePhoto): Uri {
        if (photo.isSample) {
            val cache = File(context.cacheDir, "shared").apply { mkdirs() }
            val copy = File(cache, "sample_sphere.jpg")
            context.assets.open(SAMPLE_ASSET).use { input ->
                FileOutputStream(copy).use { output -> input.copyTo(output) }
            }
            return FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
        }
        val file = File(requireNotNull(photo.filePath))
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun decode(photo: SpherePhoto, maxEdge: Int = 4096): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (photo.isSample) {
            context.assets.open(SAMPLE_ASSET).use { BitmapFactory.decodeStream(it, null, options) }
        } else {
            BitmapFactory.decodeFile(photo.filePath, options)
        }
        var sample = 1
        val longest = maxOf(options.outWidth, options.outHeight)
        while (longest / sample > maxEdge) sample *= 2
        val decode = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val bitmap = if (photo.isSample) {
            context.assets.open(SAMPLE_ASSET).use { BitmapFactory.decodeStream(it, null, decode) }
        } else {
            BitmapFactory.decodeFile(photo.filePath, decode)
        }
        return requireNotNull(bitmap) { "Could not decode ${photo.displayName}" }
    }

    private fun samplePhoto(): SpherePhoto {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(SAMPLE_ASSET).use { BitmapFactory.decodeStream(it, null, options) }
        return SpherePhoto(
            id = SAMPLE_ID,
            displayName = "Sample photosphere",
            createdAt = 0L,
            isPhotosphere = true,
            isSample = true,
            uri = Uri.parse("file:///android_asset/$SAMPLE_ASSET"),
            filePath = null,
            width = options.outWidth,
            height = options.outHeight,
        )
    }

    private fun readPhoto(file: File): SpherePhoto? {
        val id = file.nameWithoutExtension
        val metaFile = File(photosDir, "$id.json")
        val meta = if (metaFile.exists()) JSONObject(metaFile.readText()) else JSONObject()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return SpherePhoto(
            id = meta.optString("id", id),
            displayName = meta.optString("displayName", "Photo"),
            createdAt = meta.optLong("createdAt", file.lastModified()),
            isPhotosphere = meta.optBoolean("photosphere", true),
            isSample = false,
            uri = Uri.fromFile(file),
            filePath = file.absolutePath,
            width = meta.optInt("width", bounds.outWidth),
            height = meta.optInt("height", bounds.outHeight),
        )
    }

    companion object {
        const val SAMPLE_ID = "sample"
        const val SAMPLE_ASSET = "sample_sphere.jpg"
    }
}

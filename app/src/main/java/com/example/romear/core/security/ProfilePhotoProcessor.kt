package com.example.romear.core.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProfilePhotoProcessor {
    suspend fun prepare(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (bounds.outWidth / sample > 2560 || bounds.outHeight / sample > 2560) sample *= 2
        val bitmap = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("Invalid photo")
        val exif = resolver.openInputStream(uri).use { stream -> stream?.let(::ExifInterface) }
        val matrix = Matrix().apply {
            if (exif?.isFlipped == true) postScale(-1f, 1f)
            postRotate((exif?.rotationDegrees ?: 0).toFloat())
        }
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val scale = minOf(1f, 1280f / maxOf(oriented.width, oriented.height))
        val scaled = Bitmap.createScaledBitmap(oriented, (oriented.width * scale).toInt().coerceAtLeast(1), (oriented.height * scale).toInt().coerceAtLeast(1), true)
        val output = File.createTempFile("profile_", ".jpg", context.cacheDir)
        try {
            output.outputStream().use { check(scaled.compress(Bitmap.CompressFormat.JPEG, 80, it)) }
            // Fresh bitmap compression intentionally omits source EXIF, including GPS.
            output
        } catch (failure: Exception) { output.delete(); throw failure }
        finally {
            scaled.recycle()
            if (oriented !== scaled) oriented.recycle()
            if (bitmap !== oriented && bitmap !== scaled) bitmap.recycle()
        }
    }
}

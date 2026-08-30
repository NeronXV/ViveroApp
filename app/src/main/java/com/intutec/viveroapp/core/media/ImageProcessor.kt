package com.intutec.viveroapp.core.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProcessedImage(
    val bytes: ByteArray,
    val mimeType: String = "image/jpeg",
    val extension: String = "jpg",
)

class ImageProcessor @Inject constructor() {
    companion object {
        const val MAX_SIZE_BYTES = 5 * 1024 * 1024
        const val MAX_DIMENSION = 2048
        const val JPEG_QUALITY = 85
        val ALLOWED_MIME = setOf("image/jpeg", "image/png", "image/webp", "image/avif", "image/jpg")
    }

    suspend fun process(context: Context, uri: Uri): Result<ProcessedImage> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: guessMimeFromUri(uri)
            if (mime !in ALLOWED_MIME && !mime.startsWith("image/")) {
                return@withContext Result.failure(IllegalArgumentException("Formato no compatible."))
            }
            // size check before processing
            val size = querySize(resolver, uri)
            if (size != null && size > MAX_SIZE_BYTES) {
                return@withContext Result.failure(IllegalArgumentException("Archivo demasiado grande. Máximo 5 MB."))
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext Result.failure(IllegalStateException("No se pudo leer la imagen."))
            if (bytes.size > MAX_SIZE_BYTES) {
                return@withContext Result.failure(IllegalArgumentException("Archivo demasiado grande. Máximo 5 MB."))
            }
            // decode bounds
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Formato no compatible."))
            }
            // calculate sample size
            var sample = 1
            var w = opts.outWidth
            var h = opts.outHeight
            while (w > MAX_DIMENSION || h > MAX_DIMENSION) {
                sample *= 2
                w /= 2
                h /= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return@withContext Result.failure(IllegalStateException("No se pudo procesar la imagen."))
            // EXIF correction
            bitmap = correctOrientation(resolver, uri, bitmap)
            // scale if still too large
            bitmap = scaleIfNeeded(bitmap)
            // compress to JPEG, stripping GPS/metadata
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            // recycle intermediate
            if (!bitmap.isRecycled) {
                // keep for later
            }
            val compressed = out.toByteArray()
            if (compressed.size > MAX_SIZE_BYTES) {
                return@withContext Result.failure(IllegalArgumentException("Archivo demasiado grande. Máximo 5 MB."))
            }
            Result.success(ProcessedImage(compressed, "image/jpeg", "jpg"))
        } catch (e: Exception) {
            if (e is IllegalArgumentException) Result.failure(e) else Result.failure(IllegalStateException("No pudimos preparar la imagen."))
        }
    }

    private fun guessMimeFromUri(uri: Uri): String {
        val path = uri.toString().lowercase()
        return when {
            path.endsWith(".png") -> "image/png"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".avif") -> "image/avif"
            else -> "image/jpeg"
        }
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long? = try {
        resolver.openFileDescriptor(uri, "r")?.use { it.statSize.takeIf { s -> s > 0 } }
    } catch (_: Exception) { null }

    private fun correctOrientation(resolver: ContentResolver, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val input: InputStream? = resolver.openInputStream(uri)
            val exif = input?.use { ExifInterface(it) } ?: return bitmap
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) { bitmap }
    }

    private fun scaleIfNeeded(bitmap: Bitmap): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        if (max <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / max
        val nw = (bitmap.width * ratio).toInt()
        val nh = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }
}

package ir.dinal.storehub.publishing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProductImageProcessor {
    data class Result(
        val file: File,
        val aiJpeg: File,
        val width: Int,
        val height: Int,
        val backgroundRemoved: Boolean,
        val warning: String? = null
    )

    fun newCameraFile(context: Context): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "capture-${System.currentTimeMillis()}.jpg")
    }

    /**
     * White / light backgrounds connected to the photo edges are removed on-device.
     * No Google ML Kit download is required. WooCommerce gets a transparent WebP.
     */
    suspend fun prepareSmartProductWebp(context: Context, inputUri: Uri, threshold: Int = 48): Result =
        prepareProductImage(context, inputUri, threshold)

    suspend fun retryBackgroundRemoval(context: Context, inputUri: Uri, threshold: Int = 48): Result =
        prepareProductImage(context, inputUri, threshold)

    suspend fun prepareProductImage(context: Context, inputUri: Uri, threshold: Int = 48): Result = withContext(Dispatchers.IO) {
        val original = decodeBitmap(context, inputUri)
        try {
            val working = resizeIfNeeded(original, 1600)
            val aiJpeg = saveJpeg(context, working)
            val cutout = removeWhiteBackground(working, threshold)
            val webp = saveWebp(context, cutout)
            val removed = hasTransparency(cutout)
            val w = cutout.width
            val h = cutout.height
            if (cutout !== working && cutout !== original && !cutout.isRecycled) cutout.recycle()
            if (working !== original && !working.isRecycled) working.recycle()
            Result(
                file = webp,
                aiJpeg = aiJpeg,
                width = w,
                height = h,
                backgroundRemoved = removed,
                warning = if (removed) null else "پس‌زمینه سفید خیلی کم تشخیص داده شد. کالا را روی زمینه سفید عکس بگیر یا حساسیت را بالا ببر."
            )
        } finally {
            if (!original.isRecycled) original.recycle()
        }
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val side = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (side / sample > 3200) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("فایل تصویر باز نشد.")
        return rotateFromExif(context, uri, decoded)
    }

    private fun rotateFromExif(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: return bmp
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.preScale(1f, -1f)
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** Flood-fill light pixels connected to the photo border. Interior whites (logo, product) stay. */
    internal fun removeWhiteBackground(src: Bitmap, threshold: Int): Bitmap {
        val w = src.width
        val h = src.height
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val bg = backgroundColor(px, w, h)
        val bgR = Color.red(bg)
        val bgG = Color.green(bg)
        val bgB = Color.blue(bg)
        val limit = threshold.toDouble()
        fun isBackground(color: Int): Boolean {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            if (r < 165 && g < 165 && b < 165) return false
            val dist = sqrt(((r - bgR) * (r - bgR) + (g - bgG) * (g - bgG) + (b - bgB) * (b - bgB)).toDouble())
            return dist <= limit
        }
        val mask = BooleanArray(w * h)
        val q = ArrayDeque<Int>()
        fun tryEnqueue(i: Int) {
            if (i < 0 || i >= px.size || mask[i]) return
            if (isBackground(px[i])) {
                mask[i] = true
                q.add(i)
            }
        }
        for (x in 0 until w) {
            tryEnqueue(x)
            tryEnqueue((h - 1) * w + x)
        }
        for (y in 0 until h) {
            tryEnqueue(y * w)
            tryEnqueue(y * w + w - 1)
        }
        while (q.isNotEmpty()) {
            val i = q.removeFirst()
            val x = i % w
            val y = i / w
            if (x > 0) tryEnqueue(i - 1)
            if (x < w - 1) tryEnqueue(i + 1)
            if (y > 0) tryEnqueue(i - w)
            if (y < h - 1) tryEnqueue(i + w)
        }
        val out = IntArray(w * h)
        val feather = 3
        for (i in px.indices) {
            out[i] = if (mask[i]) Color.TRANSPARENT else (px[i] or (0xFF shl 24))
        }
        if (feather > 0) {
            val copy = out.copyOf()
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val i = y * w + x
                    if (mask[i]) continue
                    var nearest = feather + 1
                    loop@ for (dy in -feather..feather) {
                        val yy = y + dy
                        if (yy < 0 || yy >= h) continue
                        for (dx in -feather..feather) {
                            val xx = x + dx
                            if (xx < 0 || xx >= w) continue
                            if (mask[yy * w + xx]) {
                                val d = abs(dx) + abs(dy)
                                if (d < nearest) nearest = d
                                if (nearest <= 1) break@loop
                            }
                        }
                    }
                    if (nearest <= feather) {
                        val a = (255f * (nearest.toFloat() / (feather + 1f))).toInt().coerceIn(0, 255)
                        out[i] = (a shl 24) or (copy[i] and 0x00FFFFFF)
                    }
                }
            }
        }
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun backgroundColor(px: IntArray, w: Int, h: Int): Int {
        val samples = intArrayOf(
            px[0], px[w - 1], px[(h - 1) * w], px[h * w - 1],
            px[w / 2], px[(h - 1) * w + w / 2], px[(h / 2) * w], px[(h / 2) * w + w - 1]
        )
        var r = 0
        var g = 0
        var b = 0
        var n = 0
        for (c in samples) {
            val rr = Color.red(c)
            val gg = Color.green(c)
            val bb = Color.blue(c)
            if (rr + gg + bb >= 540) {
                r += rr; g += gg; b += bb; n++
            }
        }
        if (n == 0) return Color.WHITE
        return Color.rgb(r / n, g / n, b / n)
    }

    private fun hasTransparency(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height
        val step = max(1, minOf(w, h) / 40)
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                if (Color.alpha(bmp.getPixel(x, y)) < 250) return true
            }
        }
        return false
    }

    private fun saveWebp(context: Context, bitmap: Bitmap): File {
        val outDir = File(context.filesDir, "product_images").apply { mkdirs() }
        val out = File(outDir, "product-${System.currentTimeMillis()}.webp")
        FileOutputStream(out).use { stream ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (hasTransparency(bitmap)) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            val quality = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && format == Bitmap.CompressFormat.WEBP_LOSSLESS) 100 else 92
            if (!bitmap.compress(format, quality, stream)) error("ساخت فایل WebP ناموفق بود.")
        }
        return out
    }

    private fun saveJpeg(context: Context, bitmap: Bitmap): File {
        val flat = if (bitmap.hasAlpha()) {
            val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(out).apply {
                drawColor(Color.WHITE)
                drawBitmap(bitmap, 0f, 0f, null)
            }
            out
        } else bitmap
        val outDir = File(context.cacheDir, "ai").apply { mkdirs() }
        val out = File(outDir, "ai-${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { stream ->
            if (!flat.compress(Bitmap.CompressFormat.JPEG, 88, stream)) error("ساخت JPEG ناموفق بود.")
        }
        if (flat !== bitmap && !flat.isRecycled) flat.recycle()
        return out
    }

    private fun resizeIfNeeded(src: Bitmap, maxSide: Int): Bitmap {
        val max = maxOf(src.width, src.height)
        if (max <= maxSide) return src
        val scale = maxSide.toFloat() / max.toFloat()
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }
}

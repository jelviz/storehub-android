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

    suspend fun jpegForVision(context: Context, inputUri: Uri): File = withContext(Dispatchers.IO) {
        val original = decodeBitmap(context, inputUri)
        val working = resizeIfNeeded(original, 1600)
        try {
            saveJpeg(context, working)
        } finally {
            if (working !== original && !working.isRecycled) working.recycle()
            if (!original.isRecycled) original.recycle()
        }
    }

    suspend fun prepareProductImage(context: Context, inputFile: File, threshold: Int = 48): Result =
        prepareProductImage(context, Uri.fromFile(inputFile), threshold)

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

    private fun openImageStream(context: Context, uri: Uri): java.io.InputStream {
        if (uri.scheme == "file") {
            val path = uri.path ?: error("فایل تصویر باز نشد.")
            return java.io.FileInputStream(File(path))
        }
        return context.contentResolver.openInputStream(uri) ?: error("فایل تصویر باز نشد.")
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openImageStream(context, uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        val side = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (side / sample > 3200) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = openImageStream(context, uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("فایل تصویر باز نشد.")
        return rotateFromExif(context, uri, decoded)
    }

    private fun rotateFromExif(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val orientation = runCatching {
            openImageStream(context, uri).use {
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

    /**
     * Flood-fill from the photo border: paper, off-white, and connected gray shadows
     * become transparent. Interior whites (logo print) stay. Dark / colorful product pixels stay.
     */
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
        val bgL = luma(bgR, bgG, bgB)
        val bgC = chroma(bgR, bgG, bgB)

        val t01 = ((threshold.coerceIn(20, 100) - 20) / 80.0)
        val minLuma = (bgL - (72.0 + t01 * 115.0)).coerceAtLeast(82.0)
        val maxChroma = (16.0 + bgC + t01 * 30.0).coerceAtMost(58.0)
        val colorTol = 36.0 + t01 * 90.0

        fun isBackground(color: Int, loose: Boolean): Boolean {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val L = luma(r, g, b)
            val C = chroma(r, g, b)
            val floor = if (loose) (minLuma - 14.0).coerceAtLeast(70.0) else minLuma
            if (L < floor) return false
            val dist = colorDist(r, g, b, bgR, bgG, bgB)
            if (dist <= if (loose) colorTol + 18.0 else colorTol) return true
            val chromaLimit = if (loose) maxChroma + 12.0 else maxChroma
            if (C <= chromaLimit) return true
            val paperLike =
                abs((r - g) - (bgR - bgG)) < 22 &&
                    abs((g - b) - (bgG - bgB)) < 22 &&
                    C <= chromaLimit + 10.0
            return paperLike
        }

        val mask = BooleanArray(w * h)
        floodFromBorder(px, mask, w, h) { isBackground(it, loose = false) }
        growFromMask(px, mask, w, h) { isBackground(it, loose = true) }
        dilateMask(mask, w, h, if (t01 >= 0.45) 2 else 1)
        dropGrayIslands(px, mask, w, h)

        val out = IntArray(w * h)
        for (i in px.indices) {
            out[i] = if (mask[i]) Color.TRANSPARENT else (px[i] or (0xFF shl 24))
        }
        featherEdge(out, mask, w, h, 2)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun floodFromBorder(px: IntArray, mask: BooleanArray, w: Int, h: Int, pred: (Int) -> Boolean) {
        val q = ArrayDeque<Int>()
        fun tryEnqueue(i: Int) {
            if (i < 0 || i >= px.size || mask[i]) return
            if (pred(px[i])) {
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
        drainEight(q, mask, w, h, px, pred)
    }

    private fun growFromMask(px: IntArray, mask: BooleanArray, w: Int, h: Int, pred: (Int) -> Boolean) {
        val q = ArrayDeque<Int>()
        for (i in mask.indices) if (mask[i]) q.add(i)
        drainEight(q, mask, w, h, px, pred)
    }

    private fun drainEight(
        q: ArrayDeque<Int>,
        mask: BooleanArray,
        w: Int,
        h: Int,
        px: IntArray,
        pred: (Int) -> Boolean
    ) {
        while (q.isNotEmpty()) {
            val i = q.removeFirst()
            val x = i % w
            val y = i / w
            for (dy in -1..1) {
                val yy = y + dy
                if (yy < 0 || yy >= h) continue
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = x + dx
                    if (xx < 0 || xx >= w) continue
                    val n = yy * w + xx
                    if (mask[n]) continue
                    if (pred(px[n])) {
                        mask[n] = true
                        q.add(n)
                    }
                }
            }
        }
    }

    private fun dilateMask(mask: BooleanArray, w: Int, h: Int, radius: Int) {
        if (radius <= 0) return
        val src = mask.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (src[i]) continue
                var hit = false
                loop@ for (dy in -radius..radius) {
                    val yy = y + dy
                    if (yy < 0 || yy >= h) continue
                    for (dx in -radius..radius) {
                        val xx = x + dx
                        if (xx < 0 || xx >= w) continue
                        if (src[yy * w + xx]) {
                            hit = true
                            break@loop
                        }
                    }
                }
                if (hit) mask[i] = true
            }
        }
    }

    /** Leftover gray JPEG crumbs that are not part of the main product become transparent. */
    private fun dropGrayIslands(px: IntArray, mask: BooleanArray, w: Int, h: Int) {
        val n = w * h
        val seen = BooleanArray(n)
        data class Blob(val pixels: IntArray, val solid: Int, val meanL: Double)
        val blobs = ArrayList<Blob>()
        var largest = 0
        for (start in 0 until n) {
            if (mask[start] || seen[start]) continue
            val pixels = ArrayList<Int>(64)
            val q = ArrayDeque<Int>()
            q.add(start)
            seen[start] = true
            var solid = 0
            var lumaSum = 0.0
            while (q.isNotEmpty()) {
                val i = q.removeFirst()
                pixels.add(i)
                val r = Color.red(px[i])
                val g = Color.green(px[i])
                val b = Color.blue(px[i])
                val L = luma(r, g, b)
                lumaSum += L
                if (L < 112.0 || chroma(r, g, b) > 34.0) solid++
                val x = i % w
                val y = i / w
                if (x > 0 && !mask[i - 1] && !seen[i - 1]) { seen[i - 1] = true; q.add(i - 1) }
                if (x < w - 1 && !mask[i + 1] && !seen[i + 1]) { seen[i + 1] = true; q.add(i + 1) }
                if (y > 0 && !mask[i - w] && !seen[i - w]) { seen[i - w] = true; q.add(i - w) }
                if (y < h - 1 && !mask[i + w] && !seen[i + w]) { seen[i + w] = true; q.add(i + w) }
            }
            if (pixels.size > largest) largest = pixels.size
            blobs.add(Blob(pixels.toIntArray(), solid, lumaSum / pixels.size))
        }
        val areaLimit = (n * 0.04).toInt().coerceAtLeast(80)
        for (blob in blobs) {
            if (blob.pixels.size == largest) continue
            val junk = blob.pixels.size < areaLimit ||
                (blob.meanL > 132.0 && blob.solid < blob.pixels.size * 0.12)
            if (junk) for (i in blob.pixels) mask[i] = true
        }
    }

    private fun featherEdge(out: IntArray, mask: BooleanArray, w: Int, h: Int, feather: Int) {
        if (feather <= 0) return
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

    private fun luma(r: Int, g: Int, b: Int): Double = 0.2126 * r + 0.7152 * g + 0.0722 * b

    private fun chroma(r: Int, g: Int, b: Int): Int = max(r, max(g, b)) - minOf(r, g, b)

    private fun colorDist(r: Int, g: Int, b: Int, br: Int, bg: Int, bb: Int): Double {
        val dr = (r - br).toDouble()
        val dg = (g - bg).toDouble()
        val db = (b - bb).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    private fun backgroundColor(px: IntArray, w: Int, h: Int): Int {
        val rs = ArrayList<Int>(512)
        val gs = ArrayList<Int>(512)
        val bs = ArrayList<Int>(512)
        val step = max(1, minOf(w, h) / 120)
        fun take(i: Int) {
            val r = Color.red(px[i])
            val g = Color.green(px[i])
            val b = Color.blue(px[i])
            if (luma(r, g, b) < 150.0) return
            rs.add(r); gs.add(g); bs.add(b)
        }
        for (x in 0 until w step step) {
            take(x)
            take((h - 1) * w + x)
        }
        for (y in 0 until h step step) {
            take(y * w)
            take(y * w + w - 1)
        }
        if (rs.size < 6) return Color.WHITE
        rs.sort(); gs.sort(); bs.sort()
        val m = rs.size / 2
        return Color.rgb(rs[m], gs[m], bs[m])
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

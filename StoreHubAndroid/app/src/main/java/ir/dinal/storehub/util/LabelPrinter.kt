package ir.dinal.storehub.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.graphics.*
import androidx.print.PrintHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import ir.dinal.storehub.data.ProductEntity
import java.util.EnumMap
import java.util.UUID

object LabelPrinter {
    private const val WIDTH = 400
    private const val HEIGHT = 240
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    enum class LabelMode {
        QR_ONLY,
        QR_PRICE,
        QR_NAME_CODE,
        FULL
    }

    /**
     * WooCommerce products encode the real product-page URL so a customer's phone
     * opens the product page and sees the live website price. Price is NEVER part
     * of the QR payload. Manual products fall back to their stable StoreHub code.
     */
    fun qrPayload(product: ProductEntity, wooBaseUrl: String = ""): String {
        product.productUrl?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { return it }
        if (product.wooId != null && wooBaseUrl.startsWith("https://")) {
            return wooBaseUrl.trimEnd('/') + "/?post_type=product&p=${product.wooId}"
        }
        return product.internalCode.trim().ifBlank { "P-${product.id}" }
    }

    fun opensWebsite(product: ProductEntity, wooBaseUrl: String = ""): Boolean =
        qrPayload(product, wooBaseUrl).startsWith("http://") || qrPayload(product, wooBaseUrl).startsWith("https://")

    fun render(product: ProductEntity, mode: LabelMode, wooBaseUrl: String = ""): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(241, 241, 241) }
        val payload = qrPayload(product, wooBaseUrl)

        if (mode == LabelMode.QR_ONLY) {
            val qr = qrBitmap(payload, 202)
            canvas.drawBitmap(qr, 99f, 19f, null)
            return bitmap
        }

        ink.style = Paint.Style.STROKE
        ink.strokeWidth = 2f
        canvas.drawRoundRect(4f, 4f, 396f, 236f, 14f, 14f, ink)
        ink.style = Paint.Style.FILL

        val qrSize = if (mode == LabelMode.FULL) 145 else 160
        val qrTop = (HEIGHT - qrSize) / 2f
        val qr = qrBitmap(payload, qrSize)
        canvas.drawBitmap(qr, 13f, qrTop, null)
        ink.strokeWidth = 2f
        canvas.drawLine(182f, 18f, 182f, 222f, ink)

        when (mode) {
            LabelMode.QR_PRICE -> drawPriceOnly(canvas, product, ink, soft)
            LabelMode.QR_NAME_CODE -> drawNameCode(canvas, product, ink)
            LabelMode.FULL -> drawFull(canvas, product, ink, soft)
            LabelMode.QR_ONLY -> Unit
        }
        return bitmap
    }

    fun printSystem(context: Context, product: ProductEntity, mode: LabelMode, wooBaseUrl: String = "") {
        val helper = PrintHelper(context).apply {
            scaleMode = PrintHelper.SCALE_MODE_FIT
            colorMode = PrintHelper.COLOR_MODE_MONOCHROME
            orientation = PrintHelper.ORIENTATION_LANDSCAPE
        }
        helper.printBitmap("DINAL-${product.id}", render(product, mode, wooBaseUrl))
    }

    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<Pair<String, String>> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return adapter.bondedDevices
            .map { (it.name ?: "Bluetooth Printer") to it.address }
            .sortedBy { it.first.lowercase() }
    }

    @SuppressLint("MissingPermission")
    fun printBluetooth(
        context: Context,
        product: ProductEntity,
        mode: LabelMode,
        wooBaseUrl: String,
        macAddress: String,
        result: (Boolean, String?) -> Unit
    ) {
        Thread {
            runCatching {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: error("بلوتوث روی این دستگاه در دسترس نیست.")
                require(adapter.isEnabled) { "بلوتوث خاموش است." }
                val device = adapter.getRemoteDevice(macAddress)
                val socket = device.createRfcommSocketToServiceRecord(sppUuid)
                adapter.cancelDiscovery()
                socket.connect()
                socket.outputStream.use { out ->
                    val bitmap = render(product, mode, wooBaseUrl)
                    val data = bitmapToMonochrome(bitmap)
                    val widthBytes = (bitmap.width + 7) / 8
                    val header = "SIZE 50 mm,30 mm\r\nGAP 2 mm,0 mm\r\nDIRECTION 1\r\nCLS\r\nBITMAP 0,0,$widthBytes,${bitmap.height},0,"
                    out.write(header.toByteArray(Charsets.US_ASCII))
                    out.write(data)
                    out.write("\r\nPRINT 1,1\r\n".toByteArray(Charsets.US_ASCII))
                    out.flush()
                }
                socket.close()
            }.onSuccess { result(true, null) }
                .onFailure { result(false, it.message ?: "خطا در چاپ بلوتوث") }
        }.start()
    }

    private fun qrBitmap(payload: String, size: Int): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.MARGIN, 1)
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        val matrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
        val qr = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size) {
            qr.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
        return qr
    }

    private fun drawPriceOnly(canvas: Canvas, product: ProductEntity, ink: Paint, soft: Paint) {
        canvas.drawRoundRect(202f, 57f, 382f, 183f, 18f, 18f, soft)
        ink.textAlign = Paint.Align.CENTER
        ink.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        ink.textSize = 18f
        canvas.drawText("قیمت", 292f, 91f, ink)
        ink.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        ink.textSize = 29f
        canvas.drawText(formatPrice(product.price), 292f, 137f, ink)
        ink.textSize = 15f
        canvas.drawText("DINAL", 292f, 167f, ink)
    }

    private fun drawNameCode(canvas: Canvas, product: ProductEntity, ink: Paint) {
        ink.textAlign = Paint.Align.RIGHT
        ink.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        ink.textSize = 26f
        drawWrapped(canvas, product.name, 382f, 58f, 184f, ink, 3)
        val code = humanCode(product)
        ink.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        ink.textSize = 17f
        canvas.drawText(code.take(24), 382f, 165f, ink)
        ink.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        ink.textSize = 18f
        canvas.drawText("DINAL", 382f, 208f, ink)
    }

    private fun drawFull(canvas: Canvas, product: ProductEntity, ink: Paint, soft: Paint) {
        ink.textAlign = Paint.Align.RIGHT
        ink.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        ink.textSize = 24f
        drawWrapped(canvas, product.name, 382f, 43f, 184f, ink, 2)
        ink.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        ink.textSize = 14f
        canvas.drawText(humanCode(product).take(24), 382f, 102f, ink)

        canvas.drawRoundRect(198f, 118f, 382f, 178f, 12f, 12f, soft)
        ink.textAlign = Paint.Align.CENTER
        ink.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        ink.textSize = 14f
        canvas.drawText("قیمت", 290f, 138f, ink)
        ink.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        ink.textSize = 27f
        canvas.drawText(formatPrice(product.price), 290f, 169f, ink)
        ink.textAlign = Paint.Align.RIGHT
        ink.textSize = 18f
        canvas.drawText("DINAL", 382f, 211f, ink)
    }

    private fun humanCode(product: ProductEntity): String =
        product.sku?.takeIf { it.isNotBlank() }
            ?: product.barcode?.takeIf { it.isNotBlank() }
            ?: product.internalCode.ifBlank { "P-${product.id}" }

    private fun bitmapToMonochrome(bitmap: Bitmap): ByteArray {
        val widthBytes = (bitmap.width + 7) / 8
        val out = ByteArray(widthBytes * bitmap.height)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val c = bitmap.getPixel(x, y)
                val gray = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                if (gray < 160) {
                    val index = y * widthBytes + x / 8
                    out[index] = (out[index].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }
        }
        return out
    }

    private fun drawWrapped(canvas: Canvas, text: String, right: Float, top: Float, maxWidth: Float, paint: Paint, maxLines: Int) {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var line = ""
        for (word in words) {
            val candidate = if (line.isBlank()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth) line = candidate
            else {
                if (line.isNotBlank()) lines += line
                line = word
                if (lines.size >= maxLines - 1) break
            }
        }
        if (line.isNotBlank() && lines.size < maxLines) lines += line
        lines.take(maxLines).forEachIndexed { i, value ->
            canvas.drawText(value, right, top + i * (paint.textSize + 7f), paint)
        }
    }

    private fun formatPrice(value: Double): String = MoneyFormat.toman(value)
}

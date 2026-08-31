package ir.dinal.storehub.publishing

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.TimeUnit

object PhotoBarcode {
    fun read(context: Context, uri: Uri): String? = runCatching {
        val image = InputImage.fromFilePath(context, uri)
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
        )
        try {
            val found = Tasks.await(scanner.process(image), 8, TimeUnit.SECONDS)
            found.mapNotNull { it.rawValue?.trim() }.firstOrNull { it.length >= 6 }
        } finally {
            scanner.close()
        }
    }.getOrNull()
}

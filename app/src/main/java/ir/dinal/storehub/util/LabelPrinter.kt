package ir.dinal.storehub.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import ir.dinal.storehub.data.ProductEntity
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

object LabelPrinter{
    fun print(context:Context,p:ProductEntity){
        val value=p.barcode?:p.sku?:p.internalCode
        val matrix=QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,320,320);val bmp=Bitmap.createBitmap(320,320,Bitmap.Config.RGB_565)
        for(x in 0 until 320)for(y in 0 until 320)bmp.setPixel(x,y,if(matrix[x,y])android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        val out=ByteArrayOutputStream();bmp.compress(Bitmap.CompressFormat.PNG,100,out);val b64=Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)
        val html="""<html dir='rtl'><body style='font-family:sans-serif;text-align:center'><h3>${escape(p.name)}</h3><img width='180' src='data:image/png;base64,$b64'/><div>${escape(value)}</div><h2>${"%,.0f".format(p.price)}</h2></body></html>"""
        val web=WebView(context);web.loadDataWithBaseURL(null,html,"text/html","UTF-8",null);web.postDelayed({val pm=context.getSystemService(Context.PRINT_SERVICE) as PrintManager;pm.print("StoreHub-${p.id}",web.createPrintDocumentAdapter("StoreHub Label"),PrintAttributes.Builder().build())},700)
    }
    private fun escape(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
}

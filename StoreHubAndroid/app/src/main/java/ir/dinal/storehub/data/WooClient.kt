package ir.dinal.storehub.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class WooClient(private val settings:WooSettings){
    private val client=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS).build()
    private val jsonMedia="application/json; charset=utf-8".toMediaType()

    data class WooProduct(val wooId:Long,val name:String,val sku:String?,val barcode:String?,val price:Double,val imageUrl:String?,val productUrl:String?,val category:String?)

    fun test():WooTestResult=runCatching{
        validate()
        val request=request("products", mapOf("page" to "1", "per_page" to "1", "status" to "publish"))
        client.newCall(request).execute().use{r->
            if(r.isSuccessful) WooTestResult(true,"اتصال به ووکامرس موفق بود (HTTP ${r.code}).")
            else WooTestResult(false,"اتصال ناموفق بود: HTTP ${r.code} — ${r.body?.string()?.take(250).orEmpty()}")
        }
    }.getOrElse{WooTestResult(false,it.message?:"خطای اتصال")}

    fun fetchAll(onPage:((Int)->Unit)?=null):List<WooProduct>{
        validate();val out=mutableListOf<WooProduct>();var page=1
        while(true){
            onPage?.invoke(page)
            val req=request("products", mapOf("page" to page.toString(), "per_page" to "100", "status" to "publish"))
            val arr=client.newCall(req).execute().use{r->if(!r.isSuccessful)error("WooCommerce HTTP ${r.code}: ${r.body?.string()?.take(300)}");JsonParser.parseString(r.body?.string()?:"[]").asJsonArray}
            arr.forEach{el->parseProduct(el.asJsonObject)?.let{out.add(it)}}
            if(arr.size()<100)break
            page++
            if(page>500)error("تعداد صفحات ووکامرس غیرعادی است؛ سینک متوقف شد.")
        }
        return out
    }

    fun fetchOrders(channelCode:String, pages:Int=4):List<ir.dinal.storehub.sync.ChannelOrderDraft>{
        validate()
        val out=ArrayList<ir.dinal.storehub.sync.ChannelOrderDraft>()
        var page=1
        while(page<=pages){
            val req=request("orders", mapOf(
                "page" to page.toString(),
                "per_page" to "50",
                "orderby" to "date",
                "order" to "desc",
                "status" to "pending,on-hold,processing,cancelled,refunded,completed"
            ))
            val arr=client.newCall(req).execute().use{ r ->
                if(!r.isSuccessful) error("WooCommerce سفارش‌ها HTTP ${r.code}: ${r.body?.string()?.take(300)}")
                JsonParser.parseString(r.body?.string()?:"[]").asJsonArray
            }
            arr.forEach { el -> parseOrder(el.asJsonObject, channelCode)?.let { out += it } }
            if(arr.size()<50) break
            page++
        }
        return out
    }

    fun updateStock(externalProductId:String, variationId:String?, quantity:Int){
        validate()
        val path = if (!variationId.isNullOrBlank()) {
            "products/${externalProductId.trim()}/variations/${variationId.trim()}"
        } else {
            "products/${externalProductId.trim()}"
        }
        val body = JsonObject().apply {
            addProperty("manage_stock", true)
            addProperty("stock_quantity", quantity.coerceAtLeast(0))
        }
        val req = request(path, emptyMap(), "PUT", body.toString())
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("به‌روزرسانی موجودی ووکامرس HTTP ${r.code}: ${r.body?.string()?.take(280)}")
        }
    }

    fun updateOrderStatus(externalOrderId:String, status:String){
        validate()
        val body = JsonObject().apply { addProperty("status", status) }
        val req = request("orders/${externalOrderId.trim()}", emptyMap(), "PUT", body.toString())
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("به‌روزرسانی سفارش ووکامرس HTTP ${r.code}: ${r.body?.string()?.take(280)}")
        }
    }

    private fun validate(){
        require(settings.baseUrl.startsWith("https://")){"برای اتصال مستقیم امن، آدرس ووکامرس باید با https:// شروع شود."}
        require(settings.consumerKey.startsWith("ck_")){"Consumer Key معتبر وارد نشده است."}
        require(settings.consumerSecret.startsWith("cs_")){"Consumer Secret معتبر وارد نشده است."}
    }

    private fun request(path:String, query:Map<String,String>, method:String="GET", json:String?=null):Request{
        val urlText=settings.baseUrl.trimEnd('/')+"/wp-json/"+settings.apiVersion.trim('/')+"/"+path.trimStart('/')
        val b=(urlText.toHttpUrlOrNull()?:error("آدرس ووکامرس نامعتبر است")).newBuilder()
        query.forEach { (k,v) -> b.addQueryParameter(k,v) }
        val rb=Request.Builder()
        if(settings.queryStringAuth){
            b.addQueryParameter("consumer_key",settings.consumerKey).addQueryParameter("consumer_secret",settings.consumerSecret)
        }else rb.header("Authorization",Credentials.basic(settings.consumerKey,settings.consumerSecret))
        rb.url(b.build())
        val media = jsonMedia
        return when(method){
            "PUT" -> rb.put((json?:"{}").toRequestBody(media)).build()
            "POST" -> rb.post((json?:"{}").toRequestBody(media)).build()
            else -> rb.get().build()
        }
    }

    private fun parseOrder(o:JsonObject, channelCode:String):ir.dinal.storehub.sync.ChannelOrderDraft?{
        val id=o.longOrNull("id")?:return null
        val status=o.str("status").ifBlank{"pending"}
        val billing=o.getAsJsonObject("billing")
        val shipping=o.getAsJsonObject("shipping")
        val first=billing?.str("first_name").orEmpty()
        val last=billing?.str("last_name").orEmpty()
        val name=listOf(first, last).filter{it.isNotBlank()}.joinToString(" ").ifBlank { null }
        val phone=billing?.str("phone")?.ifBlank{null}
        val address=listOf(
            shipping?.str("address_1").orEmpty(),
            shipping?.str("city").orEmpty()
        ).filter{it.isNotBlank()}.joinToString("، ").ifBlank { billing?.str("address_1")?.ifBlank{null} }
        val total=(o.str("total").ifBlank{"0"}).toDoubleOrNull()?:0.0
        val items=ArrayList<ir.dinal.storehub.sync.ChannelOrderLine>()
        o.getAsJsonArray("line_items")?.forEach { el ->
            val li=el as? JsonObject ?: return@forEach
            val qty=li.get("quantity")?.takeIf{it.isJsonPrimitive}?.asDouble ?: 0.0
            if(qty<=0) return@forEach
            val price=(li.str("price").ifBlank{li.str("total")}).toDoubleOrNull()?:0.0
            items += ir.dinal.storehub.sync.ChannelOrderLine(
                name=li.str("name").ifBlank{"قلم سفارش"},
                sku=li.str("sku").ifBlank{null},
                quantity=qty,
                unitPrice=price,
                lineTotal=(li.str("total").ifBlank{null})?.toDoubleOrNull() ?: (price*qty),
                externalProductId=li.longOrNull("product_id")?.toString(),
                externalVariationId=li.longOrNull("variation_id")?.takeIf{it>0}?.toString()
            )
        }
        return ir.dinal.storehub.sync.ChannelOrderDraft(
            externalOrderId=id.toString(),
            channelCode=channelCode,
            customerName=name,
            customerMobile=phone,
            shippingAddress=address,
            total=total,
            externalStatus=status,
            items=items
        )
    }

    private fun parseProduct(o:JsonObject):WooProduct?{
        val id=o.longOrNull("id")?:return null
        val name=o.str("name").ifBlank{"کالای $id"}
        val sku=o.str("sku").ifBlank{null}
        val price=(o.str("price").ifBlank{o.str("regular_price")}).toDoubleOrNull()?:0.0
        val image=(o.getAsJsonArray("images")?.firstOrNull() as? JsonObject)?.str("src")?.ifBlank{null}
        val productUrl=o.str("permalink").ifBlank{null}
        val category=(o.getAsJsonArray("categories")?.firstOrNull() as? JsonObject)?.str("name")?.ifBlank{null}
        var barcode=o.str("global_unique_id").ifBlank{null}
        if(barcode==null){
            val meta=o.getAsJsonArray("meta_data")?:JsonArray()
            for(e in meta){val m=e as? JsonObject?:continue;val k=m.str("key").lowercase();if(k in setOf("_barcode","barcode","gtin","_gtin","global_unique_id")){val v=m.get("value")?.let{if(it.isJsonPrimitive)it.asString else ""}.orEmpty();if(v.isNotBlank()){barcode=v;break}}}
        }
        return WooProduct(id,name,sku,barcode,price,image,productUrl,category)
    }
    private fun JsonObject.str(k:String)=get(k)?.takeIf{it.isJsonPrimitive}?.asString?:""
    private fun JsonObject.longOrNull(k:String)=get(k)?.takeIf{it.isJsonPrimitive}?.asLong
}

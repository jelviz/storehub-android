package ir.dinal.storehub.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

class WooClient(private val settings:WooSettings){
    private val client=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS).build()

    data class WooProduct(val wooId:Long,val name:String,val sku:String?,val barcode:String?,val price:Double,val imageUrl:String?,val category:String?)

    fun test():WooTestResult=runCatching{
        validate()
        val request=request(1,1)
        client.newCall(request).execute().use{r->
            if(r.isSuccessful) WooTestResult(true,"اتصال به ووکامرس موفق بود (HTTP ${r.code}).")
            else WooTestResult(false,"اتصال ناموفق بود: HTTP ${r.code} — ${r.body?.string()?.take(250).orEmpty()}")
        }
    }.getOrElse{WooTestResult(false,it.message?:"خطای اتصال")}

    fun fetchAll(onPage:((Int)->Unit)?=null):List<WooProduct>{
        validate();val out=mutableListOf<WooProduct>();var page=1
        while(true){
            onPage?.invoke(page)
            val req=request(page,100)
            val arr=client.newCall(req).execute().use{r->if(!r.isSuccessful)error("WooCommerce HTTP ${r.code}: ${r.body?.string()?.take(300)}");JsonParser.parseString(r.body?.string()?:"[]").asJsonArray}
            arr.forEach{el->parseProduct(el.asJsonObject)?.let{out.add(it)}}
            if(arr.size()<100)break
            page++
            if(page>500)error("تعداد صفحات ووکامرس غیرعادی است؛ سینک متوقف شد.")
        }
        return out
    }

    private fun validate(){
        require(settings.baseUrl.startsWith("https://")){"برای اتصال مستقیم امن، آدرس ووکامرس باید با https:// شروع شود."}
        require(settings.consumerKey.startsWith("ck_")){"Consumer Key معتبر وارد نشده است."}
        require(settings.consumerSecret.startsWith("cs_")){"Consumer Secret معتبر وارد نشده است."}
    }

    private fun request(page:Int,perPage:Int):Request{
        val urlText=settings.baseUrl.trimEnd('/')+"/wp-json/"+settings.apiVersion.trim('/')+"/products"
        val b=(urlText.toHttpUrlOrNull()?:error("آدرس ووکامرس نامعتبر است")).newBuilder().addQueryParameter("page",page.toString()).addQueryParameter("per_page",perPage.toString()).addQueryParameter("status","publish")
        val rb=Request.Builder()
        if(settings.queryStringAuth){
            b.addQueryParameter("consumer_key",settings.consumerKey).addQueryParameter("consumer_secret",settings.consumerSecret)
        }else rb.header("Authorization",Credentials.basic(settings.consumerKey,settings.consumerSecret))
        return rb.url(b.build()).get().build()
    }

    private fun parseProduct(o:JsonObject):WooProduct?{
        val id=o.longOrNull("id")?:return null
        val name=o.str("name").ifBlank{"کالای $id"}
        val sku=o.str("sku").ifBlank{null}
        val price=(o.str("price").ifBlank{o.str("regular_price")}).toDoubleOrNull()?:0.0
        val image=(o.getAsJsonArray("images")?.firstOrNull() as? JsonObject)?.str("src")?.ifBlank{null}
        val category=(o.getAsJsonArray("categories")?.firstOrNull() as? JsonObject)?.str("name")?.ifBlank{null}
        var barcode=o.str("global_unique_id").ifBlank{null}
        if(barcode==null){
            val meta=o.getAsJsonArray("meta_data")?:JsonArray()
            for(e in meta){val m=e as? JsonObject?:continue;val k=m.str("key").lowercase();if(k in setOf("_barcode","barcode","gtin","_gtin","global_unique_id")){val v=m.get("value")?.let{if(it.isJsonPrimitive)it.asString else ""}.orEmpty();if(v.isNotBlank()){barcode=v;break}}}
        }
        return WooProduct(id,name,sku,barcode,price,image,category)
    }
    private fun JsonObject.str(k:String)=get(k)?.takeIf{it.isJsonPrimitive}?.asString?:""
    private fun JsonObject.longOrNull(k:String)=get(k)?.takeIf{it.isJsonPrimitive}?.asLong
}

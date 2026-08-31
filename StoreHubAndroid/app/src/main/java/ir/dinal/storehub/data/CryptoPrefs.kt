package ir.dinal.storehub.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class WooPrefs(context:Context){
    private val prefs=context.getSharedPreferences("storehub_woo",Context.MODE_PRIVATE)
    private val alias="storehub_local_woo_key"

    var baseUrl:String get()=prefs.getString("base_url","")!!; set(v){prefs.edit().putString("base_url",v.trim().trimEnd('/')).apply()}
    var apiVersion:String get()=prefs.getString("api_version","wc/v3")!!; set(v){prefs.edit().putString("api_version",v.trim().trim('/').ifBlank{"wc/v3"}).apply()}
    var autoSync:Boolean get()=prefs.getBoolean("auto_sync",false); set(v){prefs.edit().putBoolean("auto_sync",v).apply()}
    var autoSyncMinutes:Int get()=prefs.getInt("auto_sync_minutes",60); set(v){prefs.edit().putInt("auto_sync_minutes",v.coerceAtLeast(15)).apply()}
    var queryStringAuth:Boolean get()=prefs.getBoolean("query_string_auth",false); set(v){prefs.edit().putBoolean("query_string_auth",v).apply()}

    fun hasKey()=prefs.contains("consumer_key")
    fun hasSecret()=prefs.contains("consumer_secret")
    fun consumerKey():String=decrypt(prefs.getString("consumer_key",null))
    fun consumerSecret():String=decrypt(prefs.getString("consumer_secret",null))
    fun setConsumerKey(value:String){ if(value.isNotBlank())prefs.edit().putString("consumer_key",encrypt(value.trim())).apply() }
    fun setConsumerSecret(value:String){ if(value.isNotBlank())prefs.edit().putString("consumer_secret",encrypt(value.trim())).apply() }
    fun clearCredentials(){prefs.edit().remove("consumer_key").remove("consumer_secret").apply()}
    fun settings()=WooSettings(baseUrl,apiVersion,consumerKey(),consumerSecret(),autoSync,autoSyncMinutes,queryStringAuth)

    private fun secretKey():SecretKey{
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey(alias,null) as? SecretKey)?.let{return it}
        val kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return kg.generateKey()
    }
    private fun encrypt(value:String):String{
        val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,secretKey());val cipher=c.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(cipher,Base64.NO_WRAP)
    }
    private fun decrypt(stored:String?):String{
        if(stored.isNullOrBlank())return ""
        return runCatching{val p=stored.split(':',limit=2);val iv=Base64.decode(p[0],Base64.NO_WRAP);val enc=Base64.decode(p[1],Base64.NO_WRAP);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,secretKey(),GCMParameterSpec(128,iv));String(c.doFinal(enc),Charsets.UTF_8)}.getOrDefault("")
    }
}

/**
 * Secure settings used only for smart publishing. Woo/site passwords and the AI key
 * are encrypted with Android Keystore and never included in StoreHub JSON backups.
 */
class PublishingPrefs(private val context:Context){
    private val prefs=context.getSharedPreferences("storehub_publish",Context.MODE_PRIVATE)
    private val alias="storehub_publish_secure_key"

    var aiProvider:String
        get()=prefs.getString("ai_provider","openai").orEmpty().ifBlank{"openai"}
        set(v){prefs.edit().putString("ai_provider",v).apply()}

    var openAiBaseUrl:String
        get()=prefs.getString("openai_base","https://api.openai.com/v1").orEmpty().ifBlank{"https://api.openai.com/v1"}
        set(v){prefs.edit().putString("openai_base",v.trim().trimEnd('/').ifBlank{"https://api.openai.com/v1"}).apply()}

    var openAiModel:String
        get()=prefs.getString("openai_model","gpt-4o-mini").orEmpty().let {
            when {
                it.isBlank() || it=="gpt-5.6-sol" || it=="gpt-5-mini" || it=="gpt-5.6" -> if(aiProvider=="gemini") "gemini-2.0-flash" else "gpt-4o-mini"
                else -> it
            }
        }
        set(v){prefs.edit().putString("openai_model",v.trim().ifBlank{ if(aiProvider=="gemini") "gemini-2.0-flash" else "gpt-4o-mini" }).apply()}

    fun openAiKey():String=decrypt(prefs.getString("openai_key",null))
    fun hasOpenAiKey():Boolean=openAiKey().isNotBlank()
    fun setOpenAiKey(v:String){ if(v.isNotBlank()) prefs.edit().putString("openai_key",encrypt(v.trim())).apply() }
    fun clearOpenAiKey(){prefs.edit().remove("openai_key").apply()}

    fun site(index:Int):WooPublishSite{
        require(index in 1..3)
        val p="site_${index}_"
        val explicitBase=prefs.getString(p+"base","").orEmpty()
        if(index==1 && explicitBase.isBlank()){
            val legacy=WooPrefs(context)
            if(legacy.baseUrl.isNotBlank()){
                return WooPublishSite(
                    index=1,
                    name="سایت اصلی",
                    enabled=true,
                    baseUrl=legacy.baseUrl,
                    apiVersion=legacy.apiVersion,
                    consumerKey=legacy.consumerKey(),
                    consumerSecret=legacy.consumerSecret(),
                    queryStringAuth=legacy.queryStringAuth,
                    wpUsername=prefs.getString(p+"wp_user","").orEmpty(),
                    wpAppPassword=decrypt(prefs.getString(p+"wp_pass",null))
                )
            }
        }
        return WooPublishSite(
            index=index,
            name=prefs.getString(p+"name","سایت $index").orEmpty().ifBlank{"سایت $index"},
            enabled=prefs.getBoolean(p+"enabled",index==1 && explicitBase.isNotBlank()),
            baseUrl=explicitBase,
            apiVersion=prefs.getString(p+"api","wc/v3").orEmpty().ifBlank{"wc/v3"},
            consumerKey=decrypt(prefs.getString(p+"ck",null)),
            consumerSecret=decrypt(prefs.getString(p+"cs",null)),
            queryStringAuth=prefs.getBoolean(p+"query",false),
            wpUsername=prefs.getString(p+"wp_user","").orEmpty(),
            wpAppPassword=decrypt(prefs.getString(p+"wp_pass",null))
        )
    }

    fun sites(): List<WooPublishSite> = (1..3).map(::site)

    fun saveSite(site:WooPublishSite){
        require(site.index in 1..3)
        val p="site_${site.index}_"
        prefs.edit()
            .putString(p+"name",site.name.trim().ifBlank{"سایت ${site.index}"})
            .putBoolean(p+"enabled",site.enabled)
            .putString(p+"base",site.baseUrl.trim().trimEnd('/'))
            .putString(p+"api",site.apiVersion.trim().trim('/').ifBlank{"wc/v3"})
            .putBoolean(p+"query",site.queryStringAuth)
            .putString(p+"wp_user",site.wpUsername.trim())
            .apply()
        if(site.consumerKey.isNotBlank()) prefs.edit().putString(p+"ck",encrypt(site.consumerKey.trim())).apply()
        if(site.consumerSecret.isNotBlank()) prefs.edit().putString(p+"cs",encrypt(site.consumerSecret.trim())).apply()
        if(site.wpAppPassword.isNotBlank()) prefs.edit().putString(p+"wp_pass",encrypt(site.wpAppPassword.trim())).apply()

        // Keep the first site compatible with the existing Woo catalog sync screen.
        if(site.index==1 && site.baseUrl.isNotBlank()){
            WooPrefs(context).apply{
                baseUrl=site.baseUrl
                apiVersion=site.apiVersion
                queryStringAuth=site.queryStringAuth
                if(site.consumerKey.isNotBlank()) setConsumerKey(site.consumerKey)
                if(site.consumerSecret.isNotBlank()) setConsumerSecret(site.consumerSecret)
            }
        }
    }

    private fun secretKey():SecretKey{
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey(alias,null) as? SecretKey)?.let{return it}
        val kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return kg.generateKey()
    }
    private fun encrypt(value:String):String{
        val c=Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE,secretKey())
        return Base64.encodeToString(c.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(value.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
    }
    private fun decrypt(stored:String?):String{
        if(stored.isNullOrBlank()) return ""
        return runCatching{
            val p=stored.split(':',limit=2)
            val c=Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE,secretKey(),GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)))
            String(c.doFinal(Base64.decode(p[1],Base64.NO_WRAP)),Charsets.UTF_8)
        }.getOrDefault("")
    }
}

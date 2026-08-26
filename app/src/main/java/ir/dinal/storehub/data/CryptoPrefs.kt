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

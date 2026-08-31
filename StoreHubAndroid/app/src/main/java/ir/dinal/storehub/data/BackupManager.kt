package ir.dinal.storehub.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ir.dinal.storehub.worker.WorkerScheduler
import ir.dinal.storehub.worker.ReminderScheduler
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class BackupManager(private val context:Context){
    private val db=StoreDb.get(context);private val dao=db.dao();private val gson=GsonBuilder().setPrettyPrinting().create()

    suspend fun exportTo(uri:Uri)=withContext(Dispatchers.IO){
        val woo=WooPrefs(context)
        val payload=BackupPayload(products=dao.products(),inventory=dao.allInventory(),movements=dao.allMovements(),sales=dao.allSales(),saleItems=dao.allSaleItems(),transfers=dao.allTransfers(),transferItems=dao.allTransferItems(),purchases=dao.allPurchases(),purchaseItems=dao.allPurchaseItems(),checks=dao.allChecks(),appointments=dao.allAppointments(),wooBaseUrl=woo.baseUrl,wooApiVersion=woo.apiVersion,wooAutoSync=woo.autoSync,wooAutoSyncMinutes=woo.autoSyncMinutes,wooQueryStringAuth=woo.queryStringAuth)
        context.contentResolver.openOutputStream(uri,"wt")!!.use{out->OutputStreamWriter(out,Charsets.UTF_8).use{it.write(gson.toJson(payload))}}
    }

    suspend fun importFrom(uri:Uri)=withContext(Dispatchers.IO){
        val payload=context.contentResolver.openInputStream(uri)!!.use{input->InputStreamReader(input,Charsets.UTF_8).use{gson.fromJson(it,BackupPayload::class.java)}}
        require(payload.version==1){"نسخه فایل پشتیبان پشتیبانی نمی‌شود."}
        db.withTransaction{
            dao.clearSaleItems();dao.clearSales();dao.clearTransferItems();dao.clearTransfers();dao.clearPurchaseItems();dao.clearPurchases();dao.clearMovements();dao.clearInventory();dao.clearChecks();dao.clearAppointments();dao.clearProducts()
            if(payload.products.isNotEmpty())dao.insertProducts(payload.products)
            if(payload.inventory.isNotEmpty())dao.insertInventory(payload.inventory)
            if(payload.movements.isNotEmpty())dao.insertMovements(payload.movements)
            if(payload.sales.isNotEmpty())dao.restoreSales(payload.sales)
            if(payload.saleItems.isNotEmpty())dao.restoreSaleItems(payload.saleItems)
            if(payload.transfers.isNotEmpty())dao.restoreTransfers(payload.transfers)
            if(payload.transferItems.isNotEmpty())dao.restoreTransferItems(payload.transferItems)
            if(payload.purchases.isNotEmpty())dao.restorePurchases(payload.purchases)
            if(payload.purchaseItems.isNotEmpty())dao.restorePurchaseItems(payload.purchaseItems)
            if(payload.checks.isNotEmpty())dao.restoreChecks(payload.checks)
            if(payload.appointments.isNotEmpty())dao.restoreAppointments(payload.appointments)
        }
        WooPrefs(context).apply{baseUrl=payload.wooBaseUrl;apiVersion=payload.wooApiVersion;autoSync=payload.wooAutoSync;autoSyncMinutes=payload.wooAutoSyncMinutes;queryStringAuth=payload.wooQueryStringAuth;clearCredentials()}
        payload.checks.filter { it.status == 1 }.forEach { ReminderScheduler.scheduleCheck(context, it) }
        payload.appointments.filter { it.status == 1 }.forEach { ReminderScheduler.scheduleAppointment(context, it) }
        WorkerScheduler.scheduleAll(context)
    }
}

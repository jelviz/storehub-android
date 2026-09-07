package ir.dinal.storehub.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.GsonBuilder
import ir.dinal.storehub.worker.ReminderScheduler
import ir.dinal.storehub.worker.WorkerScheduler
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupManager(private val context:Context){
    private val db=StoreDb.get(context);private val dao=db.dao();private val gson=GsonBuilder().setPrettyPrinting().create()

    suspend fun exportTo(uri:Uri)=withContext(Dispatchers.IO){
        val woo=WooPrefs(context)
        val payload=BackupPayload(
            version=3,
            products=dao.products(),
            inventory=dao.allInventory(),
            movements=dao.allMovements(),
            sales=dao.allSales(),
            saleItems=dao.allSaleItems(),
            transfers=dao.allTransfers(),
            transferItems=dao.allTransferItems(),
            purchases=dao.allPurchases(),
            purchaseItems=dao.allPurchaseItems(),
            checks=dao.allChecks(),
            appointments=dao.allAppointments(),
            wooBaseUrl=woo.baseUrl,
            wooApiVersion=woo.apiVersion,
            wooAutoSync=woo.autoSync,
            wooAutoSyncMinutes=woo.autoSyncMinutes,
            wooQueryStringAuth=woo.queryStringAuth,
            suppliers=dao.suppliers(),
            mappings=dao.allMappings(),
            notifications=dao.notifications(1000),
            transferSuggestions=dao.openTransferSuggestions(),
            purchaseSuggestions=dao.openPurchaseSuggestions(),
            audits=dao.audits(500),
            syncQueue=dao.syncQueueByStatus("PENDING")+dao.syncQueueByStatus("FAILED")+dao.syncQueueByStatus("SUCCESS").take(80),
            orders=dao.allOrders(),
            orderItems=dao.allOrderItems(),
            stocktakes=dao.allStocktakes(),
            stocktakeItems=dao.allStocktakeItems()
        )
        context.contentResolver.openOutputStream(uri,"wt")!!.use{out->OutputStreamWriter(out,Charsets.UTF_8).use{it.write(gson.toJson(payload))}}
    }

    suspend fun importFrom(uri:Uri)=withContext(Dispatchers.IO){
        val payload=context.contentResolver.openInputStream(uri)!!.use{input->InputStreamReader(input,Charsets.UTF_8).use{gson.fromJson(it,BackupPayload::class.java)}}
        require(payload.version in 1..3){"نسخه فایل پشتیبان پشتیبانی نمی‌شود."}
        val products=if(payload.version==1) payload.products.map{it.copy(isActive=true, createdAt=if(it.createdAt==0L) it.updatedAt else it.createdAt)} else payload.products
        db.withTransaction{
            dao.clearSaleItems();dao.clearSales();dao.clearTransferItems();dao.clearTransfers();dao.clearPurchaseItems();dao.clearPurchases();dao.clearMovements();dao.clearInventory();dao.clearChecks();dao.clearAppointments();dao.clearProducts()
            dao.clearSuppliers();dao.clearMappings();dao.clearNotifications();dao.clearTransferSuggestions();dao.clearPurchaseSuggestions();dao.clearAudits();dao.clearSyncQueue()
            dao.clearOrderItems();dao.clearOrders();dao.clearStocktakeItems();dao.clearStocktakes()
            if(products.isNotEmpty())dao.insertProducts(products)
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
            payload.suppliers.orEmpty().takeIf{it.isNotEmpty()}?.let{dao.restoreSuppliers(it)}
            payload.mappings.orEmpty().takeIf{it.isNotEmpty()}?.let{dao.restoreMappings(it)}
            payload.notifications.orEmpty().takeIf{it.isNotEmpty()}?.let{dao.restoreNotifications(it)}
            payload.transferSuggestions.orEmpty().takeIf{it.isNotEmpty()}?.let{dao.restoreTransferSuggestions(it)}
            payload.purchaseSuggestions.orEmpty().takeIf{it.isNotEmpty()}?.let{dao.restorePurchaseSuggestions(it)}
            payload.audits.orEmpty().takeIf{it.isNotEmpty()}?.let{dao.restoreAudits(it)}
            payload.syncQueue.orEmpty().takeIf{it.isNotEmpty()}?.let{dao.restoreSyncQueue(it)}
            payload.orders.orEmpty().takeIf{it.isNotEmpty()}?.let{dao.restoreOrders(it)}
            payload.orderItems.orEmpty().takeIf{it.isNotEmpty()}?.let{dao.restoreOrderItems(it)}
            payload.stocktakes.orEmpty().takeIf{it.isNotEmpty()}?.let{dao.restoreStocktakes(it)}
            payload.stocktakeItems.orEmpty().takeIf{it.isNotEmpty()}?.let{dao.restoreStocktakeItems(it)}
        }
        WooPrefs(context).apply{baseUrl=payload.wooBaseUrl;apiVersion=payload.wooApiVersion;autoSync=payload.wooAutoSync;autoSyncMinutes=payload.wooAutoSyncMinutes;queryStringAuth=payload.wooQueryStringAuth;clearCredentials()}
        payload.checks.filter { it.status == 1 }.forEach { ReminderScheduler.scheduleCheck(context, it) }
        payload.appointments.filter { it.status == 1 }.forEach { ReminderScheduler.scheduleAppointment(context, it) }
        WorkerScheduler.scheduleAll(context)
    }
}

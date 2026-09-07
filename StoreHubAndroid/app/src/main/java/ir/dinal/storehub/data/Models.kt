package ir.dinal.storehub.data

import ir.dinal.storehub.inventory.AlertLevel
import ir.dinal.storehub.inventory.InventoryMath

data class DashboardLocal(
    val products:Int,
    val storeProducts:Int,
    val lowStock:Int,
    val outOfStock:Int,
    val todaySales:Double,
    val pendingTransfers:Int,
    val dueChecks:Int,
    val todayAppointments:Int,
    val lowStoreStock:Int=0,
    val lowDepotStock:Int=0,
    val transferRequired:Int=0,
    val purchaseRequired:Int=0,
    val criticalStock:Int=0,
    val unreadAlerts:Int=0,
    val failedSync:Int=0
)
data class InventoryRow(
    val product:ProductEntity,
    val warehouseId:Int,
    val onHand:Double,
    val reserved:Double=0.0,
    val damaged:Double=0.0,
    val inTransit:Double=0.0,
    val available:Double=onHand,
    val minStock:Double=0.0,
    val targetStock:Double=0.0,
    val maxStock:Double=0.0,
    val warningThreshold:Double=0.0,
    val criticalStock:Double=0.0,
    val safetyStock:Double=0.0,
    val reorderPoint:Double=0.0,
    val targetTotal:Double=0.0,
    val lastPurchaseCost:Double=0.0,
    val alertLevel:String=AlertLevel.NORMAL,
    val wooAvailable:Double=0.0,
    val storeChannelAvailable:Double=0.0
) {
    val quantity: Double get() = available
}
fun InventoryEntity.toRow(product:ProductEntity, wooAvailable:Double=0.0, storeChannelAvailable:Double=0.0):InventoryRow {
    val available = InventoryMath.available(quantity, reserved, damaged)
    return InventoryRow(
        product = product,
        warehouseId = warehouseId,
        onHand = quantity,
        reserved = reserved,
        damaged = damaged,
        inTransit = inTransit,
        available = available,
        minStock = minStock,
        targetStock = targetStock,
        maxStock = maxStock,
        warningThreshold = warningThreshold,
        criticalStock = criticalStock,
        safetyStock = safetyStock,
        reorderPoint = reorderPoint,
        targetTotal = targetTotal,
        lastPurchaseCost = lastPurchaseCost,
        alertLevel = InventoryMath.alertLevel(available, warningThreshold, minStock, criticalStock),
        wooAvailable = wooAvailable,
        storeChannelAvailable = storeChannelAvailable
    )
}
data class MovementRow(val movement:InventoryMovementEntity,val productName:String)
data class CartLine(val product:ProductEntity,val quantity:Double)
data class SaleDetails(val sale:SaleEntity,val items:List<SaleItemEntity>)
data class TransferDetails(val transfer:TransferEntity,val items:List<TransferItemEntity>)
data class PurchaseLineDraft(val productId:Long,val name:String,val quantity:Double,val unitCost:Double)
data class PurchaseDetails(val purchase:PurchaseEntity,val items:List<PurchaseItemEntity>,val supplier:SupplierEntity?=null)
data class CalendarDataLocal(val checks:List<IssuedCheckEntity>,val appointments:List<AppointmentEntity>,val purchases:List<PurchaseEntity>)
data class WooSettings(val baseUrl:String="",val apiVersion:String="wc/v3",val consumerKey:String="",val consumerSecret:String="",val autoSync:Boolean=false,val autoSyncMinutes:Int=60,val queryStringAuth:Boolean=false)
data class WooTestResult(val success:Boolean,val message:String)
data class WooSyncResult(val added:Int,val updated:Int,val failed:Int,val message:String)
data class PriceIntelligence(
    val lastPurchasePrice:Double=0.0,
    val averagePurchasePrice30Days:Double=0.0,
    val averagePurchasePrice90Days:Double=0.0,
    val minimumPurchasePrice:Double=0.0,
    val maximumPurchasePrice:Double=0.0,
    val lastSalePrice:Double=0.0,
    val lastSupplierName:String?=null
)
data class AlertCenter(
    val notifications:List<AppNotificationEntity>,
    val transfers:List<TransferSuggestionEntity>,
    val purchases:List<PurchaseSuggestionEntity>
)

data class BackupPayload(
    val version:Int=1,
    val exportedAt:Long=System.currentTimeMillis(),
    val products:List<ProductEntity>,
    val inventory:List<InventoryEntity>,
    val movements:List<InventoryMovementEntity>,
    val sales:List<SaleEntity>,
    val saleItems:List<SaleItemEntity>,
    val transfers:List<TransferEntity>,
    val transferItems:List<TransferItemEntity>,
    val purchases:List<PurchaseEntity>,
    val purchaseItems:List<PurchaseItemEntity>,
    val checks:List<IssuedCheckEntity>,
    val appointments:List<AppointmentEntity>,
    val wooBaseUrl:String="",
    val wooApiVersion:String="wc/v3",
    val wooAutoSync:Boolean=false,
    val wooAutoSyncMinutes:Int=60,
    val wooQueryStringAuth:Boolean=false,
    val suppliers:List<SupplierEntity>?=null,
    val mappings:List<ProductChannelMappingEntity>?=null,
    val notifications:List<AppNotificationEntity>?=null,
    val transferSuggestions:List<TransferSuggestionEntity>?=null,
    val purchaseSuggestions:List<PurchaseSuggestionEntity>?=null,
    val audits:List<AuditLogEntity>?=null,
    val syncQueue:List<StockSyncQueueEntity>?=null
)

data class WooPublishSite(
    val index:Int,
    val name:String="سایت",
    val enabled:Boolean=false,
    val baseUrl:String="",
    val apiVersion:String="wc/v3",
    val consumerKey:String="",
    val consumerSecret:String="",
    val queryStringAuth:Boolean=false,
    val wpUsername:String="",
    val wpAppPassword:String=""
)

data class ProductAiDraft(
    val name:String="",
    val shortDescription:String="",
    val description:String="",
    val seoTitle:String="",
    val seoDescription:String="",
    val category:String="",
    val tags:List<String> = emptyList()
)

data class ProductSearchHint(
    val queries: List<String> = emptyList(),
    val brand: String = "",
    val model: String = "",
    val persianName: String = "",
    val visualQuery: String = "",
    val confidence: String = "low"
)

data class CatalogMatch(
    val source: String,
    val sourceId: String,
    val title: String,
    val titleEn: String = "",
    val imageUrl: String? = null,
    val priceToman: Long? = null,
    val webUrl: String? = null
)

data class CatalogProductDetail(
    val match: CatalogMatch,
    val description: String,
    val shortDescription: String,
    val category: String,
    val brand: String,
    val tags: List<String>,
    val imageUrls: List<String>,
    val seoTitle: String,
    val seoDescription: String,
    val priceToman: Long? = null
)

data class PublishProductDraft(
    val name:String,
    val sku:String?,
    val shortDescription:String,
    val description:String,
    val seoTitle:String,
    val seoDescription:String,
    val category:String?,
    val tags:List<String>,
    val regularPrice:Double,
    val salePrice:Double,
    val status:String
)

data class WooPublishResult(
    val siteIndex:Int,
    val siteName:String,
    val success:Boolean,
    val productId:Long?=null,
    val permalink:String?=null,
    val message:String
)

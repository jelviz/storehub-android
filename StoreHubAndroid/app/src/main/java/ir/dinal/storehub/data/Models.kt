package ir.dinal.storehub.data

data class DashboardLocal(val products:Int,val storeProducts:Int,val lowStock:Int,val outOfStock:Int,val todaySales:Double,val pendingTransfers:Int,val dueChecks:Int,val todayAppointments:Int)
data class InventoryRow(val product:ProductEntity,val warehouseId:Int,val quantity:Double)
data class MovementRow(val movement:InventoryMovementEntity,val productName:String)
data class CartLine(val product:ProductEntity,val quantity:Double)
data class SaleDetails(val sale:SaleEntity,val items:List<SaleItemEntity>)
data class TransferDetails(val transfer:TransferEntity,val items:List<TransferItemEntity>)
data class PurchaseLineDraft(val productId:Long,val name:String,val quantity:Double,val unitCost:Double)
data class PurchaseDetails(val purchase:PurchaseEntity,val items:List<PurchaseItemEntity>)
data class CalendarDataLocal(val checks:List<IssuedCheckEntity>,val appointments:List<AppointmentEntity>,val purchases:List<PurchaseEntity>)
data class WooSettings(val baseUrl:String="",val apiVersion:String="wc/v3",val consumerKey:String="",val consumerSecret:String="",val autoSync:Boolean=false,val autoSyncMinutes:Int=60,val queryStringAuth:Boolean=false)
data class WooTestResult(val success:Boolean,val message:String)
data class WooSyncResult(val added:Int,val updated:Int,val failed:Int,val message:String)

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
    val wooQueryStringAuth:Boolean=false
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

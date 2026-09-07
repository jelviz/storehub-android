package ir.dinal.storehub.data

import androidx.room.*

@Entity(indices=[Index(value=["wooId"], unique=true), Index("sku"), Index("barcode")])
data class ProductEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val wooId:Long?=null,
    val name:String,
    val sku:String?=null,
    val barcode:String?=null,
    val internalCode:String="",
    val price:Double=0.0,
    val imageUrl:String?=null,
    val productUrl:String?=null,
    val isEnabledForStore:Boolean=false,
    val lowStockThreshold:Int=1,
    val category:String?=null,
    val source:Int=SOURCE_MANUAL,
    val updatedAt:Long=System.currentTimeMillis(),
    val isActive:Boolean=true,
    val createdAt:Long=System.currentTimeMillis()
){ companion object { const val SOURCE_MANUAL=1; const val SOURCE_WOO=2 } }

@Entity(primaryKeys=["productId","warehouseId"])
data class InventoryEntity(
    val productId:Long,
    val warehouseId:Int,
    val quantity:Double=0.0,
    val reserved:Double=0.0,
    val damaged:Double=0.0,
    val inTransit:Double=0.0,
    val safetyStock:Double=0.0,
    val minStock:Double=0.0,
    val targetStock:Double=0.0,
    val maxStock:Double=0.0,
    val warningThreshold:Double=0.0,
    val criticalStock:Double=0.0,
    val reorderPoint:Double=0.0,
    val targetTotal:Double=0.0,
    val lastPurchaseCost:Double=0.0,
    val version:Long=0
)

@Entity(indices=[Index("productId"),Index("createdAt")])
data class InventoryMovementEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val productId:Long,
    val warehouseId:Int,
    val type:Int,
    val quantityDelta:Double,
    val balanceAfter:Double,
    val reference:String?=null,
    val note:String?=null,
    val createdAt:Long=System.currentTimeMillis(),
    val beforeQuantity:Double=0.0,
    val createdBy:String?=null,
    val referenceType:String?=null,
    val referenceId:Long=0
)

@Entity(indices=[Index(value=["invoiceNo"],unique=true),Index("createdAt")])
data class SaleEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val invoiceNo:String,
    val total:Double,
    val returnedTotal:Double=0.0,
    val paymentType:Int=2,
    val customerName:String?=null,
    val customerMobile:String?=null,
    val createdAt:Long=System.currentTimeMillis()
)

@Entity(indices=[Index("saleId"),Index("productId")])
data class SaleItemEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val saleId:Long,
    val productId:Long,
    val name:String,
    val quantity:Double,
    val returnedQuantity:Double=0.0,
    val unitPrice:Double,
    val lineTotal:Double
)

@Entity(indices=[Index(value=["transferNo"],unique=true)])
data class TransferEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val transferNo:String,
    val status:Int=1,
    val note:String?=null,
    val createdAt:Long=System.currentTimeMillis(),
    val dispatchedAt:Long?=null,
    val receivedAt:Long?=null,
    val sourceWarehouseId:Int=2,
    val destinationWarehouseId:Int=1,
    val createdBy:String?=null,
    val approvedAt:Long?=null
)

@Entity(indices=[Index("transferId"),Index("productId")])
data class TransferItemEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val transferId:Long,
    val productId:Long,
    val name:String,
    val quantity:Double
)

@Entity(indices=[Index(value=["purchaseNo"],unique=true)])
data class PurchaseEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val purchaseNo:String,
    val supplierName:String?=null,
    val supplierMobile:String?=null,
    val purchaseDatePersian:String,
    val warehouseId:Int,
    val paymentType:Int=2,
    val total:Double,
    val status:Int=1,
    val note:String?=null,
    val createdAt:Long=System.currentTimeMillis(),
    val supplierId:Long?=null,
    val invoiceNumber:String?=null
)

@Entity(indices=[Index("purchaseId"),Index("productId")])
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val purchaseId:Long,
    val productId:Long,
    val name:String,
    val quantity:Double,
    val unitCost:Double,
    val lineTotal:Double
)

@Entity(indices=[Index("dueEpochDay")])
data class IssuedCheckEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val title:String,
    val bankName:String?=null,
    val checkNumber:String?=null,
    val payee:String?=null,
    val amount:Double=0.0,
    val dueDatePersian:String,
    val dueEpochDay:Long,
    val reminderDaysBefore:Int=3,
    val status:Int=1,
    val note:String?=null
)

@Entity(indices=[Index("startsAtEpochMillis")])
data class AppointmentEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val title:String,
    val personName:String?=null,
    val mobile:String?=null,
    val location:String?=null,
    val datePersian:String,
    val time:String,
    val startsAtEpochMillis:Long,
    val reminderMinutesBefore:Int=60,
    val status:Int=1,
    val note:String?=null
)

@Entity
data class WarehouseEntity(
    @PrimaryKey val id:Int,
    val code:String,
    val name:String,
    val type:String,
    val isActive:Boolean=true
)

@Entity(indices=[Index("name")])
data class SupplierEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val name:String,
    val phone:String?=null,
    val address:String?=null,
    val notes:String?=null
)

@Entity
data class ChannelEntity(
    @PrimaryKey val id:Long,
    val code:String,
    val name:String,
    val type:String,
    val integrationMode:String="DISABLED",
    val isActive:Boolean=true
)

@Entity
data class ChannelInventoryPolicyEntity(
    @PrimaryKey val channelId:Long,
    val policyType:String,
    val safetyStock:Double=0.0
)

@Entity(primaryKeys=["productId","channelId"], indices=[Index(value=["channelId","externalProductId"])])
data class ProductChannelMappingEntity(
    val productId:Long,
    val channelId:Long,
    val externalProductId:String?=null,
    val externalVariationId:String?=null,
    val externalSku:String?=null
)

@Entity(indices=[Index("createdAt"), Index(value=["type","productId","warehouseId"])])
data class AppNotificationEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val type:String,
    val productId:Long=0,
    val warehouseId:Int=0,
    val message:String,
    val severity:String,
    val createdAt:Long=System.currentTimeMillis(),
    val readAt:Long?=null,
    val actionType:String?=null,
    val actionReferenceId:Long=0
)

@Entity(indices=[Index("productId")])
data class TransferSuggestionEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val productId:Long,
    val sourceWarehouseId:Int,
    val destinationWarehouseId:Int,
    val quantity:Double,
    val reason:String,
    val status:String="OPEN",
    val createdAt:Long=System.currentTimeMillis()
)

@Entity(indices=[Index("productId")])
data class PurchaseSuggestionEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val productId:Long,
    val quantity:Double,
    val reason:String,
    val status:String="OPEN",
    val createdAt:Long=System.currentTimeMillis()
)

@Entity(indices=[Index("timestamp"), Index("entity")])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val userName:String="local",
    val action:String,
    val entity:String,
    val entityId:Long,
    val timestamp:Long=System.currentTimeMillis(),
    val beforeJson:String?=null,
    val afterJson:String?=null
)

@Entity(indices=[Index(value=["productId","channelId"], unique=true)])
data class StockSyncQueueEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val productId:Long,
    val channelId:Long,
    val calculatedQuantity:Double,
    val status:String="PENDING",
    val retryCount:Int=0,
    val lastAttemptAt:Long?=null,
    val lastError:String?=null,
    val createdAt:Long=System.currentTimeMillis()
)

@Entity(indices=[Index(value=["channelId","externalOrderId"], unique=true), Index("status"), Index("createdAt")])
data class ShopOrderEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val orderNo:String,
    val channelId:Long,
    val externalOrderId:String,
    val externalStatus:String="",
    val status:String="NEW",
    val customerName:String?=null,
    val customerMobile:String?=null,
    val shippingAddress:String?=null,
    val total:Double=0.0,
    val note:String?=null,
    val createdAt:Long=System.currentTimeMillis(),
    val importedAt:Long=System.currentTimeMillis(),
    val reservedAt:Long?=null,
    val pickedAt:Long?=null,
    val packedAt:Long?=null,
    val shippedAt:Long?=null,
    val cancelledAt:Long?=null
)

@Entity(indices=[Index("orderId"), Index("productId")])
data class ShopOrderItemEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val orderId:Long,
    val productId:Long=0,
    val name:String,
    val sku:String?=null,
    val externalProductId:String?=null,
    val externalVariationId:String?=null,
    val quantity:Double,
    val unitPrice:Double=0.0,
    val lineTotal:Double=0.0,
    val reservedStoreQty:Double=0.0,
    val waitingDepotQty:Double=0.0,
    val shortageQty:Double=0.0,
    val pickedQty:Double=0.0,
    val packedQty:Double=0.0
)

@Entity(indices=[Index("status"), Index("createdAt")])
data class StocktakeSessionEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val sessionNo:String,
    val warehouseId:Int,
    val status:String="OPEN",
    val note:String?=null,
    val createdAt:Long=System.currentTimeMillis(),
    val confirmedAt:Long?=null
)

@Entity(indices=[Index("sessionId"), Index("productId")])
data class StocktakeItemEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val sessionId:Long,
    val productId:Long,
    val name:String,
    val systemQty:Double,
    val countedQty:Double,
    val hint:String?=null
)

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
    val isEnabledForStore:Boolean=false,
    val lowStockThreshold:Int=1,
    val category:String?=null,
    val source:Int=SOURCE_MANUAL,
    val updatedAt:Long=System.currentTimeMillis()
){ companion object { const val SOURCE_MANUAL=1; const val SOURCE_WOO=2 } }

@Entity(primaryKeys=["productId","warehouseId"])
data class InventoryEntity(val productId:Long,val warehouseId:Int,val quantity:Double=0.0)

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
    val createdAt:Long=System.currentTimeMillis()
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
    val receivedAt:Long?=null
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
    val createdAt:Long=System.currentTimeMillis()
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

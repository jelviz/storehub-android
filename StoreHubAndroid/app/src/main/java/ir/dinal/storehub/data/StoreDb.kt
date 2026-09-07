package ir.dinal.storehub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ir.dinal.storehub.inventory.ChannelIds
import ir.dinal.storehub.inventory.ChannelPolicyType
import ir.dinal.storehub.inventory.IntegrationMode
import ir.dinal.storehub.inventory.WarehouseIds
import ir.dinal.storehub.inventory.WarehouseType

@Database(
    entities=[
        ProductEntity::class, InventoryEntity::class, InventoryMovementEntity::class,
        SaleEntity::class, SaleItemEntity::class, TransferEntity::class, TransferItemEntity::class,
        PurchaseEntity::class, PurchaseItemEntity::class, IssuedCheckEntity::class, AppointmentEntity::class,
        WarehouseEntity::class, SupplierEntity::class, ChannelEntity::class, ChannelInventoryPolicyEntity::class,
        ProductChannelMappingEntity::class, AppNotificationEntity::class, TransferSuggestionEntity::class,
        PurchaseSuggestionEntity::class, AuditLogEntity::class, StockSyncQueueEntity::class,
        ShopOrderEntity::class, ShopOrderItemEntity::class, StocktakeSessionEntity::class, StocktakeItemEntity::class
    ],
    version=4,
    exportSchema=false
)
abstract class StoreDb:RoomDatabase(){
    abstract fun dao():StoreHubDao
    companion object{
        @Volatile private var instance:StoreDb?=null

        private val MIGRATION_1_2=object:Migration(1,2){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("ALTER TABLE ProductEntity ADD COLUMN productUrl TEXT")
            }
        }

        private val MIGRATION_2_3=object:Migration(2,3){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("ALTER TABLE ProductEntity ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE ProductEntity ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE ProductEntity SET createdAt=updatedAt WHERE createdAt=0")

                db.execSQL("ALTER TABLE InventoryEntity ADD COLUMN reserved REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE InventoryEntity ADD COLUMN damaged REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE InventoryEntity ADD COLUMN inTransit REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE InventoryEntity ADD COLUMN safetyStock REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE InventoryEntity ADD COLUMN minStock REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE InventoryEntity ADD COLUMN targetStock REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE InventoryEntity ADD COLUMN maxStock REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE InventoryEntity ADD COLUMN warningThreshold REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE InventoryEntity ADD COLUMN criticalStock REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE InventoryEntity ADD COLUMN reorderPoint REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE InventoryEntity ADD COLUMN targetTotal REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE InventoryEntity ADD COLUMN lastPurchaseCost REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE InventoryEntity ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    UPDATE InventoryEntity SET
                        minStock=COALESCE((SELECT lowStockThreshold FROM ProductEntity WHERE ProductEntity.id=InventoryEntity.productId),1),
                        warningThreshold=COALESCE((SELECT lowStockThreshold FROM ProductEntity WHERE ProductEntity.id=InventoryEntity.productId),1)
                    WHERE warehouseId=${WarehouseIds.STORE}
                """.trimIndent())

                db.execSQL("ALTER TABLE InventoryMovementEntity ADD COLUMN beforeQuantity REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE InventoryMovementEntity ADD COLUMN createdBy TEXT")
                db.execSQL("ALTER TABLE InventoryMovementEntity ADD COLUMN referenceType TEXT")
                db.execSQL("ALTER TABLE InventoryMovementEntity ADD COLUMN referenceId INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE TransferEntity ADD COLUMN sourceWarehouseId INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE TransferEntity ADD COLUMN destinationWarehouseId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE TransferEntity ADD COLUMN createdBy TEXT")
                db.execSQL("ALTER TABLE TransferEntity ADD COLUMN approvedAt INTEGER")

                db.execSQL("ALTER TABLE PurchaseEntity ADD COLUMN supplierId INTEGER")
                db.execSQL("ALTER TABLE PurchaseEntity ADD COLUMN invoiceNumber TEXT")

                createPhase1Tables(db)

                db.execSQL("""
                    INSERT OR IGNORE INTO InventoryEntity (productId, warehouseId, quantity, reserved, damaged, inTransit, safetyStock, minStock, targetStock, maxStock, warningThreshold, criticalStock, reorderPoint, targetTotal, lastPurchaseCost, version)
                    SELECT DISTINCT ti.productId, ${WarehouseIds.STORE}, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                    FROM TransferItemEntity ti
                    INNER JOIN TransferEntity t ON t.id=ti.transferId
                    WHERE t.status=2
                """.trimIndent())
                db.execSQL("""
                    UPDATE InventoryEntity SET inTransit = inTransit + IFNULL((
                        SELECT SUM(ti.quantity) FROM TransferItemEntity ti
                        INNER JOIN TransferEntity t ON t.id=ti.transferId
                        WHERE t.status=2 AND ti.productId=InventoryEntity.productId
                    ), 0)
                    WHERE warehouseId=${WarehouseIds.STORE}
                """.trimIndent())

                db.execSQL("""
                    INSERT OR IGNORE INTO ProductChannelMappingEntity (productId, channelId, externalProductId, externalVariationId, externalSku)
                    SELECT id, ${ChannelIds.WOO_1}, CAST(wooId AS TEXT), NULL, sku FROM ProductEntity WHERE wooId IS NOT NULL
                """.trimIndent())

                seedMasterData(db)
            }
        }

        private val MIGRATION_3_4=object:Migration(3,4){
            override fun migrate(db:SupportSQLiteDatabase){
                createPhase2Tables(db)
            }
        }

        private fun createPhase2Tables(db:SupportSQLiteDatabase){
            db.execSQL("CREATE TABLE IF NOT EXISTS `ShopOrderEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderNo` TEXT NOT NULL, `channelId` INTEGER NOT NULL, `externalOrderId` TEXT NOT NULL, `externalStatus` TEXT NOT NULL, `status` TEXT NOT NULL, `customerName` TEXT, `customerMobile` TEXT, `shippingAddress` TEXT, `total` REAL NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, `importedAt` INTEGER NOT NULL, `reservedAt` INTEGER, `pickedAt` INTEGER, `packedAt` INTEGER, `shippedAt` INTEGER, `cancelledAt` INTEGER)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ShopOrderEntity_channelId_externalOrderId` ON `ShopOrderEntity` (`channelId`, `externalOrderId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ShopOrderEntity_status` ON `ShopOrderEntity` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ShopOrderEntity_createdAt` ON `ShopOrderEntity` (`createdAt`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `ShopOrderItemEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `name` TEXT NOT NULL, `sku` TEXT, `externalProductId` TEXT, `externalVariationId` TEXT, `quantity` REAL NOT NULL, `unitPrice` REAL NOT NULL, `lineTotal` REAL NOT NULL, `reservedStoreQty` REAL NOT NULL, `waitingDepotQty` REAL NOT NULL, `shortageQty` REAL NOT NULL, `pickedQty` REAL NOT NULL, `packedQty` REAL NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ShopOrderItemEntity_orderId` ON `ShopOrderItemEntity` (`orderId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ShopOrderItemEntity_productId` ON `ShopOrderItemEntity` (`productId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `StocktakeSessionEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionNo` TEXT NOT NULL, `warehouseId` INTEGER NOT NULL, `status` TEXT NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, `confirmedAt` INTEGER)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_StocktakeSessionEntity_status` ON `StocktakeSessionEntity` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_StocktakeSessionEntity_createdAt` ON `StocktakeSessionEntity` (`createdAt`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `StocktakeItemEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `name` TEXT NOT NULL, `systemQty` REAL NOT NULL, `countedQty` REAL NOT NULL, `hint` TEXT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_StocktakeItemEntity_sessionId` ON `StocktakeItemEntity` (`sessionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_StocktakeItemEntity_productId` ON `StocktakeItemEntity` (`productId`)")
        }

        private fun createPhase1Tables(db:SupportSQLiteDatabase){
            db.execSQL("CREATE TABLE IF NOT EXISTS `WarehouseEntity` (`id` INTEGER NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `SupplierEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT, `address` TEXT, `notes` TEXT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_SupplierEntity_name` ON `SupplierEntity` (`name`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `ChannelEntity` (`id` INTEGER NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `integrationMode` TEXT NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `ChannelInventoryPolicyEntity` (`channelId` INTEGER NOT NULL, `policyType` TEXT NOT NULL, `safetyStock` REAL NOT NULL, PRIMARY KEY(`channelId`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `ProductChannelMappingEntity` (`productId` INTEGER NOT NULL, `channelId` INTEGER NOT NULL, `externalProductId` TEXT, `externalVariationId` TEXT, `externalSku` TEXT, PRIMARY KEY(`productId`, `channelId`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ProductChannelMappingEntity_channelId_externalProductId` ON `ProductChannelMappingEntity` (`channelId`, `externalProductId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `AppNotificationEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `productId` INTEGER NOT NULL, `warehouseId` INTEGER NOT NULL, `message` TEXT NOT NULL, `severity` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `readAt` INTEGER, `actionType` TEXT, `actionReferenceId` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_AppNotificationEntity_createdAt` ON `AppNotificationEntity` (`createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_AppNotificationEntity_type_productId_warehouseId` ON `AppNotificationEntity` (`type`, `productId`, `warehouseId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `TransferSuggestionEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `sourceWarehouseId` INTEGER NOT NULL, `destinationWarehouseId` INTEGER NOT NULL, `quantity` REAL NOT NULL, `reason` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_TransferSuggestionEntity_productId` ON `TransferSuggestionEntity` (`productId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `PurchaseSuggestionEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `quantity` REAL NOT NULL, `reason` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_PurchaseSuggestionEntity_productId` ON `PurchaseSuggestionEntity` (`productId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `AuditLogEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userName` TEXT NOT NULL, `action` TEXT NOT NULL, `entity` TEXT NOT NULL, `entityId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `beforeJson` TEXT, `afterJson` TEXT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_AuditLogEntity_timestamp` ON `AuditLogEntity` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_AuditLogEntity_entity` ON `AuditLogEntity` (`entity`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `StockSyncQueueEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `channelId` INTEGER NOT NULL, `calculatedQuantity` REAL NOT NULL, `status` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `lastAttemptAt` INTEGER, `lastError` TEXT, `createdAt` INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_StockSyncQueueEntity_productId_channelId` ON `StockSyncQueueEntity` (`productId`, `channelId`)")
        }

        fun seedMasterData(db:SupportSQLiteDatabase){
            db.execSQL("INSERT OR IGNORE INTO WarehouseEntity(id,code,name,type,isActive) VALUES(${WarehouseIds.STORE},'STORE','مغازه','${WarehouseType.STORE}',1)")
            db.execSQL("INSERT OR IGNORE INTO WarehouseEntity(id,code,name,type,isActive) VALUES(${WarehouseIds.DEPOT},'DEPOT','دپو','${WarehouseType.DEPOT}',1)")
            db.execSQL("INSERT OR IGNORE INTO ChannelEntity(id,code,name,type,integrationMode,isActive) VALUES(${ChannelIds.WOO_1},'WOOCOMMERCE_1','ووکامرس ۱','WOOCOMMERCE','${IntegrationMode.API}',1)")
            db.execSQL("INSERT OR IGNORE INTO ChannelEntity(id,code,name,type,integrationMode,isActive) VALUES(${ChannelIds.WOO_2},'WOOCOMMERCE_2','ووکامرس ۲','WOOCOMMERCE','${IntegrationMode.API}',1)")
            db.execSQL("INSERT OR IGNORE INTO ChannelEntity(id,code,name,type,integrationMode,isActive) VALUES(${ChannelIds.WOO_3},'WOOCOMMERCE_3','ووکامرس ۳','WOOCOMMERCE','${IntegrationMode.API}',1)")
            db.execSQL("INSERT OR IGNORE INTO ChannelEntity(id,code,name,type,integrationMode,isActive) VALUES(${ChannelIds.SNAPP},'SNAPP','اسنپ‌شاپ','SNAPP','${IntegrationMode.MANUAL}',1)")
            db.execSQL("INSERT OR IGNORE INTO ChannelEntity(id,code,name,type,integrationMode,isActive) VALUES(${ChannelIds.TAPSI},'TAPSI','تپسی‌شاپ','TAPSI','${IntegrationMode.MANUAL}',1)")
            db.execSQL("INSERT OR IGNORE INTO ChannelEntity(id,code,name,type,integrationMode,isActive) VALUES(${ChannelIds.POS},'POS','فروش حضوری','POS','${IntegrationMode.DISABLED}',1)")
            db.execSQL("INSERT OR IGNORE INTO ChannelInventoryPolicyEntity(channelId,policyType,safetyStock) VALUES(${ChannelIds.WOO_1},'${ChannelPolicyType.TOTAL_AVAILABLE}',0)")
            db.execSQL("INSERT OR IGNORE INTO ChannelInventoryPolicyEntity(channelId,policyType,safetyStock) VALUES(${ChannelIds.WOO_2},'${ChannelPolicyType.TOTAL_AVAILABLE}',0)")
            db.execSQL("INSERT OR IGNORE INTO ChannelInventoryPolicyEntity(channelId,policyType,safetyStock) VALUES(${ChannelIds.WOO_3},'${ChannelPolicyType.TOTAL_AVAILABLE}',0)")
            db.execSQL("INSERT OR IGNORE INTO ChannelInventoryPolicyEntity(channelId,policyType,safetyStock) VALUES(${ChannelIds.SNAPP},'${ChannelPolicyType.STORE_AVAILABLE}',0)")
            db.execSQL("INSERT OR IGNORE INTO ChannelInventoryPolicyEntity(channelId,policyType,safetyStock) VALUES(${ChannelIds.TAPSI},'${ChannelPolicyType.STORE_AVAILABLE}',0)")
            db.execSQL("INSERT OR IGNORE INTO ChannelInventoryPolicyEntity(channelId,policyType,safetyStock) VALUES(${ChannelIds.POS},'${ChannelPolicyType.STORE_AVAILABLE}',0)")
        }

        fun get(context:Context):StoreDb=instance?:synchronized(this){
            instance?:Room.databaseBuilder(context.applicationContext,StoreDb::class.java,"storehub-local.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(object:Callback(){
                    override fun onCreate(db:SupportSQLiteDatabase){ seedMasterData(db) }
                    override fun onOpen(db:SupportSQLiteDatabase){ seedMasterData(db) }
                })
                .build()
                .also{instance=it}
        }
    }
}

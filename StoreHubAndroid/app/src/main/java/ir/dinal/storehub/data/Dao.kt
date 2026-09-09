package ir.dinal.storehub.data

import androidx.room.*

@Dao
interface StoreHubDao {
    @Query("SELECT * FROM ProductEntity WHERE (:q='' OR name LIKE '%'||:q||'%' OR IFNULL(sku,'') LIKE '%'||:q||'%' OR IFNULL(barcode,'') LIKE '%'||:q||'%' OR internalCode LIKE '%'||:q||'%' OR IFNULL(category,'') LIKE '%'||:q||'%') ORDER BY name")
    suspend fun products(q:String=""):List<ProductEntity>
    @Query("SELECT * FROM ProductEntity WHERE id=:id") suspend fun product(id:Long):ProductEntity?
    @Query("SELECT * FROM ProductEntity WHERE wooId=:wooId LIMIT 1") suspend fun productByWooId(wooId:Long):ProductEntity?
    @Query("SELECT * FROM ProductEntity WHERE productUrl=:url LIMIT 1") suspend fun productByUrl(url:String):ProductEntity?
    @Query("SELECT * FROM ProductEntity WHERE barcode=:code OR sku=:code OR internalCode=:code LIMIT 1") suspend fun productByCode(code:String):ProductEntity?
    @Insert suspend fun insertProduct(p:ProductEntity):Long
    @Update suspend fun updateProduct(p:ProductEntity)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertProducts(items:List<ProductEntity>)
    @Query("UPDATE ProductEntity SET internalCode=:code WHERE id=:id") suspend fun setInternalCode(id:Long,code:String)
    @Query("UPDATE ProductEntity SET isEnabledForStore=1 WHERE id=:id") suspend fun enableStore(id:Long)

    @Query("SELECT * FROM InventoryEntity WHERE productId=:productId AND warehouseId=:warehouseId") suspend fun inventoryOne(productId:Long,warehouseId:Int):InventoryEntity?
    @Query("SELECT * FROM InventoryEntity") suspend fun allInventory():List<InventoryEntity>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertInventoryRow(i:InventoryEntity):Long
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertInventory(i:InventoryEntity)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertInventory(items:List<InventoryEntity>)
    @Query("""
        UPDATE InventoryEntity SET
            quantity=:quantity, reserved=:reserved, damaged=:damaged, inTransit=:inTransit,
            lastPurchaseCost=:lastPurchaseCost, version=:newVersion
        WHERE productId=:productId AND warehouseId=:warehouseId AND version=:expectedVersion
    """)
    suspend fun updateInventoryStockIfVersion(
        productId:Long, warehouseId:Int, quantity:Double, reserved:Double, damaged:Double, inTransit:Double,
        lastPurchaseCost:Double, newVersion:Long, expectedVersion:Long
    ):Int
    @Query("""
        UPDATE InventoryEntity SET
            safetyStock=:safetyStock, minStock=:minStock, targetStock=:targetStock, maxStock=:maxStock,
            warningThreshold=:warningThreshold, criticalStock=:criticalStock, reorderPoint=:reorderPoint, targetTotal=:targetTotal
        WHERE productId=:productId AND warehouseId=:warehouseId
    """)
    suspend fun updateInventoryPolicy(
        productId:Long, warehouseId:Int, safetyStock:Double, minStock:Double, targetStock:Double, maxStock:Double,
        warningThreshold:Double, criticalStock:Double, reorderPoint:Double, targetTotal:Double
    )

    @Insert suspend fun movement(m:InventoryMovementEntity):Long
    @Query("SELECT * FROM InventoryMovementEntity ORDER BY createdAt DESC LIMIT :take") suspend fun movements(take:Int=400):List<InventoryMovementEntity>
    @Query("SELECT * FROM InventoryMovementEntity") suspend fun allMovements():List<InventoryMovementEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertMovements(items:List<InventoryMovementEntity>)

    @Insert suspend fun insertSale(s:SaleEntity):Long
    @Update suspend fun updateSale(s:SaleEntity)
    @Query("SELECT * FROM SaleEntity ORDER BY createdAt DESC") suspend fun sales():List<SaleEntity>
    @Query("SELECT * FROM SaleEntity") suspend fun allSales():List<SaleEntity>
    @Query("SELECT * FROM SaleEntity WHERE id=:id") suspend fun sale(id:Long):SaleEntity?
    @Insert suspend fun insertSaleItems(items:List<SaleItemEntity>)
    @Update suspend fun updateSaleItem(item:SaleItemEntity)
    @Query("SELECT * FROM SaleItemEntity WHERE saleId=:saleId ORDER BY id") suspend fun saleItems(saleId:Long):List<SaleItemEntity>
    @Query("SELECT * FROM SaleItemEntity") suspend fun allSaleItems():List<SaleItemEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreSales(items:List<SaleEntity>)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreSaleItems(items:List<SaleItemEntity>)

    @Insert suspend fun insertTransfer(t:TransferEntity):Long
    @Update suspend fun updateTransfer(t:TransferEntity)
    @Query("SELECT * FROM TransferEntity ORDER BY createdAt DESC") suspend fun transfers():List<TransferEntity>
    @Query("SELECT * FROM TransferEntity") suspend fun allTransfers():List<TransferEntity>
    @Query("SELECT * FROM TransferEntity WHERE id=:id") suspend fun transfer(id:Long):TransferEntity?
    @Insert suspend fun insertTransferItems(items:List<TransferItemEntity>)
    @Query("SELECT * FROM TransferItemEntity WHERE transferId=:id") suspend fun transferItems(id:Long):List<TransferItemEntity>
    @Query("SELECT * FROM TransferItemEntity") suspend fun allTransferItems():List<TransferItemEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreTransfers(items:List<TransferEntity>)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreTransferItems(items:List<TransferItemEntity>)

    @Insert suspend fun insertPurchase(p:PurchaseEntity):Long
    @Update suspend fun updatePurchase(p:PurchaseEntity)
    @Query("DELETE FROM PurchaseItemEntity WHERE purchaseId=:purchaseId") suspend fun deletePurchaseItems(purchaseId:Long)
    @Query("SELECT * FROM PurchaseEntity ORDER BY createdAt DESC") suspend fun purchases():List<PurchaseEntity>
    @Query("SELECT * FROM PurchaseEntity") suspend fun allPurchases():List<PurchaseEntity>
    @Query("SELECT * FROM PurchaseEntity WHERE id=:id") suspend fun purchase(id:Long):PurchaseEntity?
    @Insert suspend fun insertPurchaseItems(items:List<PurchaseItemEntity>)
    @Query("SELECT * FROM PurchaseItemEntity WHERE purchaseId=:id") suspend fun purchaseItems(id:Long):List<PurchaseItemEntity>
    @Query("SELECT * FROM PurchaseItemEntity") suspend fun allPurchaseItems():List<PurchaseItemEntity>
    @Query("SELECT * FROM PurchaseItemEntity WHERE productId=:productId") suspend fun purchaseItemsForProduct(productId:Long):List<PurchaseItemEntity>
    @Query("SELECT unitPrice FROM SaleItemEntity WHERE productId=:productId ORDER BY id DESC LIMIT 1") suspend fun lastSalePrices(productId:Long):List<Double>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restorePurchases(items:List<PurchaseEntity>)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restorePurchaseItems(items:List<PurchaseItemEntity>)

    @Insert suspend fun insertCheck(c:IssuedCheckEntity):Long
    @Update suspend fun updateCheck(c:IssuedCheckEntity)
    @Query("SELECT * FROM IssuedCheckEntity ORDER BY dueEpochDay") suspend fun checks():List<IssuedCheckEntity>
    @Query("SELECT * FROM IssuedCheckEntity") suspend fun allChecks():List<IssuedCheckEntity>
    @Query("SELECT * FROM IssuedCheckEntity WHERE id=:id") suspend fun check(id:Long):IssuedCheckEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreChecks(items:List<IssuedCheckEntity>)

    @Insert suspend fun insertAppointment(a:AppointmentEntity):Long
    @Update suspend fun updateAppointment(a:AppointmentEntity)
    @Query("SELECT * FROM AppointmentEntity ORDER BY startsAtEpochMillis") suspend fun appointments():List<AppointmentEntity>
    @Query("SELECT * FROM AppointmentEntity") suspend fun allAppointments():List<AppointmentEntity>
    @Query("SELECT * FROM AppointmentEntity WHERE id=:id") suspend fun appointment(id:Long):AppointmentEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreAppointments(items:List<AppointmentEntity>)

    @Query("SELECT * FROM WarehouseEntity ORDER BY id") suspend fun warehouses():List<WarehouseEntity>
    @Query("SELECT * FROM WarehouseEntity WHERE id=:id") suspend fun warehouse(id:Int):WarehouseEntity?
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertWarehouses(items:List<WarehouseEntity>)

    @Query("SELECT * FROM SupplierEntity ORDER BY name") suspend fun suppliers():List<SupplierEntity>
    @Query("SELECT * FROM SupplierEntity WHERE id=:id") suspend fun supplier(id:Long):SupplierEntity?
    @Query("SELECT * FROM SupplierEntity WHERE name=:name AND IFNULL(phone,'')=:phone LIMIT 1") suspend fun supplierByNamePhone(name:String, phone:String):SupplierEntity?
    @Insert suspend fun insertSupplier(s:SupplierEntity):Long
    @Update suspend fun updateSupplier(s:SupplierEntity)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreSuppliers(items:List<SupplierEntity>)
    @Query("""
        SELECT s.* FROM SupplierEntity s
        INNER JOIN PurchaseEntity p ON p.supplierId=s.id
        INNER JOIN PurchaseItemEntity i ON i.purchaseId=p.id
        WHERE i.productId=:productId
        ORDER BY p.createdAt DESC LIMIT 1
    """)
    suspend fun lastSupplierForProduct(productId:Long):SupplierEntity?

    @Query("SELECT * FROM ChannelEntity ORDER BY id") suspend fun channels():List<ChannelEntity>
    @Query("SELECT * FROM ChannelInventoryPolicyEntity") suspend fun channelPolicies():List<ChannelInventoryPolicyEntity>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertChannels(items:List<ChannelEntity>)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertChannelPolicies(items:List<ChannelInventoryPolicyEntity>)

    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertMapping(m:ProductChannelMappingEntity)
    @Query("SELECT * FROM ProductChannelMappingEntity") suspend fun allMappings():List<ProductChannelMappingEntity>
    @Query("SELECT * FROM ProductChannelMappingEntity WHERE productId=:productId") suspend fun mappingsForProduct(productId:Long):List<ProductChannelMappingEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreMappings(items:List<ProductChannelMappingEntity>)

    @Insert suspend fun insertNotification(n:AppNotificationEntity):Long
    @Query("SELECT * FROM AppNotificationEntity ORDER BY createdAt DESC LIMIT :take") suspend fun notifications(take:Int=200):List<AppNotificationEntity>
    @Query("SELECT * FROM AppNotificationEntity WHERE readAt IS NULL ORDER BY createdAt DESC") suspend fun unreadNotifications():List<AppNotificationEntity>
    @Query("SELECT COUNT(*) FROM AppNotificationEntity WHERE readAt IS NULL") suspend fun unreadNotificationCount():Int
    @Query("SELECT * FROM AppNotificationEntity WHERE productId=:productId AND warehouseId=:warehouseId AND type=:type AND readAt IS NULL LIMIT 1")
    suspend fun unreadNotification(productId:Long, warehouseId:Int, type:String):AppNotificationEntity?
    @Update suspend fun updateNotification(n:AppNotificationEntity)
    @Query("UPDATE AppNotificationEntity SET readAt=:readAt WHERE productId=:productId AND warehouseId=:warehouseId AND type=:type AND readAt IS NULL")
    suspend fun markNotificationsRead(productId:Long, warehouseId:Int, type:String, readAt:Long)
    @Query("UPDATE AppNotificationEntity SET readAt=:readAt WHERE id=:id") suspend fun markNotificationRead(id:Long, readAt:Long)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreNotifications(items:List<AppNotificationEntity>)

    @Query("DELETE FROM TransferSuggestionEntity WHERE productId=:productId AND status='OPEN'") suspend fun clearOpenTransferSuggestions(productId:Long)
    @Insert suspend fun insertTransferSuggestion(s:TransferSuggestionEntity):Long
    @Query("SELECT * FROM TransferSuggestionEntity WHERE status='OPEN' ORDER BY createdAt DESC") suspend fun openTransferSuggestions():List<TransferSuggestionEntity>
    @Query("SELECT COUNT(*) FROM TransferSuggestionEntity WHERE status='OPEN'") suspend fun openTransferSuggestionCount():Int
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreTransferSuggestions(items:List<TransferSuggestionEntity>)

    @Query("DELETE FROM PurchaseSuggestionEntity WHERE productId=:productId AND status='OPEN'") suspend fun clearOpenPurchaseSuggestions(productId:Long)
    @Insert suspend fun insertPurchaseSuggestion(s:PurchaseSuggestionEntity):Long
    @Query("SELECT * FROM PurchaseSuggestionEntity WHERE status='OPEN' ORDER BY createdAt DESC") suspend fun openPurchaseSuggestions():List<PurchaseSuggestionEntity>
    @Query("SELECT COUNT(*) FROM PurchaseSuggestionEntity WHERE status='OPEN'") suspend fun openPurchaseSuggestionCount():Int
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restorePurchaseSuggestions(items:List<PurchaseSuggestionEntity>)

    @Insert suspend fun insertAudit(a:AuditLogEntity):Long
    @Query("SELECT * FROM AuditLogEntity ORDER BY timestamp DESC LIMIT :take") suspend fun audits(take:Int=300):List<AuditLogEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreAudits(items:List<AuditLogEntity>)

    @Query("SELECT * FROM StockSyncQueueEntity WHERE productId=:productId AND channelId=:channelId LIMIT 1") suspend fun syncQueueItem(productId:Long, channelId:Long):StockSyncQueueEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertSyncQueue(item:StockSyncQueueEntity):Long
    @Query("SELECT * FROM StockSyncQueueEntity WHERE status=:status ORDER BY createdAt DESC") suspend fun syncQueueByStatus(status:String):List<StockSyncQueueEntity>
    @Query("SELECT * FROM StockSyncQueueEntity ORDER BY createdAt DESC") suspend fun allSyncQueue():List<StockSyncQueueEntity>
    @Query("SELECT COUNT(*) FROM StockSyncQueueEntity WHERE status='FAILED'") suspend fun failedSyncCount():Int
    @Query("UPDATE StockSyncQueueEntity SET status=:status, retryCount=:retryCount, lastAttemptAt=:attemptedAt, lastError=:error WHERE id=:id")
    suspend fun updateSyncQueue(id:Long, status:String, retryCount:Int, attemptedAt:Long, error:String?)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreSyncQueue(items:List<StockSyncQueueEntity>)

    @Query("UPDATE ChannelEntity SET integrationMode=:mode WHERE id=:id") suspend fun setChannelMode(id:Long, mode:String)
    @Query("SELECT * FROM ProductChannelMappingEntity WHERE productId=:productId AND channelId=:channelId LIMIT 1") suspend fun mapping(productId:Long, channelId:Long):ProductChannelMappingEntity?
    @Query("SELECT * FROM ProductChannelMappingEntity WHERE channelId=:channelId AND externalProductId=:externalId LIMIT 1") suspend fun mappingByExternal(channelId:Long, externalId:String):ProductChannelMappingEntity?
    @Query("SELECT * FROM ProductChannelMappingEntity WHERE channelId=:channelId AND externalSku=:sku LIMIT 1") suspend fun mappingBySku(channelId:Long, sku:String):ProductChannelMappingEntity?

    @Insert suspend fun insertOrder(o:ShopOrderEntity):Long
    @Update suspend fun updateOrder(o:ShopOrderEntity)
    @Query("SELECT * FROM ShopOrderEntity ORDER BY createdAt DESC") suspend fun orders():List<ShopOrderEntity>
    @Query("SELECT * FROM ShopOrderEntity") suspend fun allOrders():List<ShopOrderEntity>
    @Query("SELECT * FROM ShopOrderEntity WHERE id=:id") suspend fun order(id:Long):ShopOrderEntity?
    @Query("SELECT * FROM ShopOrderEntity WHERE channelId=:channelId AND externalOrderId=:externalId LIMIT 1") suspend fun orderByExternal(channelId:Long, externalId:String):ShopOrderEntity?
    @Query("SELECT COUNT(*) FROM ShopOrderEntity WHERE status NOT IN ('SHIPPED','CANCELLED')") suspend fun openOrderCount():Int
    @Query("SELECT COUNT(*) FROM ShopOrderEntity WHERE status IN ('RESERVED','PICKING','PICKED','PACKING','PACKED')") suspend fun fulfillmentQueueCount():Int
    @Insert suspend fun insertOrderItems(items:List<ShopOrderItemEntity>)
    @Update suspend fun updateOrderItem(item:ShopOrderItemEntity)
    @Query("SELECT * FROM ShopOrderItemEntity WHERE orderId=:orderId ORDER BY id") suspend fun orderItems(orderId:Long):List<ShopOrderItemEntity>
    @Query("SELECT * FROM ShopOrderItemEntity") suspend fun allOrderItems():List<ShopOrderItemEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreOrders(items:List<ShopOrderEntity>)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreOrderItems(items:List<ShopOrderItemEntity>)

    @Insert suspend fun insertStocktake(s:StocktakeSessionEntity):Long
    @Update suspend fun updateStocktake(s:StocktakeSessionEntity)
    @Query("SELECT * FROM StocktakeSessionEntity ORDER BY createdAt DESC") suspend fun stocktakes():List<StocktakeSessionEntity>
    @Query("SELECT * FROM StocktakeSessionEntity") suspend fun allStocktakes():List<StocktakeSessionEntity>
    @Query("SELECT * FROM StocktakeSessionEntity WHERE id=:id") suspend fun stocktake(id:Long):StocktakeSessionEntity?
    @Insert suspend fun insertStocktakeItem(item:StocktakeItemEntity):Long
    @Query("SELECT * FROM StocktakeItemEntity WHERE sessionId=:sessionId ORDER BY id") suspend fun stocktakeItems(sessionId:Long):List<StocktakeItemEntity>
    @Query("SELECT * FROM StocktakeItemEntity") suspend fun allStocktakeItems():List<StocktakeItemEntity>
    @Query("SELECT * FROM StocktakeItemEntity WHERE sessionId=:sessionId AND productId=:productId LIMIT 1") suspend fun stocktakeItem(sessionId:Long, productId:Long):StocktakeItemEntity?
    @Query("SELECT * FROM StocktakeItemEntity WHERE id=:id LIMIT 1") suspend fun stocktakeItemById(id:Long):StocktakeItemEntity?
    @Query("DELETE FROM StocktakeItemEntity WHERE id=:id") suspend fun deleteStocktakeItem(id:Long)
    @Update suspend fun updateStocktakeItem(item:StocktakeItemEntity)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreStocktakes(items:List<StocktakeSessionEntity>)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun restoreStocktakeItems(items:List<StocktakeItemEntity>)

    @Query("DELETE FROM ProductEntity") suspend fun clearProducts()
    @Query("DELETE FROM InventoryEntity") suspend fun clearInventory()
    @Query("DELETE FROM InventoryMovementEntity") suspend fun clearMovements()
    @Query("DELETE FROM SaleItemEntity") suspend fun clearSaleItems()
    @Query("DELETE FROM SaleEntity") suspend fun clearSales()
    @Query("DELETE FROM TransferItemEntity") suspend fun clearTransferItems()
    @Query("DELETE FROM TransferEntity") suspend fun clearTransfers()
    @Query("DELETE FROM PurchaseItemEntity") suspend fun clearPurchaseItems()
    @Query("DELETE FROM PurchaseEntity") suspend fun clearPurchases()
    @Query("DELETE FROM IssuedCheckEntity") suspend fun clearChecks()
    @Query("DELETE FROM AppointmentEntity") suspend fun clearAppointments()
    @Query("DELETE FROM SupplierEntity") suspend fun clearSuppliers()
    @Query("DELETE FROM ProductChannelMappingEntity") suspend fun clearMappings()
    @Query("DELETE FROM AppNotificationEntity") suspend fun clearNotifications()
    @Query("DELETE FROM TransferSuggestionEntity") suspend fun clearTransferSuggestions()
    @Query("DELETE FROM PurchaseSuggestionEntity") suspend fun clearPurchaseSuggestions()
    @Query("DELETE FROM AuditLogEntity") suspend fun clearAudits()
    @Query("DELETE FROM StockSyncQueueEntity") suspend fun clearSyncQueue()
    @Query("DELETE FROM ShopOrderItemEntity") suspend fun clearOrderItems()
    @Query("DELETE FROM ShopOrderEntity") suspend fun clearOrders()
    @Query("DELETE FROM StocktakeItemEntity") suspend fun clearStocktakeItems()
    @Query("DELETE FROM StocktakeSessionEntity") suspend fun clearStocktakes()
}

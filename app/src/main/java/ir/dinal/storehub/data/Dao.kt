package ir.dinal.storehub.data

import androidx.room.*

@Dao
interface StoreHubDao {
    @Query("SELECT * FROM ProductEntity WHERE (:q='' OR name LIKE '%'||:q||'%' OR IFNULL(sku,'') LIKE '%'||:q||'%' OR IFNULL(barcode,'') LIKE '%'||:q||'%' OR internalCode LIKE '%'||:q||'%') ORDER BY name")
    suspend fun products(q:String=""):List<ProductEntity>
    @Query("SELECT * FROM ProductEntity WHERE id=:id") suspend fun product(id:Long):ProductEntity?
    @Query("SELECT * FROM ProductEntity WHERE wooId=:wooId LIMIT 1") suspend fun productByWooId(wooId:Long):ProductEntity?
    @Query("SELECT * FROM ProductEntity WHERE barcode=:code OR sku=:code OR internalCode=:code LIMIT 1") suspend fun productByCode(code:String):ProductEntity?
    @Insert suspend fun insertProduct(p:ProductEntity):Long
    @Update suspend fun updateProduct(p:ProductEntity)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertProducts(items:List<ProductEntity>)
    @Query("UPDATE ProductEntity SET internalCode=:code WHERE id=:id") suspend fun setInternalCode(id:Long,code:String)
    @Query("UPDATE ProductEntity SET isEnabledForStore=1 WHERE id=:id") suspend fun enableStore(id:Long)

    @Query("SELECT * FROM InventoryEntity WHERE productId=:productId AND warehouseId=:warehouseId") suspend fun inventoryOne(productId:Long,warehouseId:Int):InventoryEntity?
    @Query("SELECT * FROM InventoryEntity") suspend fun allInventory():List<InventoryEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertInventory(i:InventoryEntity)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertInventory(items:List<InventoryEntity>)

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
    @Query("SELECT * FROM PurchaseEntity ORDER BY createdAt DESC") suspend fun purchases():List<PurchaseEntity>
    @Query("SELECT * FROM PurchaseEntity") suspend fun allPurchases():List<PurchaseEntity>
    @Query("SELECT * FROM PurchaseEntity WHERE id=:id") suspend fun purchase(id:Long):PurchaseEntity?
    @Insert suspend fun insertPurchaseItems(items:List<PurchaseItemEntity>)
    @Query("SELECT * FROM PurchaseItemEntity WHERE purchaseId=:id") suspend fun purchaseItems(id:Long):List<PurchaseItemEntity>
    @Query("SELECT * FROM PurchaseItemEntity") suspend fun allPurchaseItems():List<PurchaseItemEntity>
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
}

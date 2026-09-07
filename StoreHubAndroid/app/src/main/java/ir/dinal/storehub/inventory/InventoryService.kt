package ir.dinal.storehub.inventory

import ir.dinal.storehub.data.AppNotificationEntity
import ir.dinal.storehub.data.AuditLogEntity
import ir.dinal.storehub.data.InventoryEntity
import ir.dinal.storehub.data.InventoryMovementEntity
import ir.dinal.storehub.data.ProductChannelMappingEntity
import ir.dinal.storehub.data.PurchaseSuggestionEntity
import ir.dinal.storehub.data.StockSyncQueueEntity
import ir.dinal.storehub.data.StoreHubDao
import ir.dinal.storehub.data.TransferSuggestionEntity

class InventoryService(
    private val dao: StoreHubDao,
    private val notifyCritical: (id: Int, title: String, text: String) -> Unit = { _, _, _ -> }
) {
    suspend fun snapshot(productId: Long, warehouseId: Int): InventorySnapshot {
        ensureInventory(productId, warehouseId)
        return dao.inventoryOne(productId, warehouseId)!!.toSnapshot()
    }

    suspend fun ensureInventory(productId: Long, warehouseId: Int) {
        if (dao.inventoryOne(productId, warehouseId) != null) return
        val product = dao.product(productId)
        val min = if (warehouseId == WarehouseIds.STORE) (product?.lowStockThreshold ?: 0).toDouble() else 0.0
        dao.insertInventoryRow(
            InventoryEntity(
                productId = productId,
                warehouseId = warehouseId,
                minStock = min,
                warningThreshold = min,
                targetStock = if (warehouseId == WarehouseIds.STORE && min > 0) (min * 2).coerceAtLeast(2.0) else 0.0
            )
        )
    }

    suspend fun mutate(
        productId: Long,
        warehouseId: Int,
        mutation: StockMutation,
        type: Int,
        referenceType: String?,
        referenceId: Long = 0,
        reference: String? = null,
        note: String? = null,
        allowNegative: Boolean = false,
        lastPurchaseCost: Double? = null,
        createdBy: String = "local"
    ): InventorySnapshot {
        ensureInventory(productId, warehouseId)
        val current = dao.inventoryOne(productId, warehouseId)!!.toSnapshot()
        val next = InventoryLedger.apply(current, mutation, allowNegative)
        val cost = lastPurchaseCost ?: dao.inventoryOne(productId, warehouseId)!!.lastPurchaseCost
        val updated = dao.updateInventoryStockIfVersion(
            productId = productId,
            warehouseId = warehouseId,
            quantity = next.onHand,
            reserved = next.reserved,
            damaged = next.damaged,
            inTransit = next.inTransit,
            lastPurchaseCost = cost,
            newVersion = next.version,
            expectedVersion = current.version
        )
        require(updated == 1) { "موجودی همزمان تغییر کرد. دوباره تلاش کن." }
        val shownDelta = when {
            kotlin.math.abs(mutation.onHandDelta) > 0.000001 -> mutation.onHandDelta
            kotlin.math.abs(mutation.reservedDelta) > 0.000001 -> mutation.reservedDelta
            else -> mutation.inTransitDelta + mutation.damagedDelta
        }
        dao.movement(
            InventoryMovementEntity(
                productId = productId,
                warehouseId = warehouseId,
                type = type,
                quantityDelta = shownDelta,
                balanceAfter = next.onHand,
                reference = reference,
                note = note,
                beforeQuantity = current.onHand,
                createdBy = createdBy,
                referenceType = referenceType,
                referenceId = referenceId
            )
        )
        dao.insertAudit(
            AuditLogEntity(
                userName = createdBy,
                action = TxType.label(type),
                entity = referenceType ?: "INVENTORY",
                entityId = if (referenceId > 0) referenceId else productId,
                afterJson = "wh=$warehouseId onHand=${next.onHand} reserved=${next.reserved} inTransit=${next.inTransit} available=${next.available}"
            )
        )
        evaluateAlerts(productId, pushSystem = true)
        enqueueChannelStock(productId)
        return next
    }

    suspend fun sellFromStore(productId: Long, quantity: Double, saleId: Long, invoiceNo: String) {
        val current = snapshot(productId, WarehouseIds.STORE)
        InventoryLedger.sell(current, quantity)
        mutate(
            productId = productId,
            warehouseId = WarehouseIds.STORE,
            mutation = StockMutation(onHandDelta = -quantity),
            type = TxType.SALE,
            referenceType = RefType.SALE,
            referenceId = saleId,
            reference = invoiceNo,
            note = "فروش $invoiceNo"
        )
    }

    suspend fun updatePolicy(
        productId: Long,
        warehouseId: Int,
        minStock: Double,
        targetStock: Double,
        maxStock: Double,
        warningThreshold: Double,
        criticalStock: Double,
        safetyStock: Double,
        reorderPoint: Double,
        targetTotal: Double
    ) {
        ensureInventory(productId, warehouseId)
        dao.updateInventoryPolicy(
            productId, warehouseId, safetyStock, minStock, targetStock, maxStock,
            warningThreshold, criticalStock, reorderPoint, targetTotal
        )
        evaluateAlerts(productId)
    }

    suspend fun evaluateAlerts(productId: Long, pushSystem: Boolean = true) {
        val product = dao.product(productId) ?: return
        ensureInventory(productId, WarehouseIds.STORE)
        ensureInventory(productId, WarehouseIds.DEPOT)
        val storeRow = dao.inventoryOne(productId, WarehouseIds.STORE)!!
        val depotRow = dao.inventoryOne(productId, WarehouseIds.DEPOT)!!
        val plan = AlertEvaluator.evaluate(product.name, storeRow.toPolicyView(), depotRow.toPolicyView())

        val keep = plan.notifications.map { it.type to it.warehouseId }.toSet()
        listOf(
            NotificationType.LOW_STORE_STOCK to WarehouseIds.STORE,
            NotificationType.LOW_DEPOT_STOCK to WarehouseIds.DEPOT,
            NotificationType.CRITICAL_STOCK to WarehouseIds.STORE,
            NotificationType.CRITICAL_STOCK to WarehouseIds.DEPOT,
            NotificationType.OUT_OF_STOCK to WarehouseIds.STORE,
            NotificationType.OUT_OF_STOCK to WarehouseIds.DEPOT,
            NotificationType.TRANSFER_REQUIRED to WarehouseIds.STORE,
            NotificationType.PURCHASE_REQUIRED to WarehouseIds.DEPOT
        ).forEach { pair ->
            if (pair !in keep) {
                dao.markNotificationsRead(productId, pair.second, pair.first, System.currentTimeMillis())
            }
        }

        plan.notifications.forEach { planned ->
            val existing = dao.unreadNotification(productId, planned.warehouseId, planned.type)
            if (existing == null) {
                val id = dao.insertNotification(
                    AppNotificationEntity(
                        type = planned.type,
                        productId = productId,
                        warehouseId = planned.warehouseId,
                        message = planned.message,
                        severity = planned.severity,
                        actionType = planned.actionType
                    )
                )
                if (pushSystem && (planned.severity == AlertLevel.CRITICAL || planned.severity == AlertLevel.OUT_OF_STOCK)) {
                    notifyCritical(80000 + id.toInt(), "هشدار موجودی دینال", planned.message)
                }
            } else if (existing.message != planned.message) {
                dao.updateNotification(existing.copy(message = planned.message, severity = planned.severity))
            }
        }

        dao.clearOpenTransferSuggestions(productId)
        if (plan.transferQuantity > 0.000001) {
            dao.insertTransferSuggestion(
                TransferSuggestionEntity(
                    productId = productId,
                    sourceWarehouseId = WarehouseIds.DEPOT,
                    destinationWarehouseId = WarehouseIds.STORE,
                    quantity = plan.transferQuantity,
                    reason = "موجودی فروشگاه زیر حداقل است"
                )
            )
        }
        dao.clearOpenPurchaseSuggestions(productId)
        if (plan.purchaseQuantity > 0.000001) {
            dao.insertPurchaseSuggestion(
                PurchaseSuggestionEntity(
                    productId = productId,
                    quantity = plan.purchaseQuantity,
                    reason = "موجودی دپو/کل زیر نقطه سفارش است"
                )
            )
        }
    }

    suspend fun evaluateAllAlerts() {
        dao.products().forEach { evaluateAlerts(it.id, pushSystem = false) }
    }

    suspend fun enqueueChannelStock(productId: Long) {
        val store = dao.inventoryOne(productId, WarehouseIds.STORE)?.toSnapshot()?.available ?: 0.0
        val depot = dao.inventoryOne(productId, WarehouseIds.DEPOT)?.toSnapshot()?.available ?: 0.0
        val policies = dao.channelPolicies().associateBy { it.channelId }
        dao.channels().filter { it.isActive && it.integrationMode != IntegrationMode.DISABLED }.forEach { channel ->
            val policy = policies[channel.id] ?: return@forEach
            val qty = InventoryMath.channelAvailable(policy.policyType, store, depot, policy.safetyStock)
            val existing = dao.syncQueueItem(productId, channel.id)
            dao.upsertSyncQueue(
                (existing ?: StockSyncQueueEntity(productId = productId, channelId = channel.id, calculatedQuantity = qty)).copy(
                    calculatedQuantity = qty,
                    status = SyncQueueStatus.PENDING,
                    lastError = null
                )
            )
        }
    }

    suspend fun upsertWooMapping(productId: Long, wooId: Long?, sku: String?, channelId: Long = ChannelIds.WOO_1) {
        if (wooId == null) return
        dao.upsertMapping(
            ProductChannelMappingEntity(
                productId = productId,
                channelId = channelId,
                externalProductId = wooId.toString(),
                externalSku = sku
            )
        )
    }

    suspend fun reserve(productId: Long, warehouseId: Int, quantity: Double, orderId: Long, orderNo: String) {
        InventoryLedger.reserve(snapshot(productId, warehouseId), quantity)
        mutate(
            productId, warehouseId, StockMutation(reservedDelta = quantity),
            TxType.RESERVATION, RefType.ORDER, orderId, orderNo, "رزرو سفارش $orderNo"
        )
    }

    suspend fun releaseReservation(productId: Long, warehouseId: Int, quantity: Double, orderId: Long, orderNo: String, type: Int = TxType.RESERVATION_RELEASE) {
        if (quantity <= 0.000001) return
        InventoryLedger.releaseReservation(snapshot(productId, warehouseId), quantity)
        mutate(
            productId, warehouseId, StockMutation(reservedDelta = -quantity),
            type, RefType.ORDER, orderId, orderNo, "آزادسازی رزرو $orderNo"
        )
    }

    suspend fun fulfillReservation(productId: Long, warehouseId: Int, quantity: Double, orderId: Long, orderNo: String) {
        InventoryLedger.fulfillReservation(snapshot(productId, warehouseId), quantity)
        mutate(
            productId, warehouseId, StockMutation(onHandDelta = -quantity, reservedDelta = -quantity),
            TxType.SALE, RefType.ORDER, orderId, orderNo, "ارسال سفارش $orderNo"
        )
    }

    private fun InventoryEntity.toSnapshot() = InventorySnapshot(
        productId = productId,
        warehouseId = warehouseId,
        onHand = quantity,
        reserved = reserved,
        damaged = damaged,
        inTransit = inTransit,
        version = version
    )

    private fun InventoryEntity.toPolicyView() = StockPolicyView(
        warehouseId = warehouseId,
        available = InventoryMath.available(quantity, reserved, damaged),
        minStock = minStock,
        warningThreshold = warningThreshold,
        criticalStock = criticalStock,
        targetStock = targetStock,
        reorderPoint = reorderPoint,
        targetTotal = targetTotal
    )
}

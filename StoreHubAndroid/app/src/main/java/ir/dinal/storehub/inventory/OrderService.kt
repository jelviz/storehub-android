package ir.dinal.storehub.inventory

import ir.dinal.storehub.data.AppNotificationEntity
import ir.dinal.storehub.data.ShopOrderEntity
import ir.dinal.storehub.data.ShopOrderItemEntity
import ir.dinal.storehub.data.StoreHubDao
import ir.dinal.storehub.sync.ChannelOrderDraft

data class OrderImportResult(
    val imported: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val cancelled: Int = 0,
    val message: String
)

class OrderService(
    private val dao: StoreHubDao,
    private val inventory: InventoryService,
    private val notify: (id: Int, title: String, text: String) -> Unit,
    private val createTransfer: suspend (productId: Long, quantity: Double, note: String) -> Long
) {
    suspend fun ingest(drafts: List<ChannelOrderDraft>): OrderImportResult {
        var imported = 0
        var updated = 0
        var skipped = 0
        var cancelled = 0
        drafts.forEach { draft ->
            val channelId = channelIdOf(draft.channelCode) ?: run { skipped++; return@forEach }
            val existing = dao.orderByExternal(channelId, draft.externalOrderId)
            val remoteCancelled = draft.externalStatus in setOf("cancelled", "refunded", "failed", "trash")
            if (existing != null) {
                if (remoteCancelled && OrderStatus.isOpen(existing.status)) {
                    cancel(existing.id, "لغو از کانال ${draft.channelCode}")
                    cancelled++
                } else {
                    if (existing.externalStatus != draft.externalStatus) {
                        dao.updateOrder(existing.copy(externalStatus = draft.externalStatus))
                        updated++
                    } else skipped++
                }
                return@forEach
            }
            if (remoteCancelled || draft.externalStatus == "completed") {
                skipped++
                return@forEach
            }
            if (draft.externalStatus !in setOf("pending", "on-hold", "processing")) {
                skipped++
                return@forEach
            }
            importNew(channelId, draft)
            imported++
        }
        return OrderImportResult(imported, updated, skipped, cancelled, "$imported سفارش جدید، $cancelled لغو از کانال، $updated به‌روز.")
    }

    private suspend fun importNew(channelId: Long, draft: ChannelOrderDraft): Long {
        val orderNo = "O-${draft.channelCode}-${draft.externalOrderId}"
        val orderId = dao.insertOrder(
            ShopOrderEntity(
                orderNo = orderNo,
                channelId = channelId,
                externalOrderId = draft.externalOrderId,
                externalStatus = draft.externalStatus,
                status = OrderStatus.NEW,
                customerName = draft.customerName,
                customerMobile = draft.customerMobile,
                shippingAddress = draft.shippingAddress,
                total = draft.total
            )
        )
        val items = draft.items.map { line ->
            val product = resolveProduct(channelId, line.externalProductId, line.sku)
            ShopOrderItemEntity(
                orderId = orderId,
                productId = product?.id ?: 0L,
                name = product?.name ?: line.name,
                sku = line.sku ?: product?.sku,
                externalProductId = line.externalProductId,
                externalVariationId = line.externalVariationId,
                quantity = line.quantity,
                unitPrice = line.unitPrice,
                lineTotal = line.lineTotal
            )
        }
        if (items.isNotEmpty()) dao.insertOrderItems(items)
        allocate(orderId)
        return orderId
    }

    suspend fun allocate(orderId: Long) {
        val order = dao.order(orderId) ?: return
        if (!OrderStatus.isOpen(order.status) || order.status in setOf(OrderStatus.PICKING, OrderStatus.PICKED, OrderStatus.PACKING, OrderStatus.PACKED)) return
        val items = dao.orderItems(orderId)
        val plans = ArrayList<OrderLinePlan>()
        items.forEach { item ->
            if (item.productId <= 0L) {
                val updated = item.copy(shortageQty = item.quantity, reservedStoreQty = 0.0, waitingDepotQty = 0.0)
                dao.updateOrderItem(updated)
                plans += OrderLinePlan(0.0, 0.0, item.quantity)
                return@forEach
            }
            val already = item.reservedStoreQty
            val need = (item.quantity - already).coerceAtLeast(0.0)
            if (need <= 0.000001) {
                dao.updateOrderItem(item.copy(waitingDepotQty = 0.0, shortageQty = 0.0))
                plans += OrderLinePlan(item.reservedStoreQty, 0.0, 0.0)
                return@forEach
            }
            inventory.ensureInventory(item.productId, WarehouseIds.STORE)
            inventory.ensureInventory(item.productId, WarehouseIds.DEPOT)
            val store = inventory.snapshot(item.productId, WarehouseIds.STORE)
            val depot = inventory.snapshot(item.productId, WarehouseIds.DEPOT)
            val plan = OrderAllocator.plan(need, store.available, depot.available)
            if (plan.reserveStore > 0.000001) {
                inventory.reserve(item.productId, WarehouseIds.STORE, plan.reserveStore, order.id, order.orderNo)
            }
            if (plan.waitDepot > 0.000001 && item.waitingDepotQty <= 0.000001) {
                createTransfer(item.productId, plan.waitDepot, "سفارش ${order.orderNo}")
                notify(
                    91000 + order.id.toInt(),
                    "سفارش منتظر دپو",
                    "${item.name} برای ${order.orderNo} باید از دپو به مغازه بیاید."
                )
                dao.insertNotification(
                    AppNotificationEntity(
                        type = NotificationType.ORDER_WAITING_FOR_DEPOT,
                        productId = item.productId,
                        warehouseId = WarehouseIds.STORE,
                        message = "${item.name} در سفارش ${order.orderNo} منتظر انتقال از دپو است.",
                        severity = AlertLevel.WARNING,
                        actionType = "TRANSFER",
                        actionReferenceId = order.id
                    )
                )
            }
            if (plan.shortage > 0.000001 && item.shortageQty <= 0.000001) {
                notify(
                    92000 + order.id.toInt(),
                    "کمبود سفارش آنلاین",
                    "${item.name} برای ${order.orderNo} موجودی کافی ندارد."
                )
                dao.insertNotification(
                    AppNotificationEntity(
                        type = NotificationType.ORDER_STOCK_SHORTAGE,
                        productId = item.productId,
                        warehouseId = WarehouseIds.STORE,
                        message = "${item.name} در سفارش ${order.orderNo} کمبود دارد.",
                        severity = AlertLevel.CRITICAL,
                        actionType = "PURCHASE",
                        actionReferenceId = order.id
                    )
                )
            }
            dao.updateOrderItem(
                item.copy(
                    reservedStoreQty = item.reservedStoreQty + plan.reserveStore,
                    waitingDepotQty = plan.waitDepot,
                    shortageQty = plan.shortage
                )
            )
            plans += plan.copy(reserveStore = item.reservedStoreQty + plan.reserveStore)
        }
        val status = OrderAllocator.status(plans)
        dao.updateOrder(
            order.copy(
                status = status,
                reservedAt = if (status != OrderStatus.SHORTAGE) System.currentTimeMillis() else order.reservedAt
            )
        )
    }

    suspend fun retryOpenOrders() {
        dao.orders().filter { it.status == OrderStatus.WAITING_DEPOT || it.status == OrderStatus.SHORTAGE }.forEach { allocate(it.id) }
    }

    suspend fun startPicking(orderId: Long) {
        val order = requireOpen(orderId)
        require(OrderStatus.canPick(order.status)) { "این سفارش هنوز برای چیدن آماده نیست. موجودی مغازه را رزرو کن." }
        requireReadyAtStore(orderId)
        dao.updateOrder(order.copy(status = OrderStatus.PICKING))
    }

    suspend fun confirmPicked(orderId: Long) {
        val order = requireOpen(orderId)
        require(order.status == OrderStatus.PICKING || order.status == OrderStatus.RESERVED) { "اول چیدن را شروع کن." }
        requireReadyAtStore(orderId)
        dao.orderItems(orderId).forEach { dao.updateOrderItem(it.copy(pickedQty = it.quantity)) }
        dao.updateOrder(order.copy(status = OrderStatus.PICKED, pickedAt = System.currentTimeMillis()))
    }

    suspend fun startPacking(orderId: Long) {
        val order = requireOpen(orderId)
        require(order.status == OrderStatus.PICKED || order.status == OrderStatus.PACKING) { "اول چیدن را تمام کن." }
        dao.updateOrder(order.copy(status = OrderStatus.PACKING))
    }

    suspend fun confirmPacked(orderId: Long) {
        val order = requireOpen(orderId)
        require(order.status == OrderStatus.PACKING || order.status == OrderStatus.PICKED) { "بسته‌بندی بعد از چیدن است." }
        dao.orderItems(orderId).forEach {
            require(it.pickedQty + 0.000001 >= it.quantity) { "${it.name} هنوز چیده نشده است." }
            dao.updateOrderItem(it.copy(packedQty = it.quantity))
        }
        dao.updateOrder(order.copy(status = OrderStatus.PACKED, packedAt = System.currentTimeMillis()))
    }

    suspend fun ship(orderId: Long) {
        val order = requireOpen(orderId)
        require(OrderStatus.canShip(order.status) || order.status == OrderStatus.PACKED) { "اول بسته‌بندی را تأیید کن." }
        dao.orderItems(orderId).forEach { item ->
            require(item.packedQty + 0.000001 >= item.quantity) { "${item.name} بسته‌بندی نشده است." }
            if (item.productId > 0 && item.reservedStoreQty > 0) {
                inventory.fulfillReservation(item.productId, WarehouseIds.STORE, item.reservedStoreQty, order.id, order.orderNo)
            }
        }
        dao.updateOrder(order.copy(status = OrderStatus.SHIPPED, shippedAt = System.currentTimeMillis()))
    }

    suspend fun cancel(orderId: Long, note: String?) {
        val order = dao.order(orderId) ?: error("سفارش پیدا نشد.")
        require(order.status != OrderStatus.SHIPPED) { "سفارش ارسال‌شده را نمی‌شود لغو کرد." }
        if (order.status == OrderStatus.CANCELLED) return
        dao.orderItems(orderId).forEach { item ->
            if (item.productId > 0 && item.reservedStoreQty > 0) {
                inventory.releaseReservation(
                    item.productId, WarehouseIds.STORE, item.reservedStoreQty, order.id, order.orderNo, TxType.ORDER_CANCELLED
                )
            }
            dao.updateOrderItem(item.copy(reservedStoreQty = 0.0, waitingDepotQty = 0.0))
        }
        dao.updateOrder(order.copy(status = OrderStatus.CANCELLED, cancelledAt = System.currentTimeMillis(), note = note ?: order.note))
    }

    private suspend fun requireReadyAtStore(orderId: Long) {
        dao.orderItems(orderId).forEach { item ->
            require(item.productId > 0) { "کالای «${item.name}» در کاتالوگ محلی پیدا نشد." }
            require(item.shortageQty <= 0.000001 && item.waitingDepotQty <= 0.000001) {
                "${item.name} هنوز کامل در مغازه رزرو نشده است."
            }
            require(item.reservedStoreQty + 0.000001 >= item.quantity) { "رزرو مغازه برای ${item.name} کامل نیست." }
        }
    }

    private suspend fun requireOpen(orderId: Long): ShopOrderEntity {
        val order = dao.order(orderId) ?: error("سفارش پیدا نشد.")
        require(OrderStatus.isOpen(order.status)) { "این سفارش بسته است." }
        return order
    }

    private suspend fun resolveProduct(channelId: Long, externalProductId: String?, sku: String?) =
        externalProductId?.takeIf { it.isNotBlank() }?.let { dao.mappingByExternal(channelId, it) }?.let { dao.product(it.productId) }
            ?: externalProductId?.toLongOrNull()?.let { dao.productByWooId(it) }
            ?: sku?.takeIf { it.isNotBlank() }?.let { dao.productByCode(it) }
            ?: sku?.takeIf { it.isNotBlank() }?.let { dao.mappingBySku(channelId, it) }?.let { dao.product(it.productId) }

    private fun channelIdOf(code: String): Long? = when (code) {
        ChannelCodes.WOO_1 -> ChannelIds.WOO_1
        ChannelCodes.WOO_2 -> ChannelIds.WOO_2
        ChannelCodes.WOO_3 -> ChannelIds.WOO_3
        ChannelCodes.SNAPP -> ChannelIds.SNAPP
        ChannelCodes.TAPSI -> ChannelIds.TAPSI
        else -> null
    }
}

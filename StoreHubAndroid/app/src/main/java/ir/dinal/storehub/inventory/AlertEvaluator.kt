package ir.dinal.storehub.inventory

data class StockPolicyView(
    val warehouseId: Int,
    val available: Double,
    val minStock: Double,
    val warningThreshold: Double,
    val criticalStock: Double,
    val targetStock: Double,
    val reorderPoint: Double,
    val targetTotal: Double
)

data class PlannedNotification(
    val type: String,
    val warehouseId: Int,
    val severity: String,
    val message: String,
    val actionType: String? = null
)

data class AlertPlan(
    val storeLevel: String,
    val depotLevel: String,
    val notifications: List<PlannedNotification>,
    val transferQuantity: Double,
    val purchaseQuantity: Double
)

object AlertEvaluator {
    fun evaluate(productName: String, store: StockPolicyView, depot: StockPolicyView): AlertPlan {
        val storeLevel = InventoryMath.alertLevel(store.available, store.warningThreshold, store.minStock, store.criticalStock)
        val depotLevel = InventoryMath.alertLevel(depot.available, depot.warningThreshold, depot.minStock, depot.criticalStock)
        val notifications = ArrayList<PlannedNotification>()

        fun addStockAlerts(level: String, row: StockPolicyView, lowType: String, place: String) {
            val qty = formatQty(row.available)
            when (level) {
                AlertLevel.OUT_OF_STOCK -> notifications += PlannedNotification(
                    type = NotificationType.OUT_OF_STOCK,
                    warehouseId = row.warehouseId,
                    severity = AlertLevel.OUT_OF_STOCK,
                    message = "موجودی $productName در $place صفر شده است.",
                    actionType = "SYNC"
                )
                AlertLevel.CRITICAL -> notifications += PlannedNotification(
                    type = NotificationType.CRITICAL_STOCK,
                    warehouseId = row.warehouseId,
                    severity = AlertLevel.CRITICAL,
                    message = "موجودی $productName در $place به $qty عدد رسیده و بحرانی است.",
                    actionType = if (row.warehouseId == WarehouseIds.STORE) "TRANSFER" else "PURCHASE"
                )
                AlertLevel.LOW, AlertLevel.WARNING -> notifications += PlannedNotification(
                    type = lowType,
                    warehouseId = row.warehouseId,
                    severity = level,
                    message = "موجودی $productName در $place به $qty عدد رسیده است.",
                    actionType = if (row.warehouseId == WarehouseIds.STORE) "TRANSFER" else "PURCHASE"
                )
            }
        }

        addStockAlerts(storeLevel, store, NotificationType.LOW_STORE_STOCK, "فروشگاه")
        addStockAlerts(depotLevel, depot, NotificationType.LOW_DEPOT_STOCK, "دپو")

        val transferQty = InventoryMath.suggestedTransfer(store.available, store.minStock, store.targetStock, depot.available)
        if (transferQty > 0.000001) {
            notifications += PlannedNotification(
                type = NotificationType.TRANSFER_REQUIRED,
                warehouseId = WarehouseIds.STORE,
                severity = storeLevel,
                message = "پیشنهاد انتقال $productName: ${formatQty(transferQty)} عدد از دپو به فروشگاه.",
                actionType = "TRANSFER"
            )
        }

        val purchaseQty = InventoryMath.suggestedPurchase(
            storeAvailable = store.available,
            depotAvailable = depot.available,
            depotMin = depot.minStock,
            reorderPoint = maxOf(store.reorderPoint, depot.reorderPoint),
            targetTotal = maxOf(store.targetTotal, depot.targetTotal)
        )
        if (purchaseQty > 0.000001) {
            notifications += PlannedNotification(
                type = NotificationType.PURCHASE_REQUIRED,
                warehouseId = WarehouseIds.DEPOT,
                severity = if (depotLevel == AlertLevel.OUT_OF_STOCK) AlertLevel.OUT_OF_STOCK else AlertLevel.LOW,
                message = "پیشنهاد خرید $productName: ${formatQty(purchaseQty)} عدد.",
                actionType = "PURCHASE"
            )
        }

        return AlertPlan(storeLevel, depotLevel, notifications, transferQty, purchaseQty)
    }

    private fun formatQty(value: Double): String =
        if (kotlin.math.abs(value - value.toLong()) < 0.000001) value.toLong().toString() else value.toString()
}

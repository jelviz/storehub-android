package ir.dinal.storehub.inventory

data class OrderLinePlan(
    val reserveStore: Double,
    val waitDepot: Double,
    val shortage: Double
) {
    val covered: Double get() = reserveStore + waitDepot
}

object OrderAllocator {
    fun plan(need: Double, storeAvailable: Double, depotAvailable: Double): OrderLinePlan {
        require(need > 0) { "تعداد سفارش باید بیشتر از صفر باشد." }
        val store = need.coerceAtMost(storeAvailable.coerceAtLeast(0.0))
        val rest = (need - store).coerceAtLeast(0.0)
        val depot = rest.coerceAtMost(depotAvailable.coerceAtLeast(0.0))
        val shortage = (rest - depot).coerceAtLeast(0.0)
        return OrderLinePlan(store, depot, shortage)
    }

    fun status(lines: List<OrderLinePlan>): String {
        if (lines.isEmpty()) return OrderStatus.SHORTAGE
        if (lines.any { it.shortage > 0.000001 }) return OrderStatus.SHORTAGE
        if (lines.any { it.waitDepot > 0.000001 }) return OrderStatus.WAITING_DEPOT
        return OrderStatus.RESERVED
    }
}

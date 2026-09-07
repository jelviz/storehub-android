package ir.dinal.storehub.inventory

data class InventorySnapshot(
    val productId: Long,
    val warehouseId: Int,
    val onHand: Double,
    val reserved: Double = 0.0,
    val damaged: Double = 0.0,
    val inTransit: Double = 0.0,
    val version: Long = 0
) {
    val available: Double get() = InventoryMath.available(onHand, reserved, damaged)
}

data class StockMutation(
    val onHandDelta: Double = 0.0,
    val reservedDelta: Double = 0.0,
    val damagedDelta: Double = 0.0,
    val inTransitDelta: Double = 0.0
)

object InventoryLedger {
    fun apply(
        current: InventorySnapshot,
        mutation: StockMutation,
        allowNegativeOnHand: Boolean = false
    ): InventorySnapshot {
        val onHand = current.onHand + mutation.onHandDelta
        val reserved = current.reserved + mutation.reservedDelta
        val damaged = current.damaged + mutation.damagedDelta
        val inTransit = current.inTransit + mutation.inTransitDelta
        require(reserved >= -0.000001) { "رزرو نمی‌تواند منفی شود." }
        require(damaged >= -0.000001) { "موجودی آسیب‌دیده نمی‌تواند منفی شود." }
        require(inTransit >= -0.000001) { "موجودی در مسیر نمی‌تواند منفی شود." }
        if (!allowNegativeOnHand) {
            require(onHand >= -0.000001) { "موجودی نمی‌تواند منفی شود." }
            require(reserved + damaged <= onHand + 0.000001) { "موجودی قابل فروش کافی نیست." }
        }
        val nextOnHand = if (allowNegativeOnHand) onHand else onHand.coerceAtLeast(0.0)
        return current.copy(
            onHand = nextOnHand,
            reserved = reserved.coerceAtLeast(0.0),
            damaged = damaged.coerceAtLeast(0.0),
            inTransit = inTransit.coerceAtLeast(0.0),
            version = current.version + 1
        )
    }

    fun sell(current: InventorySnapshot, quantity: Double): InventorySnapshot {
        require(quantity > 0) { "تعداد فروش باید بیشتر از صفر باشد." }
        require(current.available + 0.000001 >= quantity) { "موجودی قابل فروش کافی نیست." }
        return apply(current, StockMutation(onHandDelta = -quantity))
    }

    fun reserve(current: InventorySnapshot, quantity: Double): InventorySnapshot {
        require(quantity > 0) { "تعداد رزرو باید بیشتر از صفر باشد." }
        require(current.available + 0.000001 >= quantity) { "موجودی قابل رزرو کافی نیست." }
        return apply(current, StockMutation(reservedDelta = quantity))
    }

    fun releaseReservation(current: InventorySnapshot, quantity: Double): InventorySnapshot {
        require(quantity > 0) { "تعداد آزادسازی باید بیشتر از صفر باشد." }
        require(current.reserved + 0.000001 >= quantity) { "رزرو کافی برای آزادسازی نیست." }
        return apply(current, StockMutation(reservedDelta = -quantity))
    }

    fun versionsConflict(expected: Long, actual: Long): Boolean = expected != actual
}

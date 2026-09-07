package ir.dinal.storehub.inventory

object InventoryMath {
    fun available(onHand: Double, reserved: Double, damaged: Double): Double =
        (onHand - reserved - damaged).coerceAtLeast(0.0)

    fun alertLevel(
        available: Double,
        warningThreshold: Double,
        minStock: Double,
        criticalStock: Double
    ): String {
        if (available <= 0.000001) return AlertLevel.OUT_OF_STOCK
        if (criticalStock > 0 && available <= criticalStock) return AlertLevel.CRITICAL
        if (minStock > 0 && available <= minStock) return AlertLevel.LOW
        if (warningThreshold > 0 && available <= warningThreshold) return AlertLevel.WARNING
        return AlertLevel.NORMAL
    }

    fun channelAvailable(
        policyType: String,
        storeAvailable: Double,
        depotAvailable: Double,
        safetyStock: Double
    ): Double {
        val raw = when (policyType) {
            ChannelPolicyType.STORE_AVAILABLE -> storeAvailable
            ChannelPolicyType.DEPOT_AVAILABLE -> depotAvailable
            ChannelPolicyType.CUSTOM -> storeAvailable
            else -> storeAvailable + depotAvailable
        }
        return (raw - safetyStock).coerceAtLeast(0.0)
    }

    fun suggestedTransfer(
        storeAvailable: Double,
        minStock: Double,
        targetStock: Double,
        depotAvailable: Double
    ): Double {
        if (depotAvailable <= 0.000001) return 0.0
        val needsReplenish = minStock > 0 && storeAvailable <= minStock
        if (!needsReplenish) return 0.0
        val target = if (targetStock > storeAvailable) targetStock else if (minStock > storeAvailable) minStock else 0.0
        if (target <= storeAvailable) return 0.0
        return (target - storeAvailable).coerceAtMost(depotAvailable)
    }

    fun suggestedPurchase(
        storeAvailable: Double,
        depotAvailable: Double,
        depotMin: Double,
        reorderPoint: Double,
        targetTotal: Double
    ): Double {
        val total = storeAvailable + depotAvailable
        val depotLow = depotMin > 0 && depotAvailable <= depotMin
        val trigger = when {
            reorderPoint > 0 -> reorderPoint
            depotMin > 0 -> depotMin
            else -> 0.0
        }
        val belowReorder = trigger > 0 && total <= trigger
        if (!depotLow && !belowReorder) return 0.0
        val target = when {
            targetTotal > total -> targetTotal
            trigger > 0 -> trigger * 3
            depotMin > 0 -> depotMin * 3
            else -> 0.0
        }
        return (target - total).coerceAtLeast(0.0)
    }
}

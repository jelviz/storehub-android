package ir.dinal.storehub.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class InventoryMathTest {
    @Test
    fun availableSubtractsReservedAndDamaged() {
        assertEquals(3.0, InventoryMath.available(5.0, 2.0, 0.0), 0.0)
        assertEquals(2.0, InventoryMath.available(5.0, 2.0, 1.0), 0.0)
        assertEquals(0.0, InventoryMath.available(1.0, 1.0, 1.0), 0.0)
    }

    @Test
    fun alertLevelsFollowThresholdOrder() {
        assertEquals(AlertLevel.NORMAL, InventoryMath.alertLevel(5.0, 4.0, 2.0, 1.0))
        assertEquals(AlertLevel.WARNING, InventoryMath.alertLevel(4.0, 4.0, 2.0, 1.0))
        assertEquals(AlertLevel.LOW, InventoryMath.alertLevel(2.0, 4.0, 2.0, 1.0))
        assertEquals(AlertLevel.CRITICAL, InventoryMath.alertLevel(1.0, 4.0, 2.0, 1.0))
        assertEquals(AlertLevel.OUT_OF_STOCK, InventoryMath.alertLevel(0.0, 4.0, 2.0, 1.0))
    }

    @Test
    fun channelPolicyDoesNotBlindlySumForSnapp() {
        val total = InventoryMath.channelAvailable(ChannelPolicyType.TOTAL_AVAILABLE, 2.0, 8.0, 1.0)
        val storeOnly = InventoryMath.channelAvailable(ChannelPolicyType.STORE_AVAILABLE, 2.0, 8.0, 1.0)
        assertEquals(9.0, total, 0.0)
        assertEquals(1.0, storeOnly, 0.0)
    }

    @Test
    fun storeLowStockSuggestsTransferCappedByDepot() {
        val qty = InventoryMath.suggestedTransfer(storeAvailable = 1.0, minStock = 2.0, targetStock = 5.0, depotAvailable = 10.0)
        assertEquals(4.0, qty, 0.0)
        assertEquals(0.0, InventoryMath.suggestedTransfer(1.0, 2.0, 5.0, 0.0), 0.0)
    }

    @Test
    fun depotLowStockSuggestsPurchase() {
        val qty = InventoryMath.suggestedPurchase(
            storeAvailable = 1.0,
            depotAvailable = 2.0,
            depotMin = 5.0,
            reorderPoint = 5.0,
            targetTotal = 15.0
        )
        assertEquals(12.0, qty, 0.0)
    }
}

class InventoryLedgerTest {
    private fun row(onHand: Double, reserved: Double = 0.0, version: Long = 0) =
        InventorySnapshot(productId = 1, warehouseId = WarehouseIds.STORE, onHand = onHand, reserved = reserved, version = version)

    @Test
    fun saleDecrementsOnHand() {
        val next = InventoryLedger.sell(row(3.0), 1.0)
        assertEquals(2.0, next.onHand, 0.0)
        assertEquals(2.0, next.available, 0.0)
        assertEquals(1L, next.version)
    }

    @Test
    fun secondSaleFailsWhenStockIsOne() {
        val afterFirst = InventoryLedger.sell(row(1.0), 1.0)
        val second = runCatching { InventoryLedger.sell(afterFirst, 1.0) }
        assertTrue(second.isFailure)
    }

    @Test
    fun concurrentSaleOnlyOneSucceedsWhenStockIsOne() {
        val stock = AtomicReference(row(1.0, version = 0))
        val successes = AtomicInteger(0)
        val failures = AtomicInteger(0)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)
        repeat(2) {
            pool.execute {
                start.await()
                var won = false
                while (!won) {
                    val current = stock.get()
                    val attempt = runCatching { InventoryLedger.sell(current, 1.0) }
                    if (attempt.isFailure) {
                        failures.incrementAndGet()
                        won = true
                    } else {
                        val next = attempt.getOrThrow()
                        if (stock.compareAndSet(current, next)) {
                            successes.incrementAndGet()
                            won = true
                        }
                    }
                }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(3, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertEquals(1, successes.get())
        assertEquals(1, failures.get())
        assertEquals(0.0, stock.get().available, 0.0)
    }

    @Test
    fun reservationThenCancelReleasesStock() {
        val reserved = InventoryLedger.reserve(row(5.0), 1.0)
        assertEquals(4.0, reserved.available, 0.0)
        assertEquals(1.0, reserved.reserved, 0.0)
        val released = InventoryLedger.releaseReservation(reserved, 1.0)
        assertEquals(5.0, released.available, 0.0)
        assertEquals(0.0, released.reserved, 0.0)
    }

    @Test
    fun twoReservationsCannotTakeTheSameLastUnit() {
        val start = row(1.0)
        val first = InventoryLedger.reserve(start, 1.0)
        val second = runCatching { InventoryLedger.reserve(first, 1.0) }
        assertTrue(second.isFailure)
        assertEquals(0.0, first.available, 0.0)
    }

    @Test
    fun reservationFulfillmentRemovesOnHandAndReserved() {
        val reserved = InventoryLedger.reserve(row(5.0), 2.0)
        val shipped = InventoryLedger.fulfillReservation(reserved, 2.0)
        assertEquals(3.0, shipped.onHand, 0.0)
        assertEquals(0.0, shipped.reserved, 0.0)
        assertEquals(3.0, shipped.available, 0.0)
    }

    @Test
    fun transferInTransitDoesNotCountAsStoreStock() {
        val depot = InventorySnapshot(1, WarehouseIds.DEPOT, onHand = 10.0)
        val store = InventorySnapshot(1, WarehouseIds.STORE, onHand = 2.0)
        val afterSendDepot = InventoryLedger.apply(depot, StockMutation(onHandDelta = -3.0))
        val afterSendStore = InventoryLedger.apply(store, StockMutation(inTransitDelta = 3.0))
        assertEquals(7.0, afterSendDepot.onHand, 0.0)
        assertEquals(2.0, afterSendStore.available, 0.0)
        assertEquals(3.0, afterSendStore.inTransit, 0.0)
        val afterReceive = InventoryLedger.apply(afterSendStore, StockMutation(onHandDelta = 3.0, inTransitDelta = -3.0))
        assertEquals(5.0, afterReceive.onHand, 0.0)
        assertEquals(0.0, afterReceive.inTransit, 0.0)
    }
}

class AlertEvaluatorTest {
    private fun store(available: Double, min: Double = 2.0, target: Double = 5.0) = StockPolicyView(
        warehouseId = WarehouseIds.STORE,
        available = available,
        minStock = min,
        warningThreshold = 4.0,
        criticalStock = 0.0,
        targetStock = target,
        reorderPoint = 0.0,
        targetTotal = 0.0
    )

    private fun depot(available: Double, min: Double = 5.0, reorder: Double = 5.0, targetTotal: Double = 15.0) = StockPolicyView(
        warehouseId = WarehouseIds.DEPOT,
        available = available,
        minStock = min,
        warningThreshold = 8.0,
        criticalStock = 2.0,
        targetStock = 0.0,
        reorderPoint = reorder,
        targetTotal = targetTotal
    )

    @Test
    fun storeLowStockCreatesTransferSuggestion() {
        val plan = AlertEvaluator.evaluate("فندک مدل X", store(1.0), depot(10.0))
        assertEquals(AlertLevel.LOW, plan.storeLevel)
        assertEquals(4.0, plan.transferQuantity, 0.0)
        assertTrue(plan.notifications.any { it.type == NotificationType.LOW_STORE_STOCK })
        assertTrue(plan.notifications.any { it.type == NotificationType.TRANSFER_REQUIRED })
    }

    @Test
    fun depotLowStockCreatesPurchaseSuggestion() {
        val plan = AlertEvaluator.evaluate("عروسک کرومی", store(1.0), depot(2.0))
        assertEquals(AlertLevel.CRITICAL, plan.depotLevel)
        assertEquals(12.0, plan.purchaseQuantity, 0.0)
        assertTrue(plan.notifications.any { it.type == NotificationType.LOW_DEPOT_STOCK || it.type == NotificationType.CRITICAL_STOCK })
        assertTrue(plan.notifications.any { it.type == NotificationType.PURCHASE_REQUIRED })
    }

    @Test
    fun outOfStockNotificationIsImmediate() {
        val plan = AlertEvaluator.evaluate("کالا", store(0.0, min = 2.0), depot(0.0, min = 5.0))
        assertEquals(AlertLevel.OUT_OF_STOCK, plan.storeLevel)
        assertTrue(plan.notifications.any { it.type == NotificationType.OUT_OF_STOCK })
        assertEquals(0.0, InventoryMath.channelAvailable(ChannelPolicyType.TOTAL_AVAILABLE, 0.0, 0.0, 0.0), 0.0)
    }
}

class OrderAllocatorTest {
    @Test
    fun storeCoversWholeOrder() {
        val plan = OrderAllocator.plan(3.0, storeAvailable = 5.0, depotAvailable = 10.0)
        assertEquals(3.0, plan.reserveStore, 0.0)
        assertEquals(0.0, plan.waitDepot, 0.0)
        assertEquals(0.0, plan.shortage, 0.0)
        assertEquals(OrderStatus.RESERVED, OrderAllocator.status(listOf(plan)))
    }

    @Test
    fun leftoverWaitsOnDepot() {
        val plan = OrderAllocator.plan(5.0, storeAvailable = 2.0, depotAvailable = 8.0)
        assertEquals(2.0, plan.reserveStore, 0.0)
        assertEquals(3.0, plan.waitDepot, 0.0)
        assertEquals(0.0, plan.shortage, 0.0)
        assertEquals(OrderStatus.WAITING_DEPOT, OrderAllocator.status(listOf(plan)))
    }

    @Test
    fun missingStockIsShortage() {
        val plan = OrderAllocator.plan(5.0, storeAvailable = 1.0, depotAvailable = 1.0)
        assertEquals(1.0, plan.reserveStore, 0.0)
        assertEquals(1.0, plan.waitDepot, 0.0)
        assertEquals(3.0, plan.shortage, 0.0)
        assertEquals(OrderStatus.SHORTAGE, OrderAllocator.status(listOf(plan)))
    }
}

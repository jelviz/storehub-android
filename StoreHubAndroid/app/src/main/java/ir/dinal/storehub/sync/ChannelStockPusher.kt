package ir.dinal.storehub.sync

import ir.dinal.storehub.data.ProductChannelMappingEntity
import ir.dinal.storehub.data.StockSyncQueueEntity
import ir.dinal.storehub.data.StoreHubDao
import ir.dinal.storehub.inventory.ChannelIds
import ir.dinal.storehub.inventory.IntegrationMode
import ir.dinal.storehub.inventory.SyncQueueStatus

data class StockPushResult(
    val sent: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val message: String
)

class ChannelStockPusher(
    private val dao: StoreHubDao,
    private val connectors: Map<Long, CommerceChannelConnector>
) {
    suspend fun flush(limit: Int = 80): StockPushResult {
        val pending = dao.syncQueueByStatus(SyncQueueStatus.PENDING) + dao.syncQueueByStatus(SyncQueueStatus.FAILED)
        val channels = dao.channels().associateBy { it.id }
        var sent = 0
        var failed = 0
        var skipped = 0
        pending.take(limit).forEach { item ->
            val channel = channels[item.channelId]
            if (channel == null || !channel.isActive || channel.integrationMode == IntegrationMode.DISABLED) {
                skipped++
                return@forEach
            }
            if (channel.integrationMode != IntegrationMode.API) {
                skipped++
                return@forEach
            }
            val connector = connectors[item.channelId]
            if (connector == null) {
                mark(item, SyncQueueStatus.FAILED, item.retryCount, "اتصال API این کانال تنظیم نشده است.")
                failed++
                return@forEach
            }
            val mapping = mappingFor(item)
            val externalId = mapping?.externalProductId.orEmpty()
            if (externalId.isBlank() && mapping?.externalSku.isNullOrBlank()) {
                mark(item, SyncQueueStatus.FAILED, item.retryCount + 1, "این کالا روی کانال مپ نشده است.")
                failed++
                return@forEach
            }
            runCatching {
                connector.updateStock(
                    externalProductId = externalId.ifBlank { mapping?.externalSku.orEmpty() },
                    externalVariationId = mapping?.externalVariationId,
                    sku = mapping?.externalSku,
                    quantity = item.calculatedQuantity.toInt()
                )
            }.onSuccess {
                mark(item, SyncQueueStatus.SUCCESS, 0, null)
                sent++
            }.onFailure { e ->
                mark(item, SyncQueueStatus.FAILED, item.retryCount + 1, e.message)
                failed++
            }
        }
        return StockPushResult(sent, failed, skipped, "$sent ارسال شد، $failed ناموفق، $skipped رد شد (دستی/خاموش).")
    }

    suspend fun markManualSent(id: Long) {
        val item = dao.allSyncQueue().firstOrNull { it.id == id } ?: return
        mark(item, SyncQueueStatus.SUCCESS, item.retryCount, "ارسال دستی")
    }

    private suspend fun mappingFor(item: StockSyncQueueEntity): ProductChannelMappingEntity? {
        dao.mapping(item.productId, item.channelId)?.let { return it }
        if (item.channelId == ChannelIds.WOO_1) {
            val product = dao.product(item.productId)
            val wooId = product?.wooId
            if (wooId != null) {
                return ProductChannelMappingEntity(
                    productId = item.productId,
                    channelId = ChannelIds.WOO_1,
                    externalProductId = wooId.toString(),
                    externalSku = product.sku
                )
            }
        }
        return null
    }

    private suspend fun mark(item: StockSyncQueueEntity, status: String, retry: Int, error: String?) {
        dao.updateSyncQueue(item.id, status, retry, System.currentTimeMillis(), error)
    }
}

package ir.dinal.storehub.sync

/**
 * Outbound commerce connector. WooCommerce / Snapp / Tapsi should implement this.
 * StoreHub remains source of truth; connector failures must never roll back local inventory.
 */
interface CommerceChannelConnector {
    suspend fun updateStock(externalProductId: String, externalVariationId: String?, quantity: Int)
    suspend fun getOrders(): List<ChannelOrderDraft> = emptyList()
}

data class ChannelOrderDraft(
    val externalOrderId: String,
    val channelCode: String,
    val customerName: String? = null,
    val total: Double = 0.0
)

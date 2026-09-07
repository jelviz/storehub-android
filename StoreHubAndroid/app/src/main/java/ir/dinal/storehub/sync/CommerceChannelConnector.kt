package ir.dinal.storehub.sync

/**
 * Outbound commerce connector. WooCommerce / Snapp / Tapsi should implement this.
 * StoreHub remains source of truth; connector failures must never roll back local inventory.
 */
interface CommerceChannelConnector {
    val channelCode: String
    suspend fun updateStock(externalProductId: String, externalVariationId: String?, sku: String?, quantity: Int)
    suspend fun getOrders(): List<ChannelOrderDraft> = emptyList()
    suspend fun updateOrderStatus(externalOrderId: String, status: String) {}
}

data class ChannelOrderLine(
    val name: String,
    val sku: String? = null,
    val quantity: Double,
    val unitPrice: Double = 0.0,
    val lineTotal: Double = 0.0,
    val externalProductId: String? = null,
    val externalVariationId: String? = null
)

data class ChannelOrderDraft(
    val externalOrderId: String,
    val channelCode: String,
    val customerName: String? = null,
    val customerMobile: String? = null,
    val shippingAddress: String? = null,
    val total: Double = 0.0,
    val externalStatus: String = "",
    val items: List<ChannelOrderLine> = emptyList()
)

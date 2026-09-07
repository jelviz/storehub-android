package ir.dinal.storehub.sync

import ir.dinal.storehub.data.WooClient
import ir.dinal.storehub.data.WooSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WooCommerceConnector(
    private val settings: WooSettings,
    override val channelCode: String
) : CommerceChannelConnector {
    private val client = WooClient(settings)

    override suspend fun updateStock(externalProductId: String, externalVariationId: String?, sku: String?, quantity: Int) =
        withContext(Dispatchers.IO) { client.updateStock(externalProductId, externalVariationId, quantity) }

    override suspend fun getOrders(): List<ChannelOrderDraft> =
        withContext(Dispatchers.IO) { client.fetchOrders(channelCode) }

    override suspend fun updateOrderStatus(externalOrderId: String, status: String) =
        withContext(Dispatchers.IO) { client.updateOrderStatus(externalOrderId, status) }
}

package ir.dinal.storehub.inventory

object WarehouseIds {
    const val STORE = 1
    const val DEPOT = 2
}

object WarehouseType {
    const val STORE = "STORE"
    const val DEPOT = "DEPOT"
}

object ChannelIds {
    const val WOO_1 = 1L
    const val WOO_2 = 2L
    const val WOO_3 = 3L
    const val SNAPP = 4L
    const val TAPSI = 5L
    const val POS = 6L
}

object ChannelCodes {
    const val WOO_1 = "WOOCOMMERCE_1"
    const val WOO_2 = "WOOCOMMERCE_2"
    const val WOO_3 = "WOOCOMMERCE_3"
    const val SNAPP = "SNAPP"
    const val TAPSI = "TAPSI"
    const val POS = "POS"
}

object ChannelPolicyType {
    const val TOTAL_AVAILABLE = "TOTAL_AVAILABLE"
    const val STORE_AVAILABLE = "STORE_AVAILABLE"
    const val DEPOT_AVAILABLE = "DEPOT_AVAILABLE"
    const val CUSTOM = "CUSTOM"
}

object IntegrationMode {
    const val API = "API"
    const val MANUAL = "MANUAL"
    const val DISABLED = "DISABLED"
}

/** Keep historical movement ints so existing rows stay valid. */
object TxType {
    const val OPENING = 1
    const val ADJUSTMENT = 2
    const val SALE = 3
    const val RETURN = 4
    const val TRANSFER_OUT = 5
    const val TRANSFER_IN = 6
    const val PURCHASE = 7
    const val RESERVATION = 8
    const val RESERVATION_RELEASE = 9
    const val DAMAGE = 10
    const val GIFT = 11
    const val INTERNAL_USE = 12
    const val STOCKTAKING_DIFFERENCE = 13
    const val ORDER_CANCELLED = 14

    fun label(type: Int): String = when (type) {
        OPENING -> "موجودی اولیه"
        ADJUSTMENT -> "تعدیل"
        SALE -> "فروش"
        RETURN -> "مرجوعی"
        TRANSFER_OUT -> "خروج انتقال"
        TRANSFER_IN -> "ورود انتقال"
        PURCHASE -> "خرید"
        RESERVATION -> "رزرو"
        RESERVATION_RELEASE -> "آزادسازی رزرو"
        DAMAGE -> "آسیب‌دیده"
        GIFT -> "هدیه"
        INTERNAL_USE -> "مصرف داخلی"
        STOCKTAKING_DIFFERENCE -> "اختلاف انبارگردانی"
        ORDER_CANCELLED -> "لغو سفارش"
        else -> "حرکت موجودی"
    }
}

object RefType {
    const val SALE = "SALE"
    const val PURCHASE = "PURCHASE"
    const val TRANSFER = "TRANSFER"
    const val ADJUSTMENT = "ADJUSTMENT"
    const val OPENING = "OPENING"
    const val ORDER = "ORDER"
    const val ENABLE = "ENABLE"
}

object AlertLevel {
    const val NORMAL = "NORMAL"
    const val WARNING = "WARNING"
    const val LOW = "LOW"
    const val CRITICAL = "CRITICAL"
    const val OUT_OF_STOCK = "OUT_OF_STOCK"

    fun label(level: String): String = when (level) {
        WARNING -> "هشدار"
        LOW -> "کم"
        CRITICAL -> "بحرانی"
        OUT_OF_STOCK -> "ناموجود"
        else -> "عادی"
    }
}

object NotificationType {
    const val LOW_STORE_STOCK = "LOW_STORE_STOCK"
    const val LOW_DEPOT_STOCK = "LOW_DEPOT_STOCK"
    const val CRITICAL_STOCK = "CRITICAL_STOCK"
    const val OUT_OF_STOCK = "OUT_OF_STOCK"
    const val PURCHASE_REQUIRED = "PURCHASE_REQUIRED"
    const val TRANSFER_REQUIRED = "TRANSFER_REQUIRED"
    const val ORDER_WAITING_FOR_DEPOT = "ORDER_WAITING_FOR_DEPOT"
    const val ORDER_STOCK_SHORTAGE = "ORDER_STOCK_SHORTAGE"
    const val SYNC_FAILED = "SYNC_FAILED"
    const val TRANSFER_RECEIVED = "TRANSFER_RECEIVED"
}

object SuggestionStatus {
    const val OPEN = "OPEN"
    const val DONE = "DONE"
    const val DISMISSED = "DISMISSED"
}

object SyncQueueStatus {
    const val PENDING = "PENDING"
    const val SYNCING = "SYNCING"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
}

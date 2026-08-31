package ir.dinal.storehub.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Single money formatter for the whole app. StoreHub monetary values are Toman. */
object MoneyFormat {
    fun number(value: Double): String = DecimalFormat(
        "#,##0",
        DecimalFormatSymbols(Locale.US).apply { groupingSeparator = ',' }
    ).format(value)

    fun toman(value: Double): String = "${number(value)} تومان"
}

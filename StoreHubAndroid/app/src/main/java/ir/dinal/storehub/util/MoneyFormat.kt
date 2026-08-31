package ir.dinal.storehub.util

/**
 * All StoreHub money is Toman.
 * Uses the Arabic thousands separator (٬) so grouping stays next to the digits
 * in RTL Persian UI instead of an ASCII comma that jumps to the wrong side.
 */
object MoneyFormat {
    const val SEPARATOR = '\u066C'

    fun groupDigits(digits: String): String {
        if (digits.isEmpty()) return ""
        return buildString(digits.length + digits.length / 3) {
            digits.forEachIndexed { index, ch ->
                if (index > 0 && (digits.length - index) % 3 == 0) append(SEPARATOR)
                append(ch)
            }
        }
    }

    /** Visual cursor offset after inserting grouping separators. */
    fun groupedOffset(originalLength: Int, originalOffset: Int): Int {
        val safe = originalOffset.coerceIn(0, originalLength)
        var extra = 0
        var i = 1
        while (i <= safe && i < originalLength) {
            if ((originalLength - i) % 3 == 0) extra++
            i++
        }
        return safe + extra
    }

    fun number(value: Double): String {
        if (!value.isFinite()) return "0"
        val grouped = groupDigits(kotlin.math.abs(value).toLong().toString())
        return if (value < 0) "-$grouped" else grouped
    }

    /** On-screen amount: keep the number LTR so separators cannot reverse. */
    fun toman(value: Double): String = "\u2066${number(value)}\u2069 تومان"

    /** Notifications, printers, and plain reports — no bidi marks. */
    fun tomanPlain(value: Double): String = "${number(value)} تومان"
}

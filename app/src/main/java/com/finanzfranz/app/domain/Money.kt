package com.finanzfranz.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

object Money {
    private fun fmt(): NumberFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)

    /** "1.234,56 €" */
    fun format(cents: Long): String = fmt().format(BigDecimal.valueOf(cents, 2))

    /** "+12,00 €" / "−12,00 €" */
    fun signed(cents: Long): String = (if (cents < 0) "−" else "+") + format(abs(cents))

    /** Parses user input like "12", "12,5", "12.50", "1.234,56", "-3". Returns cents or null. */
    fun parse(input: String): Long? {
        var s = input.trim().replace("€", "").replace(" ", "").replace(" ", "").replace("−", "-")
        if (s.isEmpty()) return null
        s = when {
            s.contains(',') -> s.replace(".", "").replace(',', '.')
            // German thousands separator without decimals: "1.500" = 1500 €
            Regex("""^-?\d{1,3}(\.\d{3})+$""").matches(s) -> s.replace(".", "")
            else -> s
        }
        val bd = s.toBigDecimalOrNull() ?: return null
        return try {
            bd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** Value for an input field: "12,50" (no currency sign). */
    fun toInput(cents: Long): String {
        val a = abs(cents)
        return (if (cents < 0) "-" else "") + "${a / 100},${(a % 100).toString().padStart(2, '0')}"
    }
}

package com.finanzfranz.app.domain

import com.finanzfranz.app.data.Src
import java.time.LocalDate

/** One recognised text line with its position on the photo (pixels). */
data class OcrLine(val text: String, val top: Int = 0, val bottom: Int = 0, val left: Int = 0, val right: Int = 0) {
    val centerY get() = (top + bottom) / 2
    val height get() = (bottom - top).coerceAtLeast(1)
}

data class ReceiptResult(
    val totalCents: Long? = null,
    val date: LocalDate? = null,
    val store: String? = null,
    /** [Src.ACCOUNT] (card), [Src.CASH] or null if unknown. */
    val source: Int? = null,
    /** Name of a suggested category ("Essen & Trinken" ...) or null. */
    val categoryHint: String? = null,
)

/**
 * Extracts total, date, store and payment method from the OCR text of a German receipt.
 * Pure Kotlin, so it can be unit-tested without a device.
 */
object ReceiptParser {
    private val amountRx = Regex("""(?<![\d.,])(-?\d{1,4})\s?[,.]\s?(\d{2})(?![\d]|[.,]\d)""")
    private val dateRx = Regex("""(?<!\d)(\d{1,2})[./-](\d{1,2})[./-](\d{4}|\d{2})(?!\d)""")

    /** Keywords for the total, best first. */
    private val totalKeys = listOf("ZU ZAHLEN", "ZUZAHLEN", "GESAMTBETRAG", "ENDBETRAG", "GESAMTSUMME", "SUMME", "GESAMT", "TOTAL", "BETRAG")
    private val notTotal = listOf("MWST", "MWST.", "NETTO", "STEUER", "UST", "ZWISCHENSUMME", "GEGEBEN", "CKGELD", "ZURUCK", "ZURÜCK", "RABATT", "PFAND")
    private val paidKeys = listOf("GEGEBEN", "CKGELD", "ZURUCK", "ZURÜCK", "WECHSELGELD")

    private val cardKeys = listOf("GIROCARD", "EC-KARTE", "EC KARTE", "ECKARTE", "KARTENZAHLUNG", "KARTE", "VISA", "MASTERCARD",
        "MAESTRO", "KONTAKTLOS", "DEBIT", "APPLE PAY", "GOOGLE PAY", "PAYPAL", "V PAY", "VPAY", "AMEX", "KREDITKARTE", "EC-CASH", "ELECTRONIC CASH")
    private val cashRx = Regex("""\bBAR\b|BARGELD|BARZAHLUNG|GEGEBEN|CKGELD|WECHSELGELD""")

    private const val FOOD = "Essen & Trinken"
    private const val LEISURE = "Freizeit"
    private const val OTHER = "Sonstiges"

    /** Known chains -> display name and category. */
    private val stores = listOf(
        "REWE" to FOOD, "EDEKA" to FOOD, "LIDL" to FOOD, "ALDI" to FOOD, "NETTO" to FOOD, "PENNY" to FOOD,
        "KAUFLAND" to FOOD, "NORMA" to FOOD, "GLOBUS" to FOOD, "TEGUT" to FOOD, "MARKTKAUF" to FOOD, "COMBI" to FOOD,
        "FAMILA" to FOOD, "HIT" to FOOD, "NAH UND GUT" to FOOD, "DENNS" to FOOD, "ALNATURA" to FOOD, "BIOMARKT" to FOOD,
        "MCDONALD" to FOOD, "BURGER KING" to FOOD, "SUBWAY" to FOOD, "KFC" to FOOD, "STARBUCKS" to FOOD, "BACKWERK" to FOOD,
        "BÄCKEREI" to FOOD, "BAECKEREI" to FOOD, "BACKEREI" to FOOD, "BÄCKER" to FOOD, "DÖNER" to FOOD, "PIZZA" to FOOD,
        "RESTAURANT" to FOOD, "CAFE" to FOOD, "IMBISS" to FOOD, "GETRÄNKE" to FOOD, "GETRANKE" to FOOD,
        "DM-DROGERIE" to OTHER, "DM DROGERIE" to OTHER, "ROSSMANN" to OTHER, "MÜLLER" to OTHER, "BUDNI" to OTHER, "APOTHEKE" to OTHER,
        "IKEA" to OTHER, "OBI" to OTHER, "BAUHAUS" to OTHER, "HORNBACH" to OTHER, "TOOM" to OTHER, "ACTION" to OTHER, "TEDI" to OTHER,
        "MEDIAMARKT" to LEISURE, "MEDIA MARKT" to LEISURE, "SATURN" to LEISURE, "CONRAD" to LEISURE, "DECATHLON" to LEISURE,
        "SHELL" to OTHER, "ARAL" to OTHER, "ESSO" to OTHER, "AVIA" to OTHER,
    )
    private val boringFirstLines = listOf("WILLKOMMEN", "KASSENBON", "BELEG", "RECHNUNG", "QUITTUNG", "FILIALE", "TEL", "WWW", "STR.", "STRASSE", "UST-ID", "STEUER")

    fun parse(text: String, today: LocalDate = LocalDate.now()): ReceiptResult =
        parse(text.lines().filter { it.isNotBlank() }.mapIndexed { i, l -> OcrLine(l, i * 10, i * 10 + 8, 0, 100) }, today)

    fun parse(rawLines: List<OcrLine>, today: LocalDate = LocalDate.now()): ReceiptResult {
        val lines = rawLines.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.top }, { it.left }))
        val up = lines.map { normalize(it.text) }

        val total = findTotal(lines, up)
        val date = findDate(up, today)
        val (store, hint) = findStore(lines, up)
        val all = up.joinToString("\n")
        val source = when {
            cardKeys.any { all.contains(it) } -> Src.ACCOUNT
            cashRx.containsMatchIn(all) -> Src.CASH
            else -> null
        }
        return ReceiptResult(total, date, store, source, hint)
    }

    private fun normalize(s: String) = s.uppercase().replace(Regex("\\s+"), " ").trim()

    fun amounts(s: String): List<Long> = amountRx.findAll(s).mapNotNull { m ->
        val euros = m.groupValues[1].toLongOrNull() ?: return@mapNotNull null
        val cents = m.groupValues[2].toLong()
        if (euros < 0) null else euros * 100 + cents
    }.toList()

    private fun findTotal(lines: List<OcrLine>, up: List<String>): Long? {
        for (key in totalKeys) {
            for (i in up.indices) {
                val u = up[i]
                if (!u.contains(key)) continue
                if (notTotal.any { u.contains(it) } && !(key == "ZU ZAHLEN" || key == "ZUZAHLEN")) continue
                // 1) amount on the same text line
                amounts(u).lastOrNull()?.let { if (it in 1..999_999) return it }
                // 2) amount on the same visual row (OCR often splits label and value)
                val row = lines[i]
                val sameRow = lines.indices.filter { j ->
                    j != i && kotlin.math.abs(lines[j].centerY - row.centerY) <= row.height * 0.7 && lines[j].left >= row.left
                }.flatMap { amounts(up[it]) }
                sameRow.lastOrNull()?.let { if (it in 1..999_999) return it }
                // 3) amount on the next line
                if (i + 1 < up.size) amounts(up[i + 1]).firstOrNull()?.let { if (it in 1..999_999) return it }
            }
        }
        // Fallback: biggest amount that is not the given money / change / tax.
        return up.indices
            .filter { i -> paidKeys.none { up[i].contains(it) } && !Regex("""\bBAR\b""").containsMatchIn(up[i]) && notTotal.none { up[i].contains(it) } }
            .flatMap { amounts(up[it]) }
            .filter { it in 1..99_999 }
            .maxOrNull()
    }

    private fun findDate(up: List<String>, today: LocalDate): LocalDate? {
        for (u in up) {
            for (m in dateRx.findAll(u)) {
                val d = m.groupValues[1].toInt()
                val mo = m.groupValues[2].toInt()
                var y = m.groupValues[3].toInt()
                if (y < 100) y += 2000
                val date = runCatching { LocalDate.of(y, mo, d) }.getOrNull() ?: continue
                if (!date.isAfter(today.plusDays(1)) && date.isAfter(today.minusYears(2))) return date
            }
        }
        return null
    }

    private fun findStore(lines: List<OcrLine>, up: List<String>): Pair<String?, String?> {
        val head = up.take(12)
        for ((key, cat) in stores) {
            val rx = Regex("""(?<![A-ZÄÖÜ])${Regex.escape(key)}(?![A-ZÄÖÜ])""")
            if (head.any { rx.containsMatchIn(it) }) return pretty(key) to cat
        }
        // dm writes just "dm" in its logo
        if (head.any { Regex("""^DM\b""").containsMatchIn(it) }) return "dm" to OTHER
        val first = lines.take(4).map { it.text.trim() }.firstOrNull { l ->
            val u = l.uppercase()
            l.count { it.isLetter() } >= 3 && l.count { it.isDigit() } <= 2 && boringFirstLines.none { u.contains(it) }
        }
        return first?.let { pretty(it) } to null
    }

    private fun pretty(s: String): String {
        val keepUpper = setOf("REWE", "EDEKA", "LIDL", "ALDI", "KFC", "OBI", "IKEA", "ARAL", "ESSO", "HIT", "AVIA", "TEDI")
        return s.trim().split(Regex("\\s+")).joinToString(" ") { w ->
            if (w.uppercase() in keepUpper) w.uppercase()
            else w.lowercase().replaceFirstChar { it.titlecase() }
        }.replace("Mcdonald", "McDonald's").replace("McDonald'ss", "McDonald's")
    }
}

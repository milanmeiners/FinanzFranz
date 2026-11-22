package com.finanzfranz.app.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.finanzfranz.app.data.AppData
import com.finanzfranz.app.data.Kind
import com.finanzfranz.app.data.Src
import com.finanzfranz.app.data.Tx
import com.finanzfranz.app.domain.Ledger
import com.finanzfranz.app.domain.Money
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object Exporter {
    private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)

    /** Signed amount as it affects the total wealth (expense negative, withdrawal 0). */
    private fun signedForList(t: Tx): Long = when (t.kind) {
        Kind.EXPENSE -> -t.amountCents
        Kind.INCOME -> t.amountCents
        Kind.WITHDRAWAL -> t.amountCents
        else -> t.amountCents
    }

    private fun kindText(t: Tx) = when (t.kind) {
        Kind.WITHDRAWAL -> "Abheben (Konto → Bar)"
        else -> Kind.label(t.kind)
    }

    /** CSV with ; separator and comma decimals, so Excel (German) opens it directly. */
    fun csv(d: AppData): String {
        val cats = d.categories.associateBy({ it.id }, { it.name })
        val sb = StringBuilder("﻿")
        sb.append("Datum;Art;Konto/Bar;Kategorie;Notiz;Betrag;Fixkosten\r\n")
        for (t in d.txs.sortedWith(compareBy({ it.epochDay }, { it.createdAt }))) {
            val cells = listOf(
                LocalDate.ofEpochDay(t.epochDay).format(dateFmt),
                kindText(t),
                if (t.kind == Kind.WITHDRAWAL) "" else Src.label(t.source),
                t.categoryId?.let { cats[it] } ?: "",
                t.note,
                Money.toInput(signedForList(t)),
                if (t.fixedCostId != null) "ja" else "",
            )
            sb.append(cells.joinToString(";") { esc(it) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun esc(s: String): String =
        if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** A4 monthly report. */
    fun pdf(d: AppData, ym: YearMonth, out: OutputStream) {
        val doc = PdfDocument()
        val w = 595
        val h = 842
        val margin = 48f
        val title = Paint().apply { textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
        val head = Paint().apply { textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = 0xFF777777.toInt(); isAntiAlias = true; letterSpacing = 0.1f }
        val body = Paint().apply { textSize = 10.5f; isAntiAlias = true }
        val bold = Paint(body).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val right = Paint(body).apply { textAlign = Paint.Align.RIGHT }
        val rightBold = Paint(bold).apply { textAlign = Paint.Align.RIGHT }
        val line = Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 0.6f }
        val barBg = Paint().apply { color = 0xFFEEEEEE.toInt() }
        val bar = Paint().apply { color = 0xFF000000.toInt() }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            y = margin + 10f
        }
        fun canvas() = page!!.canvas
        fun need(space: Float) { if (y + space > h - margin) newPage() }

        newPage()
        val stats = Ledger.monthStats(d.txs, ym)
        val cats = d.categories.associateBy { it.id }
        canvas().drawText("FinanzFranz – " + ym.format(monthFmt), margin, y + 10f, title)
        y += 40f
        val end = ym.atEndOfMonth()
        val bal = Ledger.balances(d.txs, if (end.isAfter(LocalDate.now())) LocalDate.now() else end)
        val summary = listOf(
            "Einnahmen" to Money.format(stats.income),
            "Ausgaben" to Money.format(stats.expense),
            "Saldo" to Money.signed(stats.net),
            "Kontostand (Monatsende/heute)" to Money.format(bal.account),
            "Bargeld (Monatsende/heute)" to Money.format(bal.cash),
        )
        for ((k, v) in summary) {
            canvas().drawText(k, margin, y, body); canvas().drawText(v, w - margin, y, rightBold); y += 17f
        }

        y += 16f
        canvas().drawText("AUSGABEN NACH KATEGORIE", margin, y, head); y += 16f
        val maxVal = (stats.byCategory.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
        val rows = d.categories.filter { (stats.byCategory[it.id] ?: 0L) > 0 || it.budgetCents != null }
            .sortedByDescending { stats.byCategory[it.id] ?: 0L }
        for (c in rows) {
            need(30f)
            val spent = stats.byCategory[c.id] ?: 0L
            val budgetText = c.budgetCents?.let { " / Budget " + Money.format(it) } ?: ""
            canvas().drawText(c.name, margin, y, body)
            canvas().drawText(Money.format(spent) + budgetText, w - margin, y, right)
            y += 6f
            val full = w - 2 * margin
            val ref = c.budgetCents?.takeIf { it > 0 } ?: maxVal
            val frac = (spent.toFloat() / ref).coerceIn(0f, 1f)
            canvas().drawRect(margin, y, margin + full, y + 4f, barBg)
            canvas().drawRect(margin, y, margin + full * frac, y + 4f, bar)
            y += 20f
        }

        y += 10f
        need(40f)
        canvas().drawText("BUCHUNGEN", margin, y, head); y += 14f
        val from = ym.atDay(1).toEpochDay()
        val to = end.toEpochDay()
        val txs = d.txs.filter { it.epochDay in from..to }.sortedWith(compareBy({ it.epochDay }, { it.createdAt }))
        if (txs.isEmpty()) { canvas().drawText("Keine Buchungen in diesem Monat.", margin, y + 4f, body); y += 18f }
        for (t in txs) {
            need(18f)
            val date = LocalDate.ofEpochDay(t.epochDay).format(DateTimeFormatter.ofPattern("dd.MM."))
            val what = t.note.ifBlank { t.categoryId?.let { cats[it]?.name } ?: kindText(t) }
            val detail = when (t.kind) {
                Kind.WITHDRAWAL -> "Konto → Bar"
                Kind.EXPENSE -> (t.categoryId?.let { cats[it]?.name } ?: "") + " · " + Src.label(t.source)
                else -> kindText(t) + " · " + Src.label(t.source)
            }
            canvas().drawText(date, margin, y + 4f, body)
            canvas().drawText(ellipsize(what, body, 220f), margin + 50f, y + 4f, body)
            canvas().drawText(ellipsize(detail, body, 170f), margin + 280f, y + 4f, Paint(body).apply { color = 0xFF777777.toInt() })
            val amount = if (t.kind == Kind.WITHDRAWAL) Money.format(t.amountCents) else Money.signed(signedForList(t))
            canvas().drawText(amount, w - margin, y + 4f, right)
            y += 10f
            canvas().drawLine(margin, y, w - margin, y, line)
            y += 8f
        }
        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
    }

    private fun ellipsize(s: String, p: Paint, max: Float): String {
        if (p.measureText(s) <= max) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }
}

package com.finanzfranz.app.domain

import com.finanzfranz.app.data.Src
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {
    private val today = LocalDate.of(2026, 11, 22)

    @Test fun reweCard() {
        val text = """
            REWE
            Musterstraße 12
            12345 Berlin
            UID Nr.: DE812706034
            EUR
            BANANEN                1,49 B
            VOLLMILCH 3,5%         1,19 B
            BROT                   2,79 B
            --------------------------------
            SUMME EUR              5,47
            ================================
            Geg. EC-Cash EUR       5,47
            Steuer %   Netto   Steuer  Brutto
            B= 7,0%    5,11    0,36    5,47
            21.11.2026 18:42  Bon-Nr.:4711
            Girocard kontaktlos
        """.trimIndent()
        val r = ReceiptParser.parse(text, today)
        assertEquals(547L, r.totalCents)
        assertEquals(LocalDate.of(2026, 11, 21), r.date)
        assertEquals("REWE", r.store)
        assertEquals(Src.ACCOUNT, r.source)
        assertEquals("Essen & Trinken", r.categoryHint)
    }

    @Test fun lidlCash() {
        val text = """
            LIDL
            Hauptstr. 5
            Gurken              0,79 A
            Käse                2,49 A
            Pfand               0,25 B
            zu zahlen          3,53
            BAR                10,00
            Rückgeld            6,47
            MwSt  A 7%   0,21
            22.11.26 10:15
        """.trimIndent()
        val r = ReceiptParser.parse(text, today)
        assertEquals(353L, r.totalCents)
        assertEquals(Src.CASH, r.source)
        assertEquals(LocalDate.of(2026, 11, 22), r.date)
        assertEquals("LIDL", r.store)
    }

    @Test fun labelAndAmountOnSeparateOcrLines() {
        // ML Kit often returns the label and the price as separate lines on the same row
        val lines = listOf(
            OcrLine("FPV Shop Hamburg", 0, 30, 10, 400),
            OcrLine("Propeller 5x4", 100, 130, 10, 300),
            OcrLine("12,90", 100, 130, 600, 700),
            OcrLine("Akku 4S 1300mAh", 140, 170, 10, 300),
            OcrLine("29,90", 140, 170, 600, 700),
            OcrLine("Gesamt", 200, 232, 10, 150),
            OcrLine("42,80", 202, 234, 600, 700),
            OcrLine("Kartenzahlung VISA", 260, 290, 10, 400),
            OcrLine("15.11.2026", 300, 330, 10, 200),
        )
        val r = ReceiptParser.parse(lines, today)
        assertEquals(4280L, r.totalCents)
        assertEquals(Src.ACCOUNT, r.source)
        assertEquals("Fpv Shop Hamburg", r.store)
        assertNull(r.categoryHint)
    }

    @Test fun fallbackLargestAmountAndNoPayment() {
        val text = """
            Bäckerei Schmidt
            2 Brötchen   0,90
            Kaffee       2,50
            3,40
        """.trimIndent()
        val r = ReceiptParser.parse(text, today)
        assertEquals(340L, r.totalCents)
        assertNull(r.source)
        assertEquals("Essen & Trinken", r.categoryHint)
    }

    @Test fun datesAreNotAmounts() {
        assertEquals(listOf<Long>(), ReceiptParser.amounts("21.11.26 18:42"))
        assertEquals(listOf(547L), ReceiptParser.amounts("SUMME EUR 5,47"))
    }

    @Test fun barcodeIsNotCash() {
        val r = ReceiptParser.parse("Shop\nBarcode 123\nSumme 4,00\nZahlbar sofort", today)
        assertNull(r.source)
        assertEquals(400L, r.totalCents)
    }

    // --- Fixes from the review (Nov 2026) ---

    @Test fun thousandsSeparator() {
        assertEquals(listOf(104900L), ReceiptParser.amounts("SUMME EUR 1.049,00"))
        val r = ReceiptParser.parse("MediaMarkt\nFernseher 1.049,00\nSUMME EUR 1.049,00\nGirocard", today)
        assertEquals(104900L, r.totalCents)
        assertEquals(Src.ACCOUNT, r.source)
    }

    @Test fun paybackCardIsNotCardPayment() {
        val text = """
            dm-drogerie markt
            Zahnpasta          1,95
            SUMME EUR          1,95
            BAR                5,00
            Rückgeld           3,05
            PAYBACK Karte: Sie hätten 2 Punkte
        """.trimIndent()
        assertEquals(Src.CASH, ReceiptParser.parse(text, today).source)
    }

    @Test fun cardWithZeroChangeStaysCard() {
        val r = ReceiptParser.parse("Shop\nSumme 9,90\nKarte 9,90\nRückgeld 0,00", today)
        assertEquals(Src.ACCOUNT, r.source)
    }

    @Test fun subtotalIsNotTotal() {
        val r = ReceiptParser.parse("Restaurant Adria\nPizza 12,50\nSubtotal 12,50\nTrinkgeld 1,50\nTotal 14,00", today)
        assertEquals(1400L, r.totalCents)
    }

    @Test fun nettoColumnIsNotNettoStore() {
        val text = """
            ARAL Tankstelle
            Super E10          45,00
            MwSt 19% Netto Steuer Brutto
            SUMME              45,00
        """.trimIndent()
        assertEquals("ARAL", ReceiptParser.parse(text, today).store)
        val bakery = ReceiptParser.parse("Bäckerei Schulz\nMwSt Netto Brutto\nSumme 3,20", today)
        assertEquals("Bäckerei", bakery.store)
        // real Netto supermarket still works
        assertEquals("Netto", ReceiptParser.parse("NETTO Marken-Discount\nSumme 3,20", today).store)
    }
}

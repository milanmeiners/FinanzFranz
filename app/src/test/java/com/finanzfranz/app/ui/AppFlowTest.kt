package com.finanzfranz.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.finanzfranz.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val shots = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(shots, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitText(text: String, substring: Boolean = false) {
        try {
            rule.waitUntil(10_000) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            shot("FAIL_" + text.filter { it.isLetterOrDigit() })
            throw AssertionError("Text not found: $text", e)
        }
    }

    private fun field(label: String): SemanticsNodeInteraction = rule.onNode(hasSetTextAction() and hasText(label))

    private fun type(label: String, value: String, scroll: Boolean = true) {
        if (scroll) {
            field(label).performScrollTo()
            field(label).performTextClearance()
            field(label).performTextInput(value)
        } else {
            field(label).performTextReplacement(value)
        }
    }

    private fun click(text: String, index: Int = 0) {
        rule.onAllNodesWithText(text)[index].performScrollTo().performClick()
    }

    private fun clickTab(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().let {
        rule.onAllNodesWithText(text)[it.size - 1].performClick()
    }

    private fun save() { rule.onNodeWithText("Speichern").performClick(); rule.waitForIdle() }

    @Test fun fullFlow() {
        // ---------- Setup
        waitText("Los geht's")
        shot("01_setup")
        type("Kontostand", "1500")
        type("Bargeld", "80")
        rule.onNodeWithText("Los geht's").performScrollTo().performClick()
        waitText("Frei verfügbar")
        waitText("1.500,00", substring = true)
        shot("02_overview_empty")

        // ---------- Fixed costs
        clickTab("Fixkosten")
        waitText("FESTE AUSGABEN")
        rule.onNodeWithContentDescription("Neue feste Ausgabe").performClick()
        waitText("RHYTHMUS")
        click("Miete")
        type("Betrag", "600")
        shot("03_fixed_editor")
        save()
        waitText("Miete")

        rule.onNodeWithContentDescription("Neue feste Ausgabe").performClick()
        waitText("RHYTHMUS")
        type("Name", "Haftpflicht")
        type("Betrag", "60")
        click("Jährlich")
        waitText("Entspricht", substring = true)
        save()
        waitText("Haftpflicht")

        click("+ Hinzufügen", 0) // feste Einnahme
        waitText("RHYTHMUS")
        type("Betrag", "1800")
        save()
        waitText("Gehalt")
        shot("04_fixed_list")

        // ---------- Overview shows planned money (rent is due tomorrow = same month in this test unless month end)
        clickTab("Übersicht")
        waitText("Frei verfügbar")
        shot("05_overview_planned")

        // ---------- Budget for food
        rule.onNodeWithContentDescription("Menü").performClick()
        click("Kategorien & Budgets")
        waitText("Essen & Trinken")
        click("Essen & Trinken")
        waitText("Monatsbudget (optional)")
        type("Monatsbudget (optional)", "20", scroll = false)
        rule.onAllNodesWithText("Speichern").let { it[0].performClick() }
        waitText("Budget 20,00", substring = true)
        shot("06_categories")
        rule.onNodeWithContentDescription("Zurück").performClick()

        // ---------- Expense from account -> budget warning
        rule.onNodeWithContentDescription("Neue Buchung").performClick()
        waitText("KATEGORIE")
        type("Betrag", "23,99")
        click("Essen & Trinken")
        type("Wofür? (z. B. Rewe, Propeller)", "Rewe")
        shot("07_tx_editor")
        save()
        waitText("überschritten", substring = true)
        rule.mainClock.advanceTimeBy(12_000) // let the snackbar disappear (it would cover the small test screen)

        // ---------- Cash expense
        rule.onNodeWithContentDescription("Neue Buchung").performClick()
        waitText("KATEGORIE")
        type("Betrag", "4,50")
        click("Bar")
        click("Freizeit")
        type("Wofür? (z. B. Rewe, Propeller)", "Propeller")
        shot("07b_cash_editor")
        save()

        // ---------- Withdrawal
        rule.onNodeWithContentDescription("Neue Buchung").performClick()
        waitText("KATEGORIE")
        click("Geld abheben")
        type("Betrag", "50")
        save()

        // 1500 - 23,99 - 50 = 1426,01 ; cash 80 - 4,50 + 50 = 125,50
        waitText("1.426,01", substring = true)
        waitText("125,50", substring = true)
        shot("08_overview_after_bookings")

        clickTab("Buchungen")
        waitText("Rewe")
        shot("09_transactions")

        clickTab("Bericht")
        waitText("Ausgaben nach Kategorie".uppercase())
        shot("10_report")

        // ---------- Balance check
        clickTab("Übersicht")
        rule.onNodeWithContentDescription("Menü").performClick()
        click("Bestand abgleichen")
        waitText("Echter Kontostand")
        type("Echter Kontostand", "1420", scroll = false)
        shot("11_reconcile")
        rule.onNodeWithText("Übernehmen").performClick()
        waitText("1.420,00", substring = true)
        shot("12_overview_final")
    }
}

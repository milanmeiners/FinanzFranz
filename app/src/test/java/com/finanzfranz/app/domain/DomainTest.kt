package com.finanzfranz.app.domain

import com.finanzfranz.app.data.Cat
import com.finanzfranz.app.data.FixedCost
import com.finanzfranz.app.data.Kind
import com.finanzfranz.app.data.Src
import com.finanzfranz.app.data.Tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DomainTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun moneyParse() {
        assertEquals(1250L, Money.parse("12,5"))
        assertEquals(1250L, Money.parse("12.50"))
        assertEquals(123456L, Money.parse("1.234,56"))
        assertEquals(-300L, Money.parse("-3"))
        assertEquals(150000L, Money.parse("1.500"))
        assertEquals(1230000L, Money.parse("12.300"))
        assertEquals(1999L, Money.parse("19,99 €"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
        assertEquals("12,05", Money.toInput(1205))
        assertEquals("-0,50", Money.toInput(-50))
        assertEquals("1.234,56 €", Money.format(123456))
    }

    @Test fun balancesAndWithdrawal() {
        val txs = listOf(
            Tx(epochDay = d("2026-12-01").toEpochDay(), amountCents = 100000, kind = Kind.ADJUST, source = Src.ACCOUNT),
            Tx(epochDay = d("2026-12-01").toEpochDay(), amountCents = 5000, kind = Kind.ADJUST, source = Src.CASH),
            Tx(epochDay = d("2026-12-02").toEpochDay(), amountCents = 2399, kind = Kind.EXPENSE, source = Src.ACCOUNT, categoryId = Cat.FOOD),
            Tx(epochDay = d("2026-12-03").toEpochDay(), amountCents = 450, kind = Kind.EXPENSE, source = Src.CASH, categoryId = Cat.FOOD),
            Tx(epochDay = d("2026-12-04").toEpochDay(), amountCents = 10000, kind = Kind.WITHDRAWAL),
            Tx(epochDay = d("2026-12-05").toEpochDay(), amountCents = 180000, kind = Kind.INCOME, source = Src.ACCOUNT),
            Tx(epochDay = d("2026-12-20").toEpochDay(), amountCents = 999, kind = Kind.EXPENSE, source = Src.ACCOUNT, categoryId = Cat.LEISURE),
        )
        val b = Ledger.balances(txs, d("2026-12-10"))
        assertEquals(100000 - 2399 - 10000 + 180000L, b.account)
        assertEquals(5000 - 450 + 10000L, b.cash)
        val all = Ledger.balances(txs, d("2026-12-31"))
        assertEquals(b.account - 999, all.account)

        val s = Ledger.monthStats(txs, YearMonth.of(2026, 12))
        assertEquals(180000L, s.income)
        assertEquals(2399 + 450 + 999L, s.expense)
        assertEquals(10000L, s.withdrawals)
        assertEquals(2849L, s.byCategory[Cat.FOOD])
    }

    @Test fun scheduleMonthlyAndYearly() {
        val rent = FixedCost(id = 1, name = "Miete", amountCents = 60000, firstDueEpochDay = d("2026-12-01").toEpochDay())
        val insurance = FixedCost(id = 2, name = "Haftpflicht", amountCents = 6000, firstDueEpochDay = d("2027-03-15").toEpochDay(), intervalMonths = 12)
        val end31 = FixedCost(id = 3, name = "X", amountCents = 100, firstDueEpochDay = d("2027-01-31").toEpochDay())

        assertEquals(listOf<LocalDate>(), Schedule.dueUntil(rent, d("2026-11-30")))
        assertEquals(listOf(d("2026-12-01"), d("2027-01-01"), d("2027-02-01")), Schedule.dueUntil(rent, d("2027-02-10")))
        assertEquals(d("2028-03-15"), Schedule.dueAt(insurance, 1))
        assertEquals(500L, Schedule.monthlyShare(insurance))
        // day 31 is clamped per month but returns to 31 when possible
        assertEquals(d("2027-02-28"), Schedule.dueAt(end31, 1))
        assertEquals(d("2027-03-31"), Schedule.dueAt(end31, 2))

        val booked = rent.copy(bookedCount = 1) // December booked
        assertEquals(d("2027-01-01"), Schedule.nextDue(booked))
    }

    @Test fun plannedRestOfMonth() {
        val rent = FixedCost(id = 1, name = "Miete", amountCents = 60000, firstDueEpochDay = d("2026-12-01").toEpochDay(), bookedCount = 1)
        val claude = FixedCost(id = 2, name = "Claude", amountCents = 2160, firstDueEpochDay = d("2026-12-18").toEpochDay())
        val salary = FixedCost(id = 3, name = "Gehalt", amountCents = 180000, firstDueEpochDay = d("2026-12-28").toEpochDay(), income = true)
        val paused = FixedCost(id = 4, name = "Pause", amountCents = 999, firstDueEpochDay = d("2026-12-20").toEpochDay(), active = false)
        val yearly = FixedCost(id = 5, name = "Versicherung", amountCents = 9000, firstDueEpochDay = d("2027-03-01").toEpochDay(), intervalMonths = 12)
        val p = Ledger.plannedRestOfMonth(listOf(rent, claude, salary, paused, yearly), d("2026-12-10"))
        assertEquals(listOf("Claude", "Gehalt"), p.map { it.fc.name })
        // a fixed cost due today is not "planned" any more (it is booked today)
        assertTrue(Ledger.plannedRestOfMonth(listOf(claude), d("2026-12-18")).isEmpty())
        val march = Ledger.plannedRestOfMonth(listOf(yearly), d("2027-02-27"))
        assertTrue(march.isEmpty())
        assertEquals(1, Ledger.plannedBetween(listOf(yearly), d("2026-12-10"), d("2027-06-10")).size)
    }

    @Test fun budgetWarnings() {
        assertNull(Ledger.budgetWarning("Essen", null, 0, 1000))
        assertTrue(Ledger.budgetWarning("Essen", 10000, 7000, 1500)!!.startsWith("80 %"))
        assertTrue(Ledger.budgetWarning("Essen", 10000, 9000, 1500)!!.startsWith("Budget"))
        assertNull(Ledger.budgetWarning("Essen", 10000, 11000, 1500)) // already over, don't nag again
        assertNull(Ledger.budgetWarning("Essen", 10000, 1000, 1000))
    }

    @Test fun reactivatedSkipsPausedDates() {
        // Netflix on the 5th, booked until January, paused, reactivated on 10 May
        val netflix = FixedCost(id = 1, name = "Netflix", amountCents = 1399, firstDueEpochDay = d("2027-01-05").toEpochDay(), bookedCount = 1)
        val k = Schedule.firstIndexNotBefore(netflix, d("2027-05-10"))
        assertEquals(d("2027-06-05"), Schedule.dueAt(netflix, k))
        assertTrue(Schedule.dueUntil(netflix.copy(bookedCount = k), d("2027-05-10")).isEmpty())
        // reactivated exactly on the due day: that day is still booked
        val k2 = Schedule.firstIndexNotBefore(netflix, d("2027-05-05"))
        assertEquals(listOf(d("2027-05-05")), Schedule.dueUntil(netflix.copy(bookedCount = k2), d("2027-05-05")))
    }
}

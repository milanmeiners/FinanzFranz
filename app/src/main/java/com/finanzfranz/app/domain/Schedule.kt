package com.finanzfranz.app.domain

import com.finanzfranz.app.data.FixedCost
import java.time.LocalDate
import java.time.YearMonth

object Schedule {
    val intervals = listOf(1 to "Monatlich", 3 to "Vierteljährlich", 6 to "Halbjährlich", 12 to "Jährlich")

    fun intervalLabel(months: Int) = intervals.firstOrNull { it.first == months }?.second ?: "Alle $months Monate"

    /** Due date of occurrence [k] (0-based). plusMonths keeps the original day where possible (31st -> 30th/28th). */
    fun dueAt(fc: FixedCost, k: Int): LocalDate =
        LocalDate.ofEpochDay(fc.firstDueEpochDay).plusMonths(k.toLong() * fc.intervalMonths)

    fun nextDue(fc: FixedCost): LocalDate = dueAt(fc, fc.bookedCount)

    /** Not-yet-booked occurrences with a due date <= [today]. */
    fun dueUntil(fc: FixedCost, today: LocalDate): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var k = fc.bookedCount
        while (out.size < 400) {
            val d = dueAt(fc, k)
            if (d.isAfter(today)) break
            out += d
            k++
        }
        return out
    }

    /** Not-yet-booked occurrences in the range [from, to]. */
    fun occurrences(fc: FixedCost, from: LocalDate, to: LocalDate): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var k = fc.bookedCount
        while (out.size < 400) {
            val d = dueAt(fc, k)
            if (d.isAfter(to)) break
            if (!d.isBefore(from)) out += d
            k++
        }
        return out
    }

    /** Index of the first occurrence (from [FixedCost.bookedCount] on) that is on or after [day]. */
    fun firstIndexNotBefore(fc: FixedCost, day: LocalDate): Int {
        var k = fc.bookedCount
        while (k < fc.bookedCount + 10_000 && dueAt(fc, k).isBefore(day)) k++
        return k
    }

    /** Amount per month (yearly 120 € -> 10 €). */
    fun monthlyShare(fc: FixedCost): Long = Math.round(fc.amountCents.toDouble() / fc.intervalMonths)

    fun monthIndex(d: LocalDate): Int = d.year * 12 + (d.monthValue - 1)
    fun monthIndex(ym: YearMonth): Int = ym.year * 12 + (ym.monthValue - 1)
}

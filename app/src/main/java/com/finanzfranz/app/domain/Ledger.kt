package com.finanzfranz.app.domain

import com.finanzfranz.app.data.Cat
import com.finanzfranz.app.data.FixedCost
import com.finanzfranz.app.data.Kind
import com.finanzfranz.app.data.Src
import com.finanzfranz.app.data.Tx
import java.time.LocalDate
import java.time.YearMonth

data class Balances(val account: Long, val cash: Long) {
    val total get() = account + cash
}

data class Planned(val fc: FixedCost, val date: LocalDate)

data class MonthStats(
    val income: Long,
    val expense: Long,
    val withdrawals: Long,
    val byCategory: Map<Long, Long>,
) {
    val net get() = income - expense
}

object Ledger {
    /** Effect of one booking as (account delta, cash delta). */
    fun effect(t: Tx): Pair<Long, Long> {
        val a = t.amountCents
        return when (t.kind) {
            Kind.EXPENSE -> if (t.source == Src.CASH) 0L to -a else -a to 0L
            Kind.INCOME -> if (t.source == Src.CASH) 0L to a else a to 0L
            Kind.WITHDRAWAL -> -a to a
            else -> if (t.source == Src.CASH) 0L to a else a to 0L
        }
    }

    /** Balances counting all bookings up to and including [upTo] (future-dated bookings are ignored). */
    fun balances(txs: List<Tx>, upTo: LocalDate): Balances {
        val limit = upTo.toEpochDay()
        var acc = 0L
        var cash = 0L
        for (t in txs) {
            if (t.epochDay > limit) continue
            val (da, dc) = effect(t)
            acc += da; cash += dc
        }
        return Balances(acc, cash)
    }

    /** Fixed costs / incomes still to come this month (after [today]). Sorted by date. */
    fun plannedRestOfMonth(fixed: List<FixedCost>, today: LocalDate): List<Planned> {
        val end = YearMonth.from(today).atEndOfMonth()
        return fixed.filter { it.active }
            .flatMap { fc -> Schedule.occurrences(fc, today.plusDays(1), end).map { Planned(fc, it) } }
            .sortedBy { it.date }
    }

    /** Planned items in [from, to] (inclusive). */
    fun plannedBetween(fixed: List<FixedCost>, from: LocalDate, to: LocalDate): List<Planned> =
        fixed.filter { it.active }
            .flatMap { fc -> Schedule.occurrences(fc, from, to).map { Planned(fc, it) } }
            .sortedBy { it.date }

    fun monthStats(txs: List<Tx>, ym: YearMonth): MonthStats {
        val from = ym.atDay(1).toEpochDay()
        val to = ym.atEndOfMonth().toEpochDay()
        var inc = 0L
        var exp = 0L
        var wd = 0L
        val by = HashMap<Long, Long>()
        for (t in txs) {
            if (t.epochDay < from || t.epochDay > to) continue
            when (t.kind) {
                Kind.INCOME -> inc += t.amountCents
                Kind.EXPENSE -> {
                    exp += t.amountCents
                    val c = t.categoryId ?: Cat.OTHER
                    by[c] = (by[c] ?: 0L) + t.amountCents
                }
                Kind.WITHDRAWAL -> wd += t.amountCents
            }
        }
        return MonthStats(inc, exp, wd, by)
    }

    /** Budget warning after adding [added] cents to a category that had [before] spent. */
    fun budgetWarning(categoryName: String, budget: Long?, before: Long, added: Long): String? {
        if (budget == null || budget <= 0) return null
        val after = before + added
        return when {
            after > budget && before <= budget ->
                "Budget „$categoryName“ überschritten: ${Money.format(after)} von ${Money.format(budget)}"
            after * 10 >= budget * 8 && before * 10 < budget * 8 ->
                "80 % vom Budget „$categoryName“ erreicht: ${Money.format(after)} von ${Money.format(budget)}"
            else -> null
        }
    }
}

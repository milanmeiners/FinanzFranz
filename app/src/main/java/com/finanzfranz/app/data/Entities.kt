package com.finanzfranz.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** What kind of booking a [Tx] is. */
object Kind {
    const val EXPENSE = 0     // money goes out (from account or cash)
    const val INCOME = 1      // money comes in (to account or cash)
    const val WITHDRAWAL = 2  // account -> cash (ATM)
    const val ADJUST = 3      // correction / starting balance, signed amount

    fun label(k: Int) = when (k) {
        EXPENSE -> "Ausgabe"
        INCOME -> "Einnahme"
        WITHDRAWAL -> "Abheben"
        else -> "Korrektur"
    }
}

/** Where the money is. */
object Src {
    const val ACCOUNT = 0
    const val CASH = 1

    fun label(s: Int) = if (s == CASH) "Bar" else "Konto"
}

/** Built-in category ids (seeded on first start). */
object Cat {
    const val FIXED = 1L
    const val FOOD = 2L
    const val LEISURE = 3L
    const val OTHER = 4L

    val defaults = listOf(
        Category(FIXED, "Laufende Kosten", sort = 0, system = true),
        Category(FOOD, "Essen & Trinken", sort = 1),
        Category(LEISURE, "Freizeit", sort = 2),
        Category(OTHER, "Sonstiges", sort = 99, system = true),
    )
}

/**
 * One booking. [amountCents] is always positive, except for [Kind.ADJUST] where the sign
 * says whether money was added (+) or removed (-).
 */
@Entity(tableName = "tx", indices = [Index("epochDay")])
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val amountCents: Long,
    val kind: Int,
    val source: Int = Src.ACCOUNT,
    val categoryId: Long? = null,
    val note: String = "",
    val fixedCostId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "category")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Monthly budget, null = no budget. */
    val budgetCents: Long? = null,
    val sort: Int = 50,
    /** System categories cannot be deleted. */
    val system: Boolean = false,
)

/**
 * A recurring cost (rent, subscriptions, insurance ...) or recurring income (salary). Occurrence k is due on
 * firstDue + k * intervalMonths months. [bookedCount] occurrences have already been booked.
 */
@Entity(tableName = "fixed_cost")
data class FixedCost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountCents: Long,
    val source: Int = Src.ACCOUNT,
    val firstDueEpochDay: Long,
    val intervalMonths: Int = 1,
    val bookedCount: Int = 0,
    val active: Boolean = true,
    /** true = recurring income (salary ...), false = recurring cost. */
    val income: Boolean = false,
)

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    val setupDone: Boolean = false,
    /** year * 12 + (month - 1) of the last balance check. */
    val lastReconciledMonth: Int = 0,
)

data class AppData(
    val txs: List<Tx> = emptyList(),
    val categories: List<Category> = emptyList(),
    val fixed: List<FixedCost> = emptyList(),
    val settings: Settings? = null,
    val loaded: Boolean = false,
)

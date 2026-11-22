package com.finanzfranz.app.data

import androidx.room.withTransaction
import com.finanzfranz.app.domain.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

class Repository(private val db: AppDatabase) {
    val dao = db.dao()

    val data: Flow<AppData> = combine(dao.txs(), dao.categories(), dao.fixed(), dao.settings()) { t, c, f, s ->
        AppData(t, c, f, s, loaded = true)
    }

    suspend fun ensureSeeded() = db.withTransaction {
        if (dao.categoriesNow().isEmpty()) dao.insertCategories(Cat.defaults)
        if (dao.settingsNow() == null) dao.upsertSettings(Settings())
    }

    /** Books every fixed-cost occurrence that is due up to [today]. Returns the number of bookings. */
    suspend fun bookDueFixedCosts(today: LocalDate): Int = db.withTransaction {
        var n = 0
        for (fc in dao.fixedNow()) {
            if (!fc.active) continue
            val dates = Schedule.dueUntil(fc, today)
            if (dates.isEmpty()) continue
            dao.insertTxs(dates.map {
                Tx(epochDay = it.toEpochDay(), amountCents = fc.amountCents,
                    kind = if (fc.income) Kind.INCOME else Kind.EXPENSE, source = fc.source,
                    categoryId = if (fc.income) null else Cat.FIXED, note = fc.name, fixedCostId = fc.id)
            })
            dao.upsertFixed(fc.copy(bookedCount = fc.bookedCount + dates.size))
            n += dates.size
        }
        n
    }

    suspend fun saveTx(t: Tx) = dao.upsertTx(t)
    suspend fun addTxs(t: List<Tx>) = dao.insertTxs(t)
    suspend fun deleteTx(t: Tx) = dao.deleteTx(t)

    suspend fun saveCategory(c: Category) = dao.upsertCategory(c)
    suspend fun deleteCategory(c: Category) = db.withTransaction {
        dao.moveCategory(c.id, Cat.OTHER)
        dao.deleteCategory(c)
    }

    suspend fun saveFixed(f: FixedCost) = dao.upsertFixed(f)
    suspend fun deleteFixed(f: FixedCost) = db.withTransaction {
        dao.detachFixed(f.id)
        dao.deleteFixed(f)
    }

    suspend fun saveSettings(s: Settings) = dao.upsertSettings(s)

    suspend fun snapshot(): AppData = db.withTransaction {
        AppData(dao.txsNow(), dao.categoriesNow(), dao.fixedNow(), dao.settingsNow(), true)
    }

    suspend fun replaceAll(d: AppData) = db.withTransaction {
        dao.clearTx(); dao.clearCategories(); dao.clearFixed(); dao.clearSettings()
        dao.insertCategories(d.categories.ifEmpty { Cat.defaults })
        dao.insertFixed(d.fixed)
        dao.insertTxs(d.txs)
        dao.upsertSettings(d.settings ?: Settings(setupDone = true))
    }
}

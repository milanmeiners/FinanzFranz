package com.finanzfranz.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finanzfranz.app.data.AppData
import com.finanzfranz.app.data.AppDatabase
import com.finanzfranz.app.data.Category
import com.finanzfranz.app.data.FixedCost
import com.finanzfranz.app.data.Kind
import com.finanzfranz.app.data.Repository
import com.finanzfranz.app.data.Settings
import com.finanzfranz.app.data.Src
import com.finanzfranz.app.data.Tx
import com.finanzfranz.app.domain.Ledger
import com.finanzfranz.app.domain.Money
import com.finanzfranz.app.domain.ReceiptResult
import com.finanzfranz.app.domain.Schedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Editable state of a booking. */
data class TxDraft(
    val id: Long = 0,
    val kind: Int = Kind.EXPENSE,
    val source: Int = Src.ACCOUNT,
    val amount: String = "",
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val fixedCostId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isNew get() = id == 0L

    companion object {
        fun from(t: Tx) = TxDraft(t.id, t.kind, t.source, Money.toInput(t.amountCents), t.categoryId, LocalDate.ofEpochDay(t.epochDay), t.note, t.fixedCostId, t.createdAt)
    }
}

/** Editable state of a fixed cost / fixed income. */
data class FixedDraft(
    val id: Long = 0,
    val name: String = "",
    val amount: String = "",
    val source: Int = Src.ACCOUNT,
    val nextDue: LocalDate = LocalDate.now().plusDays(1),
    val originalNextDue: LocalDate? = null,
    val intervalMonths: Int = 1,
    val active: Boolean = true,
    val income: Boolean = false,
    val original: FixedCost? = null,
) {
    val isNew get() = id == 0L

    companion object {
        fun from(f: FixedCost): FixedDraft {
            val next = Schedule.nextDue(f)
            return FixedDraft(f.id, f.name, Money.toInput(f.amountCents), f.source, next, next, f.intervalMonths, f.active, f.income, f)
        }
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(AppDatabase.get(app))
    val data: StateFlow<AppData> = repo.data.stateIn(viewModelScope, SharingStarted.Eagerly, AppData())

    var today by mutableStateOf(LocalDate.now())
    var tab by mutableIntStateOf(0)
    var message by mutableStateOf<String?>(null)

    var txDraft by mutableStateOf<TxDraft?>(null)
    var fixedDraft by mutableStateOf<FixedDraft?>(null)
    var showCategories by mutableStateOf(false)
    var reconcileOpen by mutableStateOf(false)
    var reconcileMonthly by mutableStateOf(false)
    var scanning by mutableStateOf(false)
    var month by mutableStateOf(YearMonth.now())

    init {
        refreshToday()
    }

    /** Called on start and whenever the app comes back to the foreground. */
    fun refreshToday() {
        val now = LocalDate.now()
        if (YearMonth.from(now) != YearMonth.from(today)) month = YearMonth.from(now)
        today = now
        viewModelScope.launch {
            repo.ensureSeeded()
            val n = repo.bookDueFixedCosts(now)
            if (n > 0) message = if (n == 1) "1 feste Buchung wurde automatisch gebucht" else "$n feste Buchungen wurden automatisch gebucht"
            val s = repo.dao.settingsNow()
            if (s != null && s.setupDone && Schedule.monthIndex(now) > s.lastReconciledMonth) {
                reconcileMonthly = true
                reconcileOpen = true
            }
        }
    }

    // ------------------------------------------------------------ Setup & balance check

    fun completeSetup(account: Long, cash: Long) = viewModelScope.launch {
        val day = today.toEpochDay()
        val start = listOfNotNull(
            Tx(epochDay = day, amountCents = account, kind = Kind.ADJUST, source = Src.ACCOUNT, note = "Startbestand Konto"),
            if (cash != 0L) Tx(epochDay = day, amountCents = cash, kind = Kind.ADJUST, source = Src.CASH, note = "Startbestand Bargeld") else null,
        )
        repo.addTxs(start)
        repo.saveSettings(Settings(setupDone = true, lastReconciledMonth = Schedule.monthIndex(today)))
    }

    fun openReconcile() {
        reconcileMonthly = false
        reconcileOpen = true
    }

    /** Books the difference between what the app thinks and the real balances. */
    fun reconcile(realAccount: Long?, realCash: Long?) = viewModelScope.launch {
        val b = Ledger.balances(data.value.txs, today)
        val day = today.toEpochDay()
        val corrections = buildList {
            if (realAccount != null && realAccount != b.account) add(Tx(epochDay = day, amountCents = realAccount - b.account, kind = Kind.ADJUST, source = Src.ACCOUNT, note = "Abgleich Konto"))
            if (realCash != null && realCash != b.cash) add(Tx(epochDay = day, amountCents = realCash - b.cash, kind = Kind.ADJUST, source = Src.CASH, note = "Abgleich Bargeld"))
        }
        if (corrections.isNotEmpty()) repo.addTxs(corrections)
        markReconciled()
        reconcileOpen = false
        message = if (corrections.isEmpty()) "Passt alles – keine Korrektur nötig" else "Bestand korrigiert"
    }

    fun skipReconcile() = viewModelScope.launch {
        if (reconcileMonthly) markReconciled()
        reconcileOpen = false
    }

    private suspend fun markReconciled() {
        val s = repo.dao.settingsNow() ?: Settings(setupDone = true)
        repo.saveSettings(s.copy(lastReconciledMonth = Schedule.monthIndex(today)))
    }

    // ------------------------------------------------------------ Bookings

    fun newTx(kind: Int = Kind.EXPENSE) {
        txDraft = TxDraft(kind = kind, date = today, categoryId = if (kind == Kind.EXPENSE) null else null)
    }

    fun editTx(t: Tx) { txDraft = TxDraft.from(t) }

    fun saveTx(d: TxDraft): Boolean {
        val cents = Money.parse(d.amount)
        if (cents == null || cents == 0L || (cents < 0 && d.kind != Kind.ADJUST)) {
            message = "Bitte einen gültigen Betrag eingeben"
            return false
        }
        if (d.kind == Kind.EXPENSE && d.categoryId == null) {
            message = "Bitte eine Kategorie wählen"
            return false
        }
        val tx = Tx(
            id = d.id, epochDay = d.date.toEpochDay(), amountCents = cents, kind = d.kind,
            source = if (d.kind == Kind.WITHDRAWAL) Src.ACCOUNT else d.source,
            categoryId = if (d.kind == Kind.EXPENSE) d.categoryId else null,
            note = d.note.trim(), fixedCostId = d.fixedCostId, createdAt = d.createdAt,
        )
        // Budget warning (only for the month of the booking)
        val warning = if (tx.kind == Kind.EXPENSE) {
            val cat = data.value.categories.firstOrNull { it.id == tx.categoryId }
            val ym = YearMonth.from(d.date)
            val others = data.value.txs.filter { it.id != tx.id }
            val before = Ledger.monthStats(others, ym).byCategory[tx.categoryId] ?: 0L
            cat?.let { Ledger.budgetWarning(it.name, it.budgetCents, before, cents) }
        } else null
        viewModelScope.launch { repo.saveTx(tx) }
        txDraft = null
        if (warning != null) message = warning
        return true
    }

    fun deleteTx(id: Long) {
        val t = data.value.txs.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { repo.deleteTx(t) }
        txDraft = null
        message = "Buchung gelöscht"
    }

    /** Fills the open draft with what was read from a receipt. */
    fun applyReceipt(r: ReceiptResult) {
        val d = txDraft ?: TxDraft(date = today)
        val learnedCat = r.store?.let { store ->
            data.value.txs.firstOrNull { it.kind == Kind.EXPENSE && it.categoryId != null && it.note.equals(store, ignoreCase = true) }?.categoryId
        }
        val hintCat = r.categoryHint?.let { h -> data.value.categories.firstOrNull { it.name.equals(h, ignoreCase = true) }?.id }
        txDraft = d.copy(
            kind = Kind.EXPENSE,
            amount = r.totalCents?.let { Money.toInput(it) } ?: d.amount,
            date = r.date ?: d.date,
            note = r.store ?: d.note,
            source = r.source ?: d.source,
            categoryId = learnedCat ?: hintCat ?: d.categoryId,
        )
        message = when {
            r.totalCents == null -> "Keine Summe erkannt – bitte Betrag eintragen"
            else -> "Bon gelesen – bitte kurz prüfen"
        }
    }

    // ------------------------------------------------------------ Fixed costs

    fun newFixed(income: Boolean) {
        fixedDraft = FixedDraft(income = income, nextDue = today.plusDays(1), name = if (income) "Gehalt" else "")
    }

    fun editFixed(f: FixedCost) { fixedDraft = FixedDraft.from(f) }

    fun saveFixed(d: FixedDraft): Boolean {
        val cents = Money.parse(d.amount)
        if (d.name.isBlank()) { message = "Bitte einen Namen eingeben"; return false }
        if (cents == null || cents <= 0) { message = "Bitte einen gültigen Betrag eingeben"; return false }
        val base = d.original
        val dateChanged = base == null || d.nextDue != d.originalNextDue || d.intervalMonths != base.intervalMonths
        val f = FixedCost(
            id = d.id, name = d.name.trim(), amountCents = cents, source = d.source,
            firstDueEpochDay = if (dateChanged) d.nextDue.toEpochDay() else base!!.firstDueEpochDay,
            intervalMonths = d.intervalMonths,
            bookedCount = if (dateChanged) 0 else base!!.bookedCount,
            active = d.active, income = d.income,
        )
        viewModelScope.launch {
            repo.saveFixed(f)
            // If the first date is today or in the past, book it right away.
            val n = repo.bookDueFixedCosts(today)
            if (n > 0) message = if (n == 1) "Fällig ab heute – direkt gebucht" else "$n Termine bis heute direkt gebucht"
        }
        fixedDraft = null
        return true
    }

    fun deleteFixed(id: Long) {
        val f = data.value.fixed.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { repo.deleteFixed(f) }
        fixedDraft = null
        message = "„${f.name}“ gelöscht (bisherige Buchungen bleiben)"
    }

    // ------------------------------------------------------------ Categories

    fun saveCategory(c: Category) = viewModelScope.launch { repo.saveCategory(c) }

    fun deleteCategory(c: Category) = viewModelScope.launch {
        repo.deleteCategory(c)
        message = "„${c.name}“ gelöscht – Buchungen liegen jetzt unter Sonstiges"
    }
}

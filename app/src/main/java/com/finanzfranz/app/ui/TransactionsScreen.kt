package com.finanzfranz.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finanzfranz.app.data.AppData
import com.finanzfranz.app.data.Kind
import com.finanzfranz.app.data.Src
import com.finanzfranz.app.data.Tx
import com.finanzfranz.app.domain.Ledger
import com.finanzfranz.app.domain.Money
import java.time.LocalDate

@Composable
fun TransactionsScreen(vm: MainViewModel, data: AppData) {
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 all, 1 account, 2 cash
    val ym = vm.month
    val cats = remember(data.categories) { data.categories.associateBy { it.id } }
    val from = ym.atDay(1).toEpochDay()
    val to = ym.atEndOfMonth().toEpochDay()
    val list = remember(data.txs, ym, filter) {
        data.txs.filter { it.epochDay in from..to }.filter {
            when (filter) {
                1 -> it.kind == Kind.WITHDRAWAL || it.source == Src.ACCOUNT
                2 -> it.kind == Kind.WITHDRAWAL || it.source == Src.CASH
                else -> true
            }
        }
    }
    val groups: List<Pair<LocalDate, List<Tx>>> = remember(list) {
        list.groupBy { it.epochDay }.toSortedMap(compareByDescending { it }).map { (d, l) -> LocalDate.ofEpochDay(d) to l }
    }
    val stats = remember(data.txs, ym) { Ledger.monthStats(data.txs, ym) }

    LazyColumn(Modifier.fillMaxSize()) {
        item { MonthSwitcher(ym, { vm.month = it }) }
        item {
            ChipGroup(listOf(0 to "Alle", 1 to "Konto", 2 to "Bar"), filter, { filter = it }, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Einnahmen ${Money.format(stats.income)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("Ausgaben ${Money.format(stats.expense)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (groups.isEmpty()) item { EmptyHint("Keine Buchungen in diesem Monat.") }
        for ((day, txs) in groups) {
            item(key = "h$day") { SectionHeader(Fmt.date(day, vm.today)) }
            items(txs, key = { it.id }) { t -> TxRow(t, cats, vm.today, showDate = false) { vm.editTx(t) } }
        }
        item { Gap(140.dp) }
    }
}

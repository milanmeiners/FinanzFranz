package com.finanzfranz.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finanzfranz.app.data.AppData
import com.finanzfranz.app.data.FixedCost
import com.finanzfranz.app.data.Src
import com.finanzfranz.app.domain.Ledger
import com.finanzfranz.app.domain.Money
import com.finanzfranz.app.domain.Schedule

@Composable
fun FixedScreen(vm: MainViewModel, data: AppData) {
    val today = vm.today
    val costs = data.fixed.filter { !it.income }
    val incomes = data.fixed.filter { it.income }
    val monthlyCosts = costs.filter { it.active }.sumOf { Schedule.monthlyShare(it) }
    val monthlyIncome = incomes.filter { it.active }.sumOf { Schedule.monthlyShare(it) }
    val bigUpcoming = remember(data.fixed, today) {
        Ledger.plannedBetween(data.fixed, today.plusDays(1), today.plusMonths(6))
            .filter { !it.fc.income && it.fc.intervalMonths > 1 }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        Card {
            Text("FESTE KOSTEN PRO MONAT", style = SectionLabel)
            Gap(4.dp)
            Text(Money.format(monthlyCosts), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text("Jährliche & vierteljährliche Kosten anteilig umgerechnet", style = MaterialTheme.typography.bodySmall)
            if (monthlyIncome > 0) {
                Gap(10.dp)
                AmountRow("Feste Einnahmen pro Monat", Money.format(monthlyIncome))
                AmountRow("Bleibt nach Fixkosten", Money.signed(monthlyIncome - monthlyCosts), strong = true)
            }
        }

        SectionHeader("Feste Einnahmen", action = {
            TextButton(onClick = { vm.newFixed(income = true) }) { Text("+ Hinzufügen", color = Mono.Black) }
        })
        if (incomes.isEmpty()) EmptyHint("Trag hier z. B. dein Gehalt ein. Es wird dann automatisch gebucht.")
        for (f in incomes) FixedRow(f, vm)

        SectionHeader("Feste Ausgaben", action = {
            TextButton(onClick = { vm.newFixed(income = false) }) { Text("+ Hinzufügen", color = Mono.Black) }
        })
        if (costs.isEmpty()) EmptyHint("Noch keine festen Ausgaben. Tippe auf + für Miete, Abos, Versicherungen …")
        for (f in costs.sortedWith(compareBy({ !it.active }, { Schedule.nextDue(it) }))) FixedRow(f, vm)

        if (bigUpcoming.isNotEmpty()) {
            SectionHeader("Größere Abbuchungen in den nächsten 6 Monaten")
            for (p in bigUpcoming) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(p.date.format(Fmt.dayMonthYear), color = Mono.G600, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
                    Text(p.fc.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("−" + Money.format(p.fc.amountCents), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun FixedRow(f: FixedCost, vm: MainViewModel) {
    val next = Schedule.nextDue(f)
    val sub = buildList {
        add(Schedule.intervalLabel(f.intervalMonths))
        add(if (f.active) "nächste: " + Fmt.date(next, vm.today) else "pausiert")
        if (f.source == Src.CASH) add("bar")
    }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable { vm.editFixed(f) }.padding(horizontal = 20.dp, vertical = 10.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.name, color = if (f.active) Mono.Ink else Mono.G400, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text((if (f.income) "+" else "") + Money.format(f.amountCents), color = if (f.active) Mono.Ink else Mono.G400)
            if (f.intervalMonths > 1) Text(Money.format(Schedule.monthlyShare(f)) + " / Monat", style = MaterialTheme.typography.bodySmall)
        }
    }
}

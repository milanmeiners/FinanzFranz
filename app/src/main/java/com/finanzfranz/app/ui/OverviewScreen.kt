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
import com.finanzfranz.app.data.Category
import com.finanzfranz.app.data.Kind
import com.finanzfranz.app.data.Src
import com.finanzfranz.app.data.Tx
import com.finanzfranz.app.domain.Ledger
import com.finanzfranz.app.domain.Money
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun OverviewScreen(vm: MainViewModel, data: AppData) {
    val today = vm.today
    val bal = remember(data.txs, today) { Ledger.balances(data.txs, today) }
    val planned = remember(data.fixed, today) { Ledger.plannedRestOfMonth(data.fixed, today) }
    val plannedOut = planned.filter { !it.fc.income && it.fc.source == Src.ACCOUNT }.sumOf { it.fc.amountCents }
    val plannedOutCash = planned.filter { !it.fc.income && it.fc.source == Src.CASH }.sumOf { it.fc.amountCents }
    val expectedIn = planned.filter { it.fc.income && it.fc.source == Src.ACCOUNT }.sumOf { it.fc.amountCents }
    val stats = remember(data.txs, today) { Ledger.monthStats(data.txs, YearMonth.from(today)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 140.dp)) {
        Text(today.format(Fmt.dayLong), style = MaterialTheme.typography.bodyMedium, color = Mono.G600, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))

        Card {
            Text("KONTO", style = SectionLabel)
            Gap(4.dp)
            Text(Money.format(bal.account), fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
            Gap(10.dp)
            AmountRow("Noch verplant bis Monatsende", if (plannedOut > 0) "−" + Money.format(plannedOut) else Money.format(0), muted = true)
            if (expectedIn > 0) AmountRow("Noch erwartet", "+" + Money.format(expectedIn), muted = true)
            ThinDivider(Modifier.padding(vertical = 6.dp))
            AmountRow("Frei verfügbar", Money.format(bal.account - plannedOut), strong = true)
        }
        Gap(12.dp)
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BARGELD", style = SectionLabel)
                    Gap(4.dp)
                    Text(Money.format(bal.cash), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("GESAMT", style = SectionLabel)
                    Gap(4.dp)
                    Text(Money.format(bal.total), fontSize = 18.sp, color = Mono.G600)
                }
            }
            if (plannedOutCash > 0) {
                Gap(6.dp)
                AmountRow("Noch verplant (bar)", "−" + Money.format(plannedOutCash), muted = true)
            }
        }

        SectionHeader("Dieser Monat")
        Column(Modifier.padding(horizontal = 20.dp)) {
            AmountRow("Einnahmen", Money.format(stats.income))
            AmountRow("Ausgaben", Money.format(stats.expense))
        }

        val budgeted = data.categories.filter { (it.budgetCents ?: 0) > 0 }
        if (budgeted.isNotEmpty()) {
            SectionHeader("Budgets")
            for (c in budgeted) BudgetRow(c, stats.byCategory[c.id] ?: 0L)
        }

        SectionHeader("Demnächst fällig")
        if (planned.isEmpty()) EmptyHint("Diesen Monat steht nichts Festes mehr an.")
        for (p in planned.take(8)) {
            Row(
                Modifier.fillMaxWidth().clickable { vm.editFixed(p.fc) }.padding(horizontal = 20.dp).heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Fmt.date(p.date, today), color = Mono.G600, modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium)
                Text(p.fc.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((if (p.fc.income) "+" else "−") + Money.format(p.fc.amountCents), style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionHeader("Letzte Buchungen", action = {
            if (data.txs.isNotEmpty()) TextButton(onClick = { vm.tab = 1 }) { Text("Alle", color = Mono.Black) }
        })
        val recent = data.txs.filter { it.epochDay <= today.toEpochDay() }.take(5)
        if (recent.isEmpty()) EmptyHint("Noch keine Buchungen. Tippe auf + oder scanne einen Kassenbon.")
        val cats = remember(data.categories) { data.categories.associateBy { it.id } }
        for (t in recent) TxRow(t, cats, today) { vm.editTx(t) }
    }
}

@Composable
fun BudgetRow(c: Category, spent: Long) {
    val budget = c.budgetCents ?: return
    val over = spent > budget
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row {
            Text(c.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                "${Money.format(spent)} / ${Money.format(budget)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (over) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Gap(5.dp)
        ProgressLine(spent.toFloat() / budget, over = over)
        if (over) Text("Überschritten um ${Money.format(spent - budget)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Mono.Black, modifier = Modifier.padding(top = 3.dp))
    }
}

/** One booking in a list. */
@Composable
fun TxRow(t: Tx, cats: Map<Long, Category>, today: LocalDate, showDate: Boolean = true, onClick: () -> Unit) {
    val catName = t.categoryId?.let { cats[it]?.name }
    val title = t.note.ifBlank {
        when (t.kind) {
            Kind.EXPENSE -> catName ?: "Ausgabe"
            Kind.WITHDRAWAL -> "Geld abgehoben"
            else -> Kind.label(t.kind)
        }
    }
    val sub = buildList {
        if (showDate) add(Fmt.date(LocalDate.ofEpochDay(t.epochDay), today))
        when (t.kind) {
            Kind.EXPENSE -> { if (catName != null && t.note.isNotBlank()) add(catName); add(Src.label(t.source)) }
            Kind.INCOME -> { add("Einnahme"); add(Src.label(t.source)) }
            Kind.WITHDRAWAL -> add("Konto → Bar")
            else -> { add("Korrektur"); add(Src.label(t.source)) }
        }
        if (t.fixedCostId != null) add("fest")
    }.joinToString(" · ")
    val amount = when (t.kind) {
        Kind.EXPENSE -> "−" + Money.format(t.amountCents)
        Kind.INCOME -> "+" + Money.format(t.amountCents)
        Kind.WITHDRAWAL -> Money.format(t.amountCents)
        else -> Money.signed(t.amountCents)
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            amount,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (t.kind == Kind.INCOME) FontWeight.SemiBold else FontWeight.Normal,
            color = if (t.kind == Kind.WITHDRAWAL || t.kind == Kind.ADJUST) Mono.G600 else Mono.Ink,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

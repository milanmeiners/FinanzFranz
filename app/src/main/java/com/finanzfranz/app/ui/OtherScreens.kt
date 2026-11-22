package com.finanzfranz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finanzfranz.app.data.AppData
import com.finanzfranz.app.data.Category
import com.finanzfranz.app.domain.Ledger
import com.finanzfranz.app.domain.Money

// ---------------------------------------------------------------- Report

@Composable
fun ReportScreen(vm: MainViewModel, data: AppData) {
    val ym = vm.month
    val stats = remember(data.txs, ym) { Ledger.monthStats(data.txs, ym) }
    val prev = remember(data.txs, ym) { Ledger.monthStats(data.txs, ym.minusMonths(1)) }
    val rows = data.categories
        .map { it to (stats.byCategory[it.id] ?: 0L) }
        .filter { it.second > 0 || (it.first.budgetCents ?: 0) > 0 }
        .sortedByDescending { it.second }
    val max = (rows.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        MonthSwitcher(ym, { vm.month = it })
        Card {
            AmountRow("Einnahmen", Money.format(stats.income))
            AmountRow("Ausgaben", Money.format(stats.expense))
            ThinDivider(Modifier.padding(vertical = 6.dp))
            AmountRow("Saldo", Money.signed(stats.net), strong = true)
            if (stats.withdrawals > 0) AmountRow("Bargeld abgehoben", Money.format(stats.withdrawals), muted = true)
            if (prev.expense > 0) {
                val diff = stats.expense - prev.expense
                AmountRow("Ausgaben ggü. Vormonat", Money.signed(diff), muted = true)
            }
        }
        SectionHeader("Ausgaben nach Kategorie")
        if (rows.isEmpty()) EmptyHint("Keine Ausgaben in diesem Monat.")
        for ((c, spent) in rows) {
            val budget = c.budgetCents?.takeIf { it > 0 }
            val share = if (stats.expense > 0) Math.round(spent * 100.0 / stats.expense) else 0
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(c.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(Money.format(spent), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Gap(6.dp)
                if (budget != null) {
                    ProgressLine(spent.toFloat() / budget, height = 8.dp, over = spent > budget)
                    Gap(3.dp)
                    Text(
                        if (spent > budget) "Budget ${Money.format(budget)} · überschritten um ${Money.format(spent - budget)}"
                        else "Budget ${Money.format(budget)} · noch ${Money.format(budget - spent)} übrig",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (spent > budget) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (spent > budget) Mono.Black else Mono.G600,
                    )
                } else {
                    ProgressLine(spent.toFloat() / max, height = 8.dp)
                    Gap(3.dp)
                    Text("$share % der Ausgaben", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Categories & budgets

@Composable
fun CategoriesScreen(vm: MainViewModel, data: AppData) {
    var editing by remember { mutableStateOf<Category?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.showCategories = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Text("Kategorien & Budgets", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { editing = Category(name = "") }) { Text("+ Neu", color = Mono.Black) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EmptyHint("Tippe eine Kategorie an, um ein Monatsbudget festzulegen. Du bekommst einen Hinweis bei 80 % und wenn du drüber bist.")
            for (c in data.categories) {
                Row(
                    Modifier.fillMaxWidth().clickable { editing = c }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(c.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(c.budgetCents?.let { "Budget " + Money.format(it) } ?: "kein Budget", color = if (c.budgetCents == null) Mono.G400 else Mono.Ink)
                }
                ThinDivider(start = 20.dp)
            }
        }
    }
    editing?.let { c -> CategoryDialog(c, vm, onDismiss = { editing = null }) }
}

@Composable
private fun CategoryDialog(c: Category, vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(c.name) }
    var budget by remember { mutableStateOf(c.budgetCents?.let { Money.toInput(it) } ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Mono.White,
        title = { Text(if (c.id == 0L) "Neue Kategorie" else c.name) },
        text = {
            Column {
                MonoTextField(name, { name = it }, "Name")
                Gap(8.dp)
                MonoTextField(budget, { budget = it }, "Monatsbudget (optional)", number = true, suffix = "€", placeholder = "leer = kein Budget")
                if (c.id != 0L && !c.system) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Kategorie löschen", color = Mono.Black) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val b = Money.parse(budget)?.takeIf { it > 0 }
                vm.saveCategory(c.copy(name = name.trim(), budgetCents = b))
                onDismiss()
            }) { Text("Speichern", color = Mono.Black) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = Mono.G600) } },
    )
    if (confirmDelete) ConfirmDialog("„${c.name}“ löschen?", "Bisherige Buchungen landen unter Sonstiges.", "Löschen", { vm.deleteCategory(c); onDismiss() }, { confirmDelete = false })
}

// ---------------------------------------------------------------- Setup

@Composable
fun SetupScreen(onDone: (Long, Long) -> Unit) {
    var account by remember { mutableStateOf("") }
    var cash by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Gap(32.dp)
        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Mono.Black).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("€", color = Mono.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Gap(20.dp)
        Text("FinanzFranz", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp))
        Gap(8.dp)
        Text("Behalte im Blick, was auf deinem Konto und im Portemonnaie ist.", style = MaterialTheme.typography.bodyLarge, color = Mono.G600)
        Gap(32.dp)
        Text("Wie viel Geld hast du gerade?", style = MaterialTheme.typography.titleMedium)
        Gap(12.dp)
        MonoTextField(account, { account = it; error = null }, "Kontostand", number = true, suffix = "€", placeholder = "0,00",
            supportingText = "So wie er gerade in deiner Banking-App steht")
        Gap(8.dp)
        MonoTextField(cash, { cash = it; error = null }, "Bargeld", number = true, suffix = "€", placeholder = "0,00")
        error?.let { Text(it, color = Mono.Black, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
        Gap(24.dp)
        Button(
            onClick = {
                val a = if (account.isBlank()) 0L else Money.parse(account)
                val c = if (cash.isBlank()) 0L else Money.parse(cash)
                if (a == null || c == null) error = "Bitte gültige Beträge eingeben" else onDone(a, c)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Mono.Black, contentColor = Mono.White),
        ) { Text("Los geht's") }
        Gap(16.dp)
        Text(
            "Danach kannst du unter „Fixkosten“ Miete, Abos, Versicherungen und dein Gehalt anlegen. Am Monatsanfang fragt dich FinanzFranz kurz nach deinem echten Kontostand, damit alles stimmt.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ---------------------------------------------------------------- Balance check

@Composable
fun ReconcileDialog(vm: MainViewModel, data: AppData) {
    val bal = remember(data.txs, vm.today) { Ledger.balances(data.txs, vm.today) }
    var account by remember { mutableStateOf(Money.toInput(bal.account)) }
    var cash by remember { mutableStateOf(Money.toInput(bal.cash)) }
    AlertDialog(
        onDismissRequest = { },
        containerColor = Mono.White,
        title = { Text(if (vm.reconcileMonthly) "Neuer Monat – kurzer Abgleich" else "Bestand abgleichen") },
        text = {
            Column {
                Text("Stimmen die Beträge? Trag sonst ein, was wirklich auf dem Konto und im Portemonnaie ist. Die Differenz wird als Korrektur gebucht.", style = MaterialTheme.typography.bodyMedium)
                Gap(12.dp)
                MonoTextField(account, { account = it }, "Echter Kontostand", number = true, suffix = "€")
                Gap(8.dp)
                MonoTextField(cash, { cash = it }, "Echtes Bargeld", number = true, suffix = "€")
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.reconcile(Money.parse(account), Money.parse(cash)) }) { Text("Übernehmen", color = Mono.Black, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = { vm.skipReconcile() }) { Text(if (vm.reconcileMonthly) "Überspringen" else "Abbrechen", color = Mono.G600) }
        },
    )
}

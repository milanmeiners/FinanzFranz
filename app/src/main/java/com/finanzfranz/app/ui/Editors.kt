package com.finanzfranz.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finanzfranz.app.data.AppData
import com.finanzfranz.app.data.Kind
import com.finanzfranz.app.data.Src
import com.finanzfranz.app.domain.Money
import com.finanzfranz.app.domain.Schedule

@Composable
fun EditorScaffold(title: String, onClose: () -> Unit, onSave: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Schließen") }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onSave) { Text("Speichern", color = Mono.Black, fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) { content() }
    }
}

private val bigAmount @Composable get() = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun TxEditor(vm: MainViewModel, data: AppData, actions: Actions) {
    val d = vm.txDraft ?: return
    fun set(n: TxDraft) { vm.txDraft = n }
    var confirmDelete by remember { mutableStateOf(false) }
    var scanChoice by remember { mutableStateOf(false) }
    val title = when {
        d.kind == Kind.ADJUST -> "Korrektur"
        d.isNew -> "Neue Buchung"
        else -> "Buchung bearbeiten"
    }

    EditorScaffold(title, onClose = { vm.txDraft = null }, onSave = { vm.saveTx(d) }) {
        if (d.kind != Kind.ADJUST) {
            ChipGroup(
                listOf(Kind.EXPENSE to "Ausgabe", Kind.INCOME to "Einnahme", Kind.WITHDRAWAL to "Geld abheben"),
                d.kind, { set(d.copy(kind = it)) }, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            MonoTextField(
                d.amount, { set(d.copy(amount = it)) }, "Betrag", number = true, placeholder = "0,00",
                textStyle = bigAmount, suffix = "€",
                supportingText = if (d.kind == Kind.ADJUST) "Minus = Geld weg, Plus = Geld dazu" else null,
            )
        }
        if (d.isNew && d.kind == Kind.EXPENSE) {
            OutlinedButton(
                onClick = { scanChoice = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Mono.G300),
            ) {
                Icon(Icons.Outlined.DocumentScanner, null, tint = Mono.Black, modifier = Modifier.size(18.dp))
                HGap(8.dp)
                Text("Kassenbon scannen", color = Mono.Black)
            }
        }
        when (d.kind) {
            Kind.WITHDRAWAL -> EmptyHint("Das Geld geht vom Konto runter und kommt zum Bargeld dazu.")
            else -> {
                SectionHeader(if (d.kind == Kind.EXPENSE) "Bezahlt mit" else if (d.kind == Kind.INCOME) "Eingang auf" else "Betrifft")
                ChipGroup(listOf(Src.ACCOUNT to "Konto", Src.CASH to "Bar"), d.source, { set(d.copy(source = it)) }, Modifier.padding(horizontal = 20.dp))
            }
        }
        if (d.kind == Kind.EXPENSE) {
            SectionHeader("Kategorie")
            ChipGroup(data.categories.map { it.id to it.name }, d.categoryId, { set(d.copy(categoryId = it)) }, Modifier.padding(horizontal = 20.dp))
        }
        SectionHeader("Details")
        DateFieldRow("Datum", d.date, vm.today) { set(d.copy(date = it)) }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            MonoTextField(d.note, { set(d.copy(note = it)) }, if (d.kind == Kind.EXPENSE) "Wofür? (z. B. Rewe, Propeller)" else "Notiz")
        }
        if (d.fixedCostId != null) EmptyHint("Automatisch aus einer festen Buchung erstellt. Änderungen gelten nur für diese eine Buchung.")
        if (!d.isNew) {
            Gap(16.dp)
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Buchung löschen", color = Mono.Black) }
        }
    }
    if (confirmDelete) ConfirmDialog("Buchung löschen?", "Das kann nicht rückgängig gemacht werden.", "Löschen", { vm.deleteTx(d.id) }, { confirmDelete = false })
    if (scanChoice) ScanChoice(vm, actions) { scanChoice = false }
}

private val suggestionsCost = listOf("Miete", "Nebenkosten", "Strom", "Internet", "Handy", "ChatGPT", "Claude", "YouTube Music", "Deutschland-Ticket", "Versicherung", "Rundfunkbeitrag")
private val suggestionsIncome = listOf("Gehalt", "Kindergeld", "Nebenjob")

@Composable
fun FixedEditor(vm: MainViewModel, data: AppData) {
    val d = vm.fixedDraft ?: return
    fun set(n: FixedDraft) { vm.fixedDraft = n }
    var confirmDelete by remember { mutableStateOf(false) }
    val what = if (d.income) "Einnahme" else "Ausgabe"

    EditorScaffold(if (d.isNew) "Feste $what" else d.name.ifBlank { "Feste $what" }, onClose = { vm.fixedDraft = null }, onSave = { vm.saveFixed(d) }) {
        if (d.isNew) {
            ChipGroup(listOf(false to "Feste Ausgabe", true to "Feste Einnahme"), d.income, {
                set(d.copy(income = it, name = if (it && d.name.isBlank()) "Gehalt" else if (!it && d.name == "Gehalt") "" else d.name))
            }, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            MonoTextField(d.name, { set(d.copy(name = it)) }, "Name")
        }
        if (d.isNew) {
            val existing = data.fixed.map { it.name.lowercase() }.toSet()
            val sugg = (if (d.income) suggestionsIncome else suggestionsCost).filter { it.lowercase() !in existing }
            ChipGroup(sugg.map { it to it }, d.name, { set(d.copy(name = it)) }, Modifier.padding(horizontal = 20.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            MonoTextField(d.amount, { set(d.copy(amount = it)) }, "Betrag", number = true, placeholder = "0,00", textStyle = bigAmount, suffix = "€")
        }
        SectionHeader("Rhythmus")
        ChipGroup(Schedule.intervals, d.intervalMonths, { set(d.copy(intervalMonths = it)) }, Modifier.padding(horizontal = 20.dp))
        SectionHeader("Wann")
        DateFieldRow(if (d.income) "Nächster Eingang" else "Nächste Abbuchung", d.nextDue, vm.today) { set(d.copy(nextDue = it)) }
        SectionHeader(if (d.income) "Eingang auf" else "Abbuchung von")
        ChipGroup(listOf(Src.ACCOUNT to "Konto", Src.CASH to "Bar"), d.source, { set(d.copy(source = it)) }, Modifier.padding(horizontal = 20.dp))
        Gap(8.dp)
        SwitchRow("Aktiv", d.active, { set(d.copy(active = it)) }, sub = "Pausierte Posten werden nicht gebucht")
        val cents = Money.parse(d.amount)
        if (cents != null && cents > 0 && d.intervalMonths > 1) {
            EmptyHint("Entspricht ${Money.format(Math.round(cents.toDouble() / d.intervalMonths))} pro Monat.")
        }
        EmptyHint(
            if (d.income) "Wird am Tag des Eingangs automatisch auf dein Konto gebucht und vorher als „erwartet“ angezeigt."
            else "Wird am Fälligkeitstag automatisch abgezogen und vorher als „verplant“ angezeigt.",
        )
        if (!d.isNew) {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Löschen", color = Mono.Black) }
        }
    }
    if (confirmDelete) ConfirmDialog("„${d.name}“ löschen?", "Künftige Buchungen fallen weg. Bisherige Buchungen bleiben erhalten.", "Löschen", { vm.deleteFixed(d.id) }, { confirmDelete = false })
}

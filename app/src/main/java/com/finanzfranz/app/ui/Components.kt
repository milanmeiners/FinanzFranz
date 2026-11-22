package com.finanzfranz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------- Formatting

object Fmt {
    private val L = Locale.GERMANY
    val dayLong: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", L)
    val dayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d. MMM", L)
    val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM", L)
    val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", L)
    val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", L)

    fun date(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "Heute"
        today.plusDays(1) -> "Morgen"
        today.minusDays(1) -> "Gestern"
        else -> if (d.year == today.year) d.format(dayShort) else d.format(dayMonthYear)
    }

    fun month(ym: YearMonth): String = ym.format(monthYear)
}

// ---------------------------------------------------------------- Basic elements

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 6.dp).heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(Locale.GERMANY), style = SectionLabel, modifier = Modifier.weight(1f))
        if (action != null) action()
    }
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier, start: Dp = 0.dp) {
    HorizontalDivider(modifier.padding(start = start), thickness = 0.5.dp, color = Mono.G200)
}

/** Thin bar; [fraction] 0..1. If [over] the bar is drawn full and hatched-dark. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, height: Dp = 4.dp, over: Boolean = false) {
    val f = fraction.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(Mono.G200)) {
        Box(Modifier.fillMaxWidth(if (over) 1f else f).height(height).background(if (over) Mono.Black else Mono.G800))
    }
}

@Composable
fun MonoChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape)
            .background(if (selected) Mono.Black else Mono.White)
            .border(1.dp, if (selected) Mono.Black else Mono.G300, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 14.sp, color = if (selected) Mono.White else Mono.Ink, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipGroup(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) MonoChip(label, value == selected, { onSelect(value) })
    }
}

@Composable
fun MonoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    placeholder: String? = null,
    number: Boolean = false,
    textStyle: TextStyle? = null,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Mono.G400, style = textStyle ?: MaterialTheme.typography.bodyLarge) } },
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it, style = textStyle ?: MaterialTheme.typography.bodyLarge) } },
        textStyle = textStyle ?: MaterialTheme.typography.bodyLarge,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Mono.Black,
            unfocusedBorderColor = Mono.G300,
            focusedLabelColor = Mono.Black,
            unfocusedLabelColor = Mono.G500,
            cursorColor = Mono.Black,
            errorBorderColor = Mono.Black,
            errorLabelColor = Mono.Black,
            errorSupportingTextColor = Mono.Black,
        ),
    )
}

/** A row "Label ........ value" that opens a picker. */
@Composable
fun FieldRow(label: String, value: String, onClick: () -> Unit, muted: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = if (muted) Mono.G400 else Mono.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Mono.White, checkedTrackColor = Mono.Black, checkedBorderColor = Mono.Black,
                uncheckedThumbColor = Mono.G500, uncheckedTrackColor = Mono.White, uncheckedBorderColor = Mono.G400,
            ),
        )
    }
}

/** "Label            1.234,56 €" */
@Composable
fun AmountRow(label: String, value: String, modifier: Modifier = Modifier, strong: Boolean = false, muted: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (muted) Mono.G600 else Mono.Ink, modifier = Modifier.weight(1f))
        Text(value, style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = if (muted) Mono.G600 else Mono.Ink)
    }
}

@Composable
fun MonthSwitcher(ym: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(ym.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vorheriger Monat") }
        Text(Fmt.month(ym), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onChange(ym.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nächster Monat") }
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Mono.G500, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp))
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, Mono.G200, RoundedCornerShape(14.dp)).padding(16.dp),
    ) { content() }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))

// ---------------------------------------------------------------- Dialogs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    ) { DatePicker(state = state) }
}

@Composable
fun DateFieldRow(label: String, value: LocalDate, today: LocalDate, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FieldRow(label, Fmt.date(value, today), onClick = { open = true })
    if (open) DatePickerModal(value, onDismiss = { open = false }, onPick = onChange)
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun ChoiceDialog(title: String, options: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                for ((label, action) in options) {
                    Text(
                        label,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { action(); onDismiss() }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

val Transparent = Color.Transparent

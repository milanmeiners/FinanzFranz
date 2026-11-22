package com.finanzfranz.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Actions that need the Activity (camera, files). */
class Actions(
    val scanCamera: () -> Unit,
    val scanGallery: () -> Unit,
    val exportCsv: () -> Unit,
    val exportPdf: () -> Unit,
    val backup: () -> Unit,
    val restore: () -> Unit,
)

private data class TabDef(val title: String, val icon: ImageVector)

private val tabs = listOf(
    TabDef("Übersicht", Icons.Outlined.Home),
    TabDef("Buchungen", Icons.Outlined.ReceiptLong),
    TabDef("Fixkosten", Icons.Outlined.Repeat),
    TabDef("Bericht", Icons.Outlined.BarChart),
)

@Composable
fun AppRoot(vm: MainViewModel, actions: Actions) {
    val data by vm.data.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Box(Modifier.fillMaxSize().background(Mono.White)) {
        when {
            !data.loaded || data.settings == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Mono.Black, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
            data.settings?.setupDone != true -> SetupScreen(onDone = vm::completeSetup)
            vm.txDraft != null -> {
                BackHandler { vm.txDraft = null }
                TxEditor(vm, data, actions)
            }
            vm.fixedDraft != null -> {
                BackHandler { vm.fixedDraft = null }
                FixedEditor(vm, data)
            }
            vm.showCategories -> {
                BackHandler { vm.showCategories = false }
                CategoriesScreen(vm, data)
            }
            else -> MainScaffold(vm, data, actions, snackbar)
        }
        if (vm.txDraft != null || vm.fixedDraft != null || vm.showCategories || data.settings?.setupDone != true) {
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
        }
        if (vm.reconcileOpen && data.settings?.setupDone == true) ReconcileDialog(vm, data)
        if (vm.scanning) Box(Modifier.fillMaxSize().background(Mono.White.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Mono.Black, strokeWidth = 2.dp)
                Gap(12.dp)
                Text("Bon wird gelesen …")
            }
        }
    }
}

@Composable
private fun MainScaffold(vm: MainViewModel, data: com.finanzfranz.app.data.AppData, actions: Actions, snackbar: SnackbarHostState) {
    BackHandler(enabled = vm.tab != 0) { vm.tab = 0 }
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Mono.White,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(Mono.White).statusBarsPadding().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (vm.tab == 0) "FinanzFranz" else tabs[vm.tab].title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menü") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Mono.White) {
                        DropdownMenuItem(text = { Text("Bestand abgleichen") }, onClick = { menu = false; vm.openReconcile() })
                        DropdownMenuItem(text = { Text("Kategorien & Budgets") }, onClick = { menu = false; vm.showCategories = true })
                        HorizontalDivider(color = Mono.G200)
                        DropdownMenuItem(text = { Text("Export als CSV") }, onClick = { menu = false; actions.exportCsv() })
                        DropdownMenuItem(text = { Text("Monatsbericht als PDF") }, onClick = { menu = false; actions.exportPdf() })
                        HorizontalDivider(color = Mono.G200)
                        DropdownMenuItem(text = { Text("Sicherung speichern") }, onClick = { menu = false; actions.backup() })
                        DropdownMenuItem(text = { Text("Sicherung laden") }, onClick = { menu = false; actions.restore() })
                    }
                }
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 0.5.dp, color = Mono.G200)
                NavigationBar(containerColor = Mono.White, tonalElevation = 0.dp) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = vm.tab == i,
                            onClick = { vm.tab = i },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.title, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Mono.Black, selectedTextColor = Mono.Black, indicatorColor = Mono.G100,
                                unselectedIconColor = Mono.G500, unselectedTextColor = Mono.G500,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            when (vm.tab) {
                0, 1 -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    var scanChoice by remember { mutableStateOf(false) }
                    SmallFloatingActionButton(
                        onClick = { scanChoice = true },
                        containerColor = Mono.G100, contentColor = Mono.Black, shape = RoundedCornerShape(12.dp),
                        elevation = FloatingActionButtonDefaults.elevation(2.dp),
                    ) { Icon(Icons.Outlined.DocumentScanner, "Kassenbon scannen") }
                    FloatingActionButton(
                        onClick = { vm.newTx() },
                        containerColor = Mono.Black, contentColor = Mono.White, shape = RoundedCornerShape(16.dp),
                    ) { Icon(Icons.Filled.Add, "Neue Buchung") }
                    if (scanChoice) ScanChoice(vm, actions) { scanChoice = false }
                }
                2 -> FloatingActionButton(
                    onClick = { vm.newFixed(income = false) },
                    containerColor = Mono.Black, contentColor = Mono.White, shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Filled.Add, "Neue feste Ausgabe") }
                else -> {}
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (vm.tab) {
                0 -> OverviewScreen(vm, data)
                1 -> TransactionsScreen(vm, data)
                2 -> FixedScreen(vm, data)
                else -> ReportScreen(vm, data)
            }
        }
    }
}

/** Camera or gallery; opens an empty expense draft first so the result lands there. */
@Composable
fun ScanChoice(vm: MainViewModel, actions: Actions, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = "Kassenbon scannen",
        options = listOf(
            "Foto aufnehmen" to { if (vm.txDraft == null) vm.newTx(); actions.scanCamera() },
            "Aus Galerie wählen" to { if (vm.txDraft == null) vm.newTx(); actions.scanGallery() },
        ),
        onDismiss = onDismiss,
    )
}

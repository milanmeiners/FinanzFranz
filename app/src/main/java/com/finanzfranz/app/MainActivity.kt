package com.finanzfranz.app

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.finanzfranz.app.data.Backup
import com.finanzfranz.app.domain.ReceiptParser
import com.finanzfranz.app.export.Exporter
import com.finanzfranz.app.ocr.ReceiptScanner
import com.finanzfranz.app.ui.Actions
import com.finanzfranz.app.ui.AppRoot
import com.finanzfranz.app.ui.FinanzFranzTheme
import com.finanzfranz.app.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var photoUri: Uri? = null

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) readReceipt(uri)
    }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) readReceipt(uri)
    }

    private val csvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@registerForActivityResult
        writeTo(uri, "CSV gespeichert") { it.write(Exporter.csv(vm.repo.snapshot()).toByteArray(Charsets.UTF_8)) }
    }

    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri ?: return@registerForActivityResult
        writeTo(uri, "PDF gespeichert") { Exporter.pdf(vm.repo.snapshot(), vm.month, it) }
    }

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        writeTo(uri, "Sicherung gespeichert") { it.write(Backup.toJson(vm.repo.snapshot()).toByteArray(Charsets.UTF_8)) }
    }

    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Datei nicht lesbar")
                }
                val data = Backup.fromJson(text)
                vm.repo.replaceAll(data)
                vm.message = "Sicherung geladen: ${data.txs.size} Buchungen"
            } catch (e: Exception) {
                vm.message = "Laden fehlgeschlagen: ${e.message}"
            }
        }
    }

    private fun writeTo(uri: Uri, done: String, block: suspend (java.io.OutputStream) -> Unit) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.use { block(it) } ?: error("Datei nicht beschreibbar")
                }
                vm.message = done
            } catch (e: Exception) {
                vm.message = "Fehlgeschlagen: ${e.message}"
            }
        }
    }

    private fun readReceipt(uri: Uri) {
        vm.scanning = true
        lifecycleScope.launch {
            try {
                val lines = ReceiptScanner.scan(this@MainActivity, uri)
                val result = withContext(Dispatchers.Default) { ReceiptParser.parse(lines, LocalDate.now()) }
                vm.applyReceipt(result)
            } catch (e: Exception) {
                vm.message = "Bon konnte nicht gelesen werden: ${e.message}"
            } finally {
                vm.scanning = false
            }
        }
    }

    private fun startCamera() {
        val dir = File(cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "bon.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        photoUri = uri
        try {
            takePhoto.launch(uri)
        } catch (e: Exception) {
            vm.message = "Kamera nicht verfügbar"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
        )
        super.onCreate(savedInstanceState)
        val actions = Actions(
            scanCamera = { startCamera() },
            scanGallery = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            exportCsv = { csvLauncher.launch("finanzfranz-${LocalDate.now()}.csv") },
            exportPdf = { pdfLauncher.launch("finanzfranz-bericht-${vm.month}.pdf") },
            backup = { backupLauncher.launch("finanzfranz-sicherung-${LocalDate.now()}.json") },
            restore = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
        )
        setContent { FinanzFranzTheme { AppRoot(vm, actions) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshToday()
    }
}

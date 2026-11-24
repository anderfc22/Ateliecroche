package com.lelecroche.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase(context.applicationContext) }
    var counts by remember { mutableStateOf(AppDatabase.BACKUP_TABLES.map { it to runCatching { db.count(it) }.getOrDefault(0) }) }
    var status by remember { mutableStateOf<String?>(null) }
    var backupText by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<JSONObject?>(null) }

    fun refreshCounts() {
        counts = AppDatabase.BACKUP_TABLES.map { it to runCatching { db.count(it) }.getOrDefault(0) }
    }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val output = context.contentResolver.openOutputStream(uri) ?: error("Não foi possível abrir o arquivo.")
                output.use { it.write(backupText.toByteArray(Charsets.UTF_8)) }
                status = "Backup completo salvo com sucesso."
            } catch (e: Exception) {
                status = "Não foi possível salvar o backup: ${e.message}"
            }
        }
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Não foi possível ler o arquivo.")
                require(bytes.size <= 25 * 1024 * 1024) { "O arquivo excede o limite de 25 MB." }
                val json = JSONObject(bytes.toString(Charsets.UTF_8))
                require(json.optInt("schema_version", 1) == 1) { "Versão de backup não suportada." }
                val required = AppDatabase.BACKUP_TABLES.filterNot { it == "photos" || it == "catalog_hits" }
                required.forEach { table -> require(json.opt(table) is org.json.JSONArray) { "O arquivo não contém a tabela $table." } }
                pendingRestore = json
                status = "Arquivo válido. Confirme a restauração para continuar."
            } catch (e: Exception) {
                status = "Backup inválido ou ilegível: ${e.message}"
            }
        }
    }

    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Substituir todos os dados?") },
            text = { Text("A restauração apagará os dados atuais deste aparelho e os substituirá pelo conteúdo do arquivo selecionado. Essa ação não pode ser desfeita. Faça um backup atual antes de continuar.") },
            confirmButton = {
                TextButton(onClick = {
                    val backup = pendingRestore
                    pendingRestore = null
                    try {
                        if (backup != null) db.restoreBackup(backup)
                        refreshCounts()
                        status = "Restauração concluída. Os dados foram substituídos com sucesso."
                    } catch (e: Exception) {
                        status = "Restauração cancelada; os dados anteriores foram preservados. ${e.message}"
                    }
                }) { Text("Substituir dados") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancelar") }
            }
        )
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Backup e dados", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("Os dados ficam armazenados localmente neste aparelho.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Text("Registros por módulo", fontWeight = FontWeight.SemiBold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(counts) { (table, count) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(table.replace('_', ' ').replaceFirstChar { it.uppercase() }, fontSize = 13.sp)
                    Text(count.toString(), fontWeight = FontWeight.SemiBold)
                }
                Divider()
            }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
        Button(onClick = {
            try {
                val root = JSONObject()
                root.put("schema_version", 1)
                root.put("exportado_em", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                AppDatabase.BACKUP_TABLES.forEach { table -> root.put(table, db.exportTableJson(table)) }
                backupText = root.toString(2)
                createDocument.launch("lele-croche-backup.json")
            } catch (e: Exception) {
                status = "Erro ao preparar backup: ${e.message}"
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Exportar backup completo") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { openDocument.launch(arrayOf("application/json", "text/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Importar e restaurar backup")
        }
        Spacer(Modifier.height(6.dp))
        Text("O arquivo inclui cadastros, receitas, pedidos, pagamentos, produção, estoque, compras, despesas, configurações, fotos e histórico de acessos ao catálogo.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text("A restauração substitui os dados atuais somente após confirmação.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

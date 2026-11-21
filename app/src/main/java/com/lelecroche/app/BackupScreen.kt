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
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase(context.applicationContext) }
    val tables = listOf("materials", "stock_movements", "purchases", "products", "recipe_items", "customers", "orders", "order_items", "payments", "production_orders", "expenses", "incomes", "shopping_list", "settings")
    val counts = remember { tables.map { it to runCatching { db.count(it) }.getOrDefault(0) } }
    var status by remember { mutableStateOf<String?>(null) }
    var backupText by remember { mutableStateOf("") }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(backupText.toByteArray(Charsets.UTF_8)) }
                status = "Backup salvo com sucesso."
            } catch (e: Exception) {
                status = "Não foi possível salvar o backup: ${e.message}"
            }
        }
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
                root.put("exportado_em", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                tables.forEach { table ->
                    val rows = if (table == "settings") db.rows("SELECT * FROM settings ORDER BY skey") else db.rows("SELECT * FROM $table ORDER BY id")
                    val array = JSONArray()
                    rows.forEach { row -> array.put(JSONObject(row)) }
                    root.put(table, array)
                }
                backupText = root.toString(2)
                createDocument.launch("lele-croche-backup.json")
            } catch (e: Exception) {
                status = "Erro ao preparar backup: ${e.message}"
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Exportar backup em JSON") }
        Spacer(Modifier.height(6.dp))
        Text("O backup inclui cadastros, receitas, pedidos, pagamentos, produção, estoque, compras, despesas e configurações. Fotos não são incluídas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

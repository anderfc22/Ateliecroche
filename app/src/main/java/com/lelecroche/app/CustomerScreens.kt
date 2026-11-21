package com.lelecroche.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun NativeCustomersScreen(modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val db = remember { AppDatabase(context.applicationContext) }
    val repo = remember { CrochetRepository(db) }
    var refresh by remember { mutableIntStateOf(0) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val customers = remember(refresh, query) {
        db.rows("SELECT c.id,c.name,c.phone,c.city,c.notes,COUNT(o.id) orders,COALESCE(SUM(o.total),0) total FROM customers c LEFT JOIN orders o ON o.customer_id=c.id WHERE c.name LIKE '%$query%' OR c.phone LIKE '%$query%' OR c.city LIKE '%$query%' GROUP BY c.id ORDER BY c.name COLLATE NOCASE")
    }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Clientes", fontSize = 24.sp, fontWeight = FontWeight.SemiBold); Text("${customers.size} cadastrado(s)", color = Color(0xFF424842), fontSize = 12.sp) }
            Button(onClick = { editingId = null; showEditor = true }) { Icon(Icons.Default.Add, null); Text(" Novo") }
        }
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(query, { query = it }, label = { Text("Buscar por nome, telefone ou cidade") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Spacer(Modifier.height(8.dp))
        if (customers.isEmpty()) Text("Nenhum cliente encontrado.", color = Color(0xFF424842), fontSize = 13.sp)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(customers, key = { it["id"].orEmpty() }) { c ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(13.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(Color(0xFFE5F1E5), CircleShape), contentAlignment = Alignment.Center) {
                            Text(c["name"].orEmpty().take(1).uppercase(), color = Color(0xFF42634C), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c["name"].orEmpty(), fontWeight = FontWeight.SemiBold)
                            Text(listOf(c["city"].orEmpty(), c["phone"].orEmpty()).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Sem contato" }, color = Color(0xFF424842), fontSize = 11.sp)
                            Text("${c["orders"]} pedido(s) · R$ " + String.format(Locale("pt","BR"), "%.2f", c["total"]?.toDoubleOrNull() ?: 0.0), color = Color(0xFF42634C), fontSize = 10.sp)
                        }
                        IconButton(onClick = { editingId = c["id"]!!.toLong(); showEditor = true }) { Icon(Icons.Default.Edit, "Editar", tint = Color(0xFF42634C)) }
                        IconButton(onClick = {
                            try { repo.deleteCustomer(c["id"]!!.toLong()); refresh++; error = null }
                            catch (e: Exception) { error = e.message ?: "Não foi possível excluir." }
                        }) { Icon(Icons.Default.DeleteOutline, "Excluir", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    if (showEditor) CustomerEditorDialog(db, repo, editingId, onDismiss = { showEditor = false }, onSaved = { showEditor = false; refresh++ })
}

@Composable
private fun CustomerEditorDialog(db: AppDatabase, repo: CrochetRepository, id: Long?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val current = remember(id) { id?.let { db.rows("SELECT * FROM customers WHERE id=?", arrayOf(it.toString())).firstOrNull() } }
    var name by remember(id) { mutableStateOf(current?.get("name").orEmpty()) }
    var phone by remember(id) { mutableStateOf(current?.get("phone").orEmpty()) }
    var city by remember(id) { mutableStateOf(current?.get("city").orEmpty()) }
    var notes by remember(id) { mutableStateOf(current?.get("notes").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (id == null) "Novo cliente" else "Editar cliente") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(phone, { phone = it }, label = { Text("Telefone / WhatsApp") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(city, { city = it }, label = { Text("Cidade") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("Observações") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }, confirmButton = { TextButton(onClick = {
        try { repo.saveCustomer(id, name, phone, city, notes); onSaved() }
        catch (e: Exception) { error = e.message ?: "Não foi possível salvar." }
    }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

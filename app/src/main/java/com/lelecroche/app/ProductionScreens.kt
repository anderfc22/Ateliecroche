package com.lelecroche.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProductionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase(context.applicationContext) }
    val repo = remember { CrochetRepository(db) }
    var refresh by remember { mutableIntStateOf(0) }
    var create by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("ativas") }
    var error by remember { mutableStateOf<String?>(null) }
    val where = when(filter) { "ativas" -> "WHERE po.status IN ('fila','andamento','acabamento')"; "prontas" -> "WHERE po.status='pronta'"; "canceladas" -> "WHERE po.status='cancelada'"; else -> "" }
    val orders = remember(refresh, filter) { db.rows("SELECT po.id,po.product_id,po.qty,po.status,po.order_id,po.created_at,p.name product_name FROM production_orders po JOIN products p ON p.id=po.product_id $where ORDER BY po.id DESC") }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Produção", fontSize = 24.sp, fontWeight = FontWeight.SemiBold); Text("${orders.size} ordem(ns)", color = Color(0xFF424842), fontSize = 12.sp) }
            Button(onClick = { create = true }) { Icon(Icons.Default.Add, null); Text(" Nova") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ativas" to "Ativas", "prontas" to "Prontas", "canceladas" to "Canceladas", "todas" to "Todas").forEach { (v,l) -> FilterChip(selected = filter == v, onClick = { filter = v }, label = { Text(l, fontSize = 11.sp) }) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Spacer(Modifier.height(8.dp))
        if (orders.isEmpty()) Text("Nenhuma ordem de produção nesta categoria.", color = Color(0xFF424842), fontSize = 13.sp)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(orders, key = { it["id"].orEmpty() }) { po ->
                val status = po["status"].orEmpty()
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(13.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(po["product_name"].orEmpty(), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(status.replaceFirstChar { it.uppercase() }, color = Color(0xFF42634C), fontSize = 11.sp)
                        }
                        Text("Quantidade: ${po["qty"]} · Ordem #${po["id"]}" + (po["order_id"]?.let { if (it.isNotBlank()) " · Pedido #$it" else "" } ?: ""), color = Color(0xFF424842), fontSize = 11.sp)
                        Text(po["created_at"].orEmpty(), color = Color(0xFF727972), fontSize = 10.sp)
                        if (status in listOf("fila", "andamento", "acabamento")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    try { repo.setProductionStatus(po["id"]!!.toLong(), CrochetEngine.productionNext(status)); error = null; refresh++ }
                                    catch (e: Exception) { error = e.message ?: "Não foi possível avançar a produção." }
                                }, modifier = Modifier.weight(1f)) { Text(if (status == "acabamento") "Concluir peça" else "Avançar etapa", fontSize = 12.sp) }
                                OutlinedButton(onClick = {
                                    try { repo.setProductionStatus(po["id"]!!.toLong(), "cancelada"); refresh++ }
                                    catch (e: Exception) { error = e.message }
                                }) { Text("Cancelar", fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (create) NewProductionDialog(db, repo, onDismiss = { create = false }, onSaved = { create = false; refresh++ })
}

@Composable
private fun NewProductionDialog(db: AppDatabase, repo: CrochetRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val products = remember { db.rows("SELECT id,name,stock FROM products WHERE active=1 ORDER BY name") }
    var selected by remember { mutableStateOf(products.firstOrNull()?.get("id").orEmpty()) }
    var quantity by remember { mutableStateOf("1") }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nova ordem de produção") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(products.firstOrNull { it["id"] == selected }?.get("name") ?: "Selecione a peça") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { products.forEach { p -> DropdownMenuItem(text = { Text(p["name"].orEmpty()) }, onClick = { selected = p["id"].orEmpty(); expanded = false }) } }
            }
            OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantidade") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }, confirmButton = { TextButton(onClick = {
        try { val q = quantity.replace(",", ".").toDoubleOrNull() ?: 0.0; repo.addProduction(selected.toLongOrNull() ?: 0L, q); onSaved() }
        catch (e: Exception) { error = e.message ?: "Não foi possível criar a ordem." }
    }) { Text("Criar ordem") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

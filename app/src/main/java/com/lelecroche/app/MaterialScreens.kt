package com.lelecroche.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.io.ByteArrayOutputStream

private val StockGreen = Color(0xFF42634C)
private val StockText = Color(0xFF161D19)
private val StockMuted = Color(0xFF424842)

@Composable
fun MaterialsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase(context.applicationContext) }
    val repo = remember { CrochetRepository(db) }
    var refresh by remember { mutableIntStateOf(0) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var actionId by remember { mutableStateOf<Long?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showInventory by remember { mutableStateOf(false) }
    var showShopping by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val materials = remember(refresh) { db.rows("SELECT * FROM materials WHERE active=1 ORDER BY name COLLATE NOCASE") }
    val demands = remember(refresh) { runCatching { repo.materialDemand().associateBy { it.material.id } }.getOrDefault(emptyMap()) }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Estoque", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = StockText)
                Text("${materials.size} material(is) ativo(s)", color = StockMuted, fontSize = 12.sp)
            }
            IconButton(onClick = { showHistory = true }) { Icon(Icons.Default.History, "Histórico") }
            IconButton(onClick = { showShopping = true }) { Icon(Icons.Default.ShoppingCart, "Lista de compras") }
            Button(onClick = { editingId = null; showEditor = true }) { Icon(Icons.Default.Add, null); Text(" Novo") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showInventory = true }) { Icon(Icons.Default.Inventory, null); Text(" Inventário") }
            OutlinedButton(onClick = { actionId = -1L }) { Icon(Icons.Default.ShoppingCart, null); Text("Lista de compras") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Spacer(Modifier.height(8.dp))
        if (materials.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nenhum material cadastrado", fontWeight = FontWeight.SemiBold)
                    Text("Cadastre fios, ferragens, enchimentos e outros insumos.", color = StockMuted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(materials, key = { it["id"].orEmpty() }) { m ->
                    val id = m["id"]?.toLongOrNull() ?: 0L
                    val stock = m["stock"]?.toDoubleOrNull() ?: 0.0
                    val min = m["min_stock"]?.toDoubleOrNull() ?: 0.0
                    val demand = demands[id]
                    val short = demand?.shortage ?: 0.0
                    val low = min > 0 && stock <= min
                    Card(modifier = Modifier.fillMaxWidth().clickable { actionId = id }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m["name"].orEmpty(), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = StockText)
                                if (low || short > 0) Text(if (short > 0) "Falta para produção" else "Estoque baixo", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                            }
                            Text(listOf(m["kind"].orEmpty(), m["color"].orEmpty(), m["brand"].orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), color = StockMuted, fontSize = 11.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Disponível: ${stockNumber(stock)} ${m["unit"]}", fontWeight = FontWeight.Medium, color = StockGreen, fontSize = 13.sp)
                                Text("Custo médio: ${stockMoney(m["avg_cost"]?.toDoubleOrNull() ?: 0.0)}", color = StockMuted, fontSize = 11.sp)
                            }
                            if (min > 0) Text("Mínimo: ${stockNumber(min)} ${m["unit"]}", color = StockMuted, fontSize = 11.sp)
                            if (demand != null && demand.required > 0) Text("Reservado para produção: ${stockNumber(demand.required)} ${m["unit"]} · faltam ${stockNumber(short)}", color = if (short > 0) MaterialTheme.colorScheme.error else StockMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
    if (showEditor) MaterialEditorDialog(db, repo, editingId, onDismiss = { showEditor = false }, onSaved = { showEditor = false; refresh++ })
    actionId?.let { id ->
        if (id == -1L) ShoppingListDialog(db, repo, onDismiss = { actionId = null }, onChanged = { refresh++ })
        else MaterialActionsDialog(db, repo, id, onDismiss = { actionId = null }, onEdit = { editingId = id; actionId = null; showEditor = true }, onChanged = { actionId = null; refresh++ })
    }
    if (showInventory) InventoryDialog(db, repo, onDismiss = { showInventory = false }, onSaved = { showInventory = false; refresh++ })
    if (showShopping) ShoppingListDialog(db, repo, onDismiss = { showShopping = false }, onChanged = { refresh++ })
    if (showHistory) StockHistoryDialog(db, onDismiss = { showHistory = false })
}

private fun stockNumber(value: Double) = String.format(Locale("pt", "BR"), "%.3f", value).trimEnd('0').trimEnd(',')
private fun stockMoney(value: Double) = "R$ " + String.format(Locale("pt", "BR"), "%.2f", value)
private fun stockDecimal(value: String) = value.trim().let { raw -> if (raw.contains(",")) raw.replace(".", "").replace(",", ".").toDoubleOrNull() else raw.toDoubleOrNull() }

@Composable
private fun MaterialEditorDialog(db: AppDatabase, repo: CrochetRepository, id: Long?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val current = remember(id) { id?.let { db.rows("SELECT * FROM materials WHERE id=?", arrayOf(it.toString())).firstOrNull() } }
    var name by remember(id) { mutableStateOf(current?.get("name").orEmpty()) }
    var kind by remember(id) { mutableStateOf(current?.get("kind").orEmpty().ifBlank { "fio" }) }
    var color by remember(id) { mutableStateOf(current?.get("color").orEmpty()) }
    var brand by remember(id) { mutableStateOf(current?.get("brand").orEmpty()) }
    var unit by remember(id) { mutableStateOf(current?.get("unit").orEmpty().ifBlank { "kg" }) }
    var metersPerKg by remember(id) { mutableStateOf(current?.get("meters_per_kg").orEmpty()) }
    var minimum by remember(id) { mutableStateOf(current?.get("min_stock").orEmpty().ifBlank { "0" }) }
    var initialQty by remember(id) { mutableStateOf("") }
    var initialCost by remember(id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var photoId by remember(id) { mutableStateOf(current?.get("photo_id")?.toLongOrNull()) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: throw IllegalArgumentException("Não foi possível abrir a imagem.")
                val ratio = minOf(1f, 800f / maxOf(bitmap.width, bitmap.height).toFloat())
                val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true) else bitmap
                val bytes = ByteArrayOutputStream().use { output -> scaled.compress(Bitmap.CompressFormat.JPEG, 82, output); output.toByteArray() }
                photoId = repo.storePhoto("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
            } catch (e: Exception) { error = e.message ?: "Não foi possível carregar a foto." }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (id == null) "Novo material" else "Editar material") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(kind, { kind = it }, label = { Text("Tipo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(color, { color = it }, label = { Text("Cor") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(brand, { brand = it }, label = { Text("Marca / fornecedor") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Text("Unidade de controle", fontSize = 12.sp, color = StockMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("kg", "m", "un").forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) }) }
                }
                if (unit != "un") OutlinedTextField(metersPerKg, { metersPerKg = it }, label = { Text("Metros por kg (necessário para converter kg/m)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(minimum, { minimum = it }, label = { Text("Estoque mínimo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val photoData = remember(photoId) { photoId?.let { repo.photoBase64(it) } }
                    val photoBytes = remember(photoData) { photoData?.let { Base64.decode(it, Base64.DEFAULT) } }
                    val photoBitmap = remember(photoBytes) { photoBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
                    if (photoBitmap != null) Image(photoBitmap.asImageBitmap(), contentDescription = "Foto do material", modifier = Modifier.size(72.dp))
                    OutlinedButton(onClick = { photoPicker.launch("image/*") }) { Text(if (photoId == null) "Adicionar foto" else "Trocar foto") }
                    if (photoId != null) TextButton(onClick = { photoId = null }) { Text("Remover") }
                }
                if (id == null) {
                    Divider()
                    Text("Estoque inicial (opcional)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(initialQty, { initialQty = it }, label = { Text("Quantidade") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(initialCost, { initialCost = it }, label = { Text("Custo total R$") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Text("O estoque inicial será registrado como uma compra para calcular o custo médio.", color = StockMuted, fontSize = 11.sp)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = {
            try {
                val savedId = repo.saveMaterial(id, name, kind, color, brand, unit, stockDecimal(metersPerKg) ?: 0.0, stockDecimal(minimum) ?: 0.0)
                repo.setMaterialPhoto(savedId, photoId)
                val qty = stockDecimal(initialQty) ?: 0.0
                val cost = stockDecimal(initialCost) ?: 0.0
                if (id == null && qty > 0) repo.recordPurchase(savedId, qty, unit, cost, brand)
                onSaved()
            } catch (e: Exception) { error = e.message ?: "Não foi possível salvar o material." }
        }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun MaterialActionsDialog(db: AppDatabase, repo: CrochetRepository, id: Long, onDismiss: () -> Unit, onEdit: () -> Unit, onChanged: () -> Unit) {
    val material = remember(id) { db.rows("SELECT * FROM materials WHERE id=?", arrayOf(id.toString())).firstOrNull() }
    var action by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    if (material == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(material["name"].orEmpty()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Saldo: ${stockNumber(material["stock"]?.toDoubleOrNull() ?: 0.0)} ${material["unit"]}", color = StockGreen, fontWeight = FontWeight.SemiBold)
                Text("Custo médio: ${stockMoney(material["avg_cost"]?.toDoubleOrNull() ?: 0.0)}", color = StockMuted, fontSize = 12.sp)
                OutlinedButton(onClick = { action = "compra" }, modifier = Modifier.fillMaxWidth()) { Text("Registrar entrada / compra") }
                OutlinedButton(onClick = { action = "ajuste" }, modifier = Modifier.fillMaxWidth()) { Text("Perda ou ajuste de estoque") }
                OutlinedButton(onClick = { try { repo.addToShoppingList(id, repo.shoppingSuggestion(id)); onChanged() } catch (e: Exception) { error = e.message } }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar à lista de compras") }
                TextButton(onClick = onEdit) { Text("Editar material") }
                TextButton(onClick = { try { repo.archiveMaterial(id); onChanged() } catch (e: Exception) { error = e.message } }) { Text("Arquivar material", color = MaterialTheme.colorScheme.error) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
    if (action == "compra") PurchaseDialog(material, repo, onDismiss = { action = null }, onSaved = onChanged)
    if (action == "ajuste") AdjustmentDialog(material, repo, onDismiss = { action = null }, onSaved = onChanged)
}

@Composable
private fun PurchaseDialog(material: Map<String, String>, repo: CrochetRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(material["unit"].orEmpty()) }
    var total by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf(material["supplier"].orEmpty()) }
    var lot by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Pix") }
    var error by remember { mutableStateOf<String?>(null) }
    val baseUnit = material["unit"].orEmpty()
    val units = if (baseUnit == "un") listOf("un") else listOf("kg", "m")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Entrada de material") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(material["name"].orEmpty(), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantidade") }, modifier = Modifier.weight(1f), singleLine = true)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(unit) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { units.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { unit = u; expanded = false }) } }
                }
            }
            OutlinedTextField(total, { total = it }, label = { Text("Valor total pago (R$)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(supplier, { supplier = it }, label = { Text("Fornecedor") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(lot, { lot = it }, label = { Text("Lote / cor") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(method, { method = it }, label = { Text("Forma de pagamento") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }, confirmButton = { TextButton(onClick = {
        try { repo.recordPurchase(material["id"]!!.toLong(), stockDecimal(quantity) ?: 0.0, unit, stockDecimal(total) ?: 0.0, supplier, lot, method); onSaved() }
        catch (e: Exception) { error = e.message ?: "Compra inválida" }
    }) { Text("Registrar entrada") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun AdjustmentDialog(material: Map<String, String>, repo: CrochetRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var reason by remember { mutableStateOf("perda") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(material["unit"].orEmpty()) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val units = if (material["unit"] == "un") listOf("un") else listOf("kg", "m")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Perda ou ajuste") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Saldo atual: ${material["stock"]} ${material["unit"]}", color = StockMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = reason == "perda", onClick = { reason = "perda" }, label = { Text("Perda") })
                FilterChip(selected = reason == "ajuste", onClick = { reason = "ajuste" }, label = { Text("Ajuste") })
            }
            OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantidade") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { units.forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) }) } }
            OutlinedTextField(note, { note = it }, label = { Text("Motivo / observação") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }, confirmButton = { TextButton(onClick = {
        try { var q = stockDecimal(quantity) ?: 0.0; if (reason == "perda") q = -kotlin.math.abs(q); repo.adjustMaterialStock(material["id"]!!.toLong(), q, unit, reason, note); onSaved() }
        catch (e: Exception) { error = e.message ?: "Ajuste inválido" }
    }) { Text("Salvar ajuste") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun InventoryDialog(db: AppDatabase, repo: CrochetRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val materials = remember { db.rows("SELECT id,name,unit,stock FROM materials WHERE active=1 ORDER BY name") }
    val counts = remember { mutableStateMapOf<Long, String>() }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Inventário") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Conte o que existe fisicamente. Só as diferenças serão registradas.", color = StockMuted, fontSize = 12.sp)
            materials.forEach { m ->
                val id = m["id"]!!.toLong()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { Text(m["name"].orEmpty(), fontWeight = FontWeight.Medium, fontSize = 12.sp); Text("Sistema: ${m["stock"]} ${m["unit"]}", color = StockMuted, fontSize = 10.sp) }
                    OutlinedTextField(value = counts[id].orEmpty(), onValueChange = { counts[id] = it }, label = { Text("Contado") }, modifier = Modifier.width(125.dp), singleLine = true)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }, confirmButton = { TextButton(onClick = {
        try {
            val changed = materials.mapNotNull { m -> val id = m["id"]!!.toLong(); val value = counts[id]; if (value.isNullOrBlank()) null else CrochetRepository.InventoryCount(id, stockDecimal(value) ?: throw IllegalArgumentException("Contagem inválida")) }
            if (changed.isEmpty()) { onDismiss() } else { repo.applyInventory(changed); onSaved() }
        } catch (e: Exception) { error = e.message ?: "Não foi possível salvar o inventário." }
    }) { Text("Salvar inventário") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun ShoppingListDialog(db: AppDatabase, repo: CrochetRepository, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val materials = remember { db.rows("SELECT id,name,unit,stock,min_stock FROM materials WHERE active=1 ORDER BY name") }
    val shopping = remember(refresh) { db.rows("SELECT s.material_id,s.qty,m.name,m.unit,m.stock FROM shopping_list s JOIN materials m ON m.id=s.material_id ORDER BY m.name") }
    var selected by remember { mutableStateOf(materials.firstOrNull()?.get("id").orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Lista de compras") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (materials.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(materials.firstOrNull { it["id"] == selected }?.get("name") ?: "Selecionar material") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { materials.forEach { m -> DropdownMenuItem(text = { Text(m["name"].orEmpty()) }, onClick = { selected = m["id"].orEmpty(); expanded = false }) } }
                }
                OutlinedButton(onClick = { selected.toLongOrNull()?.let { repo.addToShoppingList(it, repo.shoppingSuggestion(it)); refresh++ } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("Adicionar com quantidade sugerida") }
            }
            if (shopping.isEmpty()) Text("Nenhum material na lista.", color = StockMuted, fontSize = 12.sp)
            shopping.forEach { item ->
                Row(Modifier.fillMaxWidth().background(Color(0xFFF4FBF3), RoundedCornerShape(9.dp)).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(item["name"].orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text("Comprar: ${item["qty"]} ${item["unit"]} · saldo: ${item["stock"]}", color = StockMuted, fontSize = 10.sp) }
                    TextButton(onClick = { repo.removeFromShoppingList(item["material_id"]!!.toLong()); refresh++; onChanged() }) { Text("Remover") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } })
}

@Composable
private fun StockHistoryDialog(db: AppDatabase, onDismiss: () -> Unit) {
    val history = remember { db.rows("SELECT sm.*,m.name,m.unit FROM stock_movements sm JOIN materials m ON m.id=sm.material_id ORDER BY sm.id DESC LIMIT 100") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Histórico de estoque") }, text = {
        LazyColumn(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(history) { row ->
                Column(Modifier.fillMaxWidth().background(Color(0xFFF4FBF3), RoundedCornerShape(8.dp)).padding(9.dp)) {
                    Text(row["name"].orEmpty(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("${row["reason"]} · ${row["qty"]} ${row["unit"]} · ${row["created_at"]}", color = StockMuted, fontSize = 10.sp)
                    if (row["note"].orEmpty().isNotBlank()) Text(row["note"].orEmpty(), color = StockMuted, fontSize = 10.sp)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } })
}

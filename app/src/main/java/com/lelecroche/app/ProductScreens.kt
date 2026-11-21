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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
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

private val ProductGreen = Color(0xFF42634C)
private val ProductText = Color(0xFF161D19)
private val ProductMuted = Color(0xFF424842)

private data class RecipeDraft(val materialId: String, val quantity: String, val unit: String)

@Composable
fun ProductsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase(context.applicationContext) }
    val repository = remember { CrochetRepository(db) }
    var refresh by remember { mutableIntStateOf(0) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val products = remember(refresh) { db.rows("SELECT id,name,description,category,price,stock,labor_minutes,in_catalog,photo_id FROM products WHERE active=1 ORDER BY name COLLATE NOCASE") }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Peças", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = ProductText)
                Text("${products.size} modelo(s) ativo(s)", color = ProductMuted, fontSize = 12.sp)
            }
            Button(onClick = { editingId = null; showEditor = true }) { Icon(Icons.Default.Add, null); Text(" Nova") }
        }
        Spacer(Modifier.height(12.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        if (products.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nenhuma peça cadastrada", fontWeight = FontWeight.SemiBold)
                    Text("Cadastre seus modelos e suas receitas de materiais.", color = ProductMuted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(products, key = { it["id"].orEmpty() }) { p ->
                    val id = p["id"]?.toLongOrNull() ?: 0L
                    val pricing = remember(refresh, id) { runCatching { repository.previewProductPricing(id) }.getOrNull() }
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { editingId = id; showEditor = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            val photoId = p["photo_id"]?.toLongOrNull()
                            val imageData = remember(refresh, photoId) { photoId?.let { repository.photoBase64(it) } }
                            val imageBitmap = remember(imageData) { imageData?.let { data -> runCatching { val bytes = Base64.decode(data, Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() } }
                            if (imageBitmap != null) Image(imageBitmap.asImageBitmap(), contentDescription = p["name"], modifier = Modifier.size(58.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(p["name"].orEmpty(), fontWeight = FontWeight.SemiBold, color = ProductText)
                                Text(listOf(p["category"].orEmpty(), "Estoque: ${p["stock"].orEmpty()}").filter { it.isNotBlank() }.joinToString(" · "), color = ProductMuted, fontSize = 11.sp)
                                Text("Custo: ${pricing?.cost?.total?.let(::productMoney) ?: "—"}  ·  Preço: ${pricing?.finalPrice?.let(::productMoney) ?: "—"}", color = ProductGreen, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                if (p["in_catalog"] == "1") Text("No catálogo", color = ProductMuted, fontSize = 10.sp)
                            }
                            IconButton(onClick = { editingId = id; showEditor = true }) { Icon(Icons.Default.Edit, "Editar", tint = ProductGreen) }
                            IconButton(onClick = { try { repository.duplicateProduct(id); refresh++ } catch (e: Exception) { error = e.message } }) { Icon(Icons.Default.ContentCopy, "Duplicar", tint = ProductGreen) }
                            IconButton(onClick = { try { repository.archiveProduct(id); refresh++ } catch (e: Exception) { error = e.message } }) { Icon(Icons.Default.DeleteOutline, "Arquivar", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    if (showEditor) {
        ProductEditorDialog(db, repository, editingId, onDismiss = { showEditor = false }, onSaved = { showEditor = false; refresh++ })
    }
}

private fun productMoney(value: Double) = "R$ " + String.format(Locale("pt", "BR"), "%.2f", value)
private fun productNumber(value: String): Double? = value.trim().let { raw -> if (raw.contains(",")) raw.replace(".", "").replace(",", ".").toDoubleOrNull() else raw.toDoubleOrNull() }

@Composable
private fun ProductEditorDialog(db: AppDatabase, repository: CrochetRepository, productId: Long?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val materials = remember { db.rows("SELECT id,name,unit,meters_per_kg,stock,min_stock,avg_cost FROM materials WHERE active=1 ORDER BY name") }
    val product = remember(productId) { productId?.let { db.rows("SELECT * FROM products WHERE id=?", arrayOf(it.toString())).firstOrNull() } }
    val recipe = remember(productId) {
        mutableStateListOf<RecipeDraft>().apply {
            if (productId != null) db.rows("SELECT material_id,qty,unit FROM recipe_items WHERE product_id=? ORDER BY id", arrayOf(productId.toString())).forEach {
                add(RecipeDraft(it["material_id"].orEmpty(), it["qty"].orEmpty(), it["unit"].orEmpty()))
            }
        }
    }
    var name by remember(productId) { mutableStateOf(product?.get("name").orEmpty()) }
    var description by remember(productId) { mutableStateOf(product?.get("description").orEmpty()) }
    var category by remember(productId) { mutableStateOf(product?.get("category").orEmpty()) }
    var labor by remember(productId) { mutableStateOf(product?.get("labor_minutes").orEmpty().ifBlank { "0" }) }
    var price by remember(productId) { mutableStateOf(product?.get("price").orEmpty()) }
    var margin by remember(productId) { mutableStateOf(product?.get("margin_pct").orEmpty()) }
    var stock by remember(productId) { mutableStateOf(if (productId == null) "0" else "") }
    var inCatalog by remember(productId) { mutableStateOf(product?.get("in_catalog") != "0") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val photoIds = remember(productId) {
        mutableStateListOf<Long?>(product?.get("photo_id")?.toLongOrNull(), product?.get("photo2_id")?.toLongOrNull(), product?.get("photo3_id")?.toLongOrNull())
    }
    var photoSlot by remember { mutableIntStateOf(0) }
    var savedProductId by remember(productId) { mutableStateOf(productId) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: throw IllegalArgumentException("Não foi possível abrir a imagem.")
                val ratio = minOf(1f, 800f / maxOf(bitmap.width, bitmap.height).toFloat())
                val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true) else bitmap
                val bytes = ByteArrayOutputStream().use { output -> scaled.compress(Bitmap.CompressFormat.JPEG, 82, output); output.toByteArray() }
                photoIds[photoSlot] = repository.storePhoto("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
            } catch (e: Exception) { error = e.message ?: "Não foi possível carregar a foto." }
        }
    }
    val recipeModels = recipe.mapNotNull { line ->
        val m = materials.firstOrNull { it["id"] == line.materialId } ?: return@mapNotNull null
        val qty = productNumber(line.quantity) ?: return@mapNotNull null
        val material = CrochetEngine.Material(
            id = m["id"]?.toLongOrNull() ?: 0L, name = m["name"].orEmpty(), unit = m["unit"].orEmpty(),
            metersPerKg = m["meters_per_kg"]?.toDoubleOrNull() ?: 0.0, stock = m["stock"]?.toDoubleOrNull() ?: 0.0,
            minStock = m["min_stock"]?.toDoubleOrNull() ?: 0.0, averageCost = m["avg_cost"]?.toDoubleOrNull() ?: 0.0
        )
        CrochetEngine.RecipeLine(material, qty, line.unit)
    }
    val preview = runCatching {
        val settings = db.rows("SELECT skey,svalue FROM settings").associate { it["skey"].orEmpty() to it["svalue"].orEmpty() }
        val cost = CrochetEngine.calculateCost(recipeModels, productNumber(labor) ?: 0.0, settings["hourly_rate"]?.toDoubleOrNull() ?: 15.0, settings["overhead_per_piece"]?.toDoubleOrNull() ?: 2.0)
        CrochetEngine.calculatePricing(cost, productNumber(margin), settings["default_margin_pct"]?.toDoubleOrNull() ?: 100.0, productNumber(price))
    }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (productId == null) "Nova peça" else "Editar peça") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 570.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome da peça / modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(category, { category = it }, label = { Text("Categoria") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Descrição") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(labor, { labor = it }, label = { Text("Mão de obra (min)") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(price, { price = it }, label = { Text("Preço manual (R$)") }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Automático") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(margin, { margin = it }, label = { Text("Margem (%)") }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Padrão") })
                    OutlinedTextField(stock, { stock = it }, label = { Text("Estoque pronto") }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Manter atual") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = inCatalog, onCheckedChange = { inCatalog = it })
                    Text("Exibir no catálogo", fontSize = 13.sp)
                }
                Text("Fotos da peça", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    photoIds.forEachIndexed { index, photoId ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            val imageData = remember(photoId) { photoId?.let { repository.photoBase64(it) } }
                            val bitmap = remember(imageData) { imageData?.let { data -> runCatching { val bytes = Base64.decode(data, Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() } }
                            if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Foto ${index + 1}", modifier = Modifier.size(76.dp))
                            else Box(Modifier.size(76.dp).background(Color(0xFFEEF6EE), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Text("Foto ${index + 1}", fontSize = 10.sp, color = ProductMuted) }
                            OutlinedButton(onClick = { photoSlot = index; photoPicker.launch("image/*") }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp)) { Text("Escolher", fontSize = 9.sp) }
                            if (photoId != null) TextButton(onClick = { photoIds[index] = null }, contentPadding = PaddingValues(0.dp)) { Text("Remover", fontSize = 9.sp) }
                        }
                    }
                }
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Receita de materiais", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(enabled = materials.isNotEmpty(), onClick = {
                        val m = materials.first()
                        val unit = m["unit"].orEmpty()
                        recipe.add(RecipeDraft(m["id"].orEmpty(), "1", unit))
                    }) { Icon(Icons.Default.Add, null); Text("Adicionar") }
                }
                if (materials.isEmpty()) Text("Cadastre materiais no Estoque para montar uma receita.", color = ProductMuted, fontSize = 12.sp)
                recipe.forEachIndexed { index, line ->
                    Column(Modifier.fillMaxWidth().background(Color(0xFFF4FBF3), RoundedCornerShape(10.dp)).padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        var expanded by remember(index) { mutableStateOf(false) }
                        val selectedName = materials.firstOrNull { it["id"] == line.materialId }?.get("name").orEmpty().ifBlank { "Selecione material" }
                        Box {
                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedName, modifier = Modifier.weight(1f)); Text("⌄") }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                materials.forEach { m -> DropdownMenuItem(text = { Text(m["name"].orEmpty()) }, onClick = {
                                    recipe[index] = line.copy(materialId = m["id"].orEmpty(), unit = m["unit"].orEmpty())
                                    expanded = false
                                }) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedTextField(line.quantity, { recipe[index] = line.copy(quantity = it) }, label = { Text("Quantidade") }, modifier = Modifier.weight(1f), singleLine = true)
                            var unitExpanded by remember(index) { mutableStateOf(false) }
                            val baseUnit = materials.firstOrNull { it["id"] == line.materialId }?.get("unit").orEmpty()
                            val units = if (baseUnit == "un") listOf("un") else listOf("kg", "m")
                            Box {
                                OutlinedButton(onClick = { unitExpanded = true }) { Text(line.unit); Text("⌄") }
                                DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                                    units.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { recipe[index] = line.copy(unit = u); unitExpanded = false }) }
                                }
                            }
                            IconButton(onClick = { recipe.removeAt(index) }) { Icon(Icons.Default.DeleteOutline, "Remover material") }
                        }
                    }
                }
                preview?.let {
                    Column(Modifier.fillMaxWidth().background(Color(0xFFEEF6EE), RoundedCornerShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Prévia do custo e preço", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Materiais ${productMoney(it.cost.materials)} · Mão de obra ${productMoney(it.cost.labor)} · Rateio ${productMoney(it.cost.overhead)}", color = ProductMuted, fontSize = 11.sp)
                        Text("Custo total: ${productMoney(it.cost.total)}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Preço sugerido: ${productMoney(it.suggestedPrice)} · Preço final: ${productMoney(it.finalPrice)}", color = ProductGreen, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = {
            try {
                val models = recipe.map { line ->
                    val m = materials.firstOrNull { it["id"] == line.materialId } ?: throw IllegalArgumentException("Selecione o material de cada linha.")
                    val q = productNumber(line.quantity) ?: throw IllegalArgumentException("Informe a quantidade da receita.")
                    val material = CrochetEngine.Material(m["id"]!!.toLong(), m["name"].orEmpty(), m["unit"].orEmpty(), m["meters_per_kg"]?.toDoubleOrNull() ?: 0.0, m["stock"]?.toDoubleOrNull() ?: 0.0, m["min_stock"]?.toDoubleOrNull() ?: 0.0, m["avg_cost"]?.toDoubleOrNull() ?: 0.0)
                    CrochetEngine.RecipeLine(material, q, line.unit)
                }
                val savedId = repository.saveProduct(savedProductId, name, description, category, productNumber(labor) ?: 0.0, productNumber(price), productNumber(margin), productNumber(stock), inCatalog, models)
                savedProductId = savedId
                photoIds.forEachIndexed { index, photoId -> repository.setProductPhoto(savedId, index + 1, photoId) }
                onSaved()
            } catch (e: Exception) { error = e.message ?: "Não foi possível salvar a peça." }
        }) { Text("Salvar peça") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

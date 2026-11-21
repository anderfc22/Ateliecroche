package com.lelecroche.app

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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
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

private val OrderGreen = Color(0xFF42634C)
private val OrderText = Color(0xFF161D19)
private val OrderMuted = Color(0xFF424842)
private val OrderCard = Color.White

@Composable
fun OrdersScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase(context.applicationContext) }
    val repository = remember { CrochetRepository(db) }
    var refresh by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<Long?>(null) }
    var filter by remember { mutableStateOf("todas") }
    val orders = remember(refresh, filter) {
        val where = when (filter) {
            "abertas" -> "WHERE o.status='aberto'"
            "solicitados" -> "WHERE o.status='solicitado'"
            "concluidas" -> "WHERE o.status IN ('entregue','cancelado')"
            else -> ""
        }
        db.rows("SELECT o.id,o.kind,o.status,o.due_date,o.total,o.created_at,COALESCE(c.name,'Sem cliente') customer,(SELECT COUNT(*) FROM order_items i WHERE i.order_id=o.id) item_count FROM orders o LEFT JOIN customers c ON c.id=o.customer_id $where ORDER BY o.id DESC")
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Encomendas", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = OrderText)
                Text("${orders.size} pedido(s)", color = OrderMuted, fontSize = 12.sp)
            }
            Button(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Novo")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("todas" to "Todas", "abertas" to "Abertas", "solicitados" to "Solicitados", "concluidas" to "Concluídas").forEach { (value, label) ->
                FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (orders.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = OrderCard)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ReceiptLong, null, tint = OrderGreen, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Nenhuma encomenda por aqui", fontWeight = FontWeight.SemiBold)
                    Text("Toque em Novo para registrar um pedido.", color = OrderMuted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(orders, key = { it["id"].orEmpty() }) { order ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedOrder = order["id"]?.toLongOrNull() },
                        colors = CardDefaults.cardColors(containerColor = OrderCard),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text((if (order["kind"] == "encomenda") "ENC-" else "VND-") + order["id"], fontWeight = FontWeight.Bold, color = OrderGreen)
                                Spacer(Modifier.weight(1f))
                                StatusPill(order["status"].orEmpty())
                            }
                            Text(order["customer"].orEmpty(), fontWeight = FontWeight.SemiBold, color = OrderText)
                            Text("${order["item_count"]} item(ns) · ${order["due_date"].orEmpty().ifBlank { "sem prazo" }}", color = OrderMuted, fontSize = 12.sp)
                            Text(money(order["total"]?.toDoubleOrNull() ?: 0.0), color = OrderGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
    if (showCreate) {
        NewOrderDialog(db, repository, onDismiss = { showCreate = false }, onSaved = { showCreate = false; refresh++ })
    }
    selectedOrder?.let { id ->
        OrderDetailDialog(db, repository, id, onDismiss = { selectedOrder = null }, onChanged = { selectedOrder = null; refresh++ })
    }
}

@Composable
private fun StatusPill(status: String) {
    val label = when (status) {
        "aberto" -> "Em aberto"
        "solicitado" -> "Solicitado"
        "entregue" -> "Entregue"
        "cancelado" -> "Cancelado"
        else -> status
    }
    val bg = when (status) { "solicitado" -> Color(0xFFFFE9D8); "cancelado" -> Color(0xFFFFE4E1); "entregue" -> Color(0xFFE5F1E5); else -> Color(0xFFEEF6EE) }
    Text(label, modifier = Modifier.background(bg, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 10.sp, color = OrderText)
}

private fun money(value: Double): String = "R$ " + String.format(Locale("pt", "BR"), "%.2f", value)
private fun decimal(value: String): Double? = value.trim().let { raw -> if (raw.contains(",")) raw.replace(".", "").replace(",", ".").toDoubleOrNull() else raw.toDoubleOrNull() }

@Composable
private fun NewOrderDialog(db: AppDatabase, repository: CrochetRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val products = remember { db.rows("SELECT id,name,stock FROM products WHERE active=1 ORDER BY name") }
    val customers = remember { db.rows("SELECT id,name,city FROM customers ORDER BY name") }
    val lines = remember { mutableStateListOf<CrochetRepository.OrderLineInput>() }
    var selectedProduct by remember { mutableStateOf(products.firstOrNull()?.get("id").orEmpty()) }
    var quantity by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    var itemNote by remember { mutableStateOf("") }
    var customerId by remember { mutableStateOf("") }
    var newCustomerName by remember { mutableStateOf("") }
    var newCustomerPhone by remember { mutableStateOf("") }
    var newCustomerCity by remember { mutableStateOf("") }
    var savedCustomerId by remember { mutableStateOf<Long?>(null) }
    var fullPaid by remember { mutableStateOf(false) }
    var customerExpanded by remember { mutableStateOf(false) }
    var productExpanded by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf("encomenda") }
    var channel by remember { mutableStateOf("WhatsApp") }
    var dueDate by remember { mutableStateOf("") }
    var deposit by remember { mutableStateOf("0") }
    var depositMethod by remember { mutableStateOf("Pix") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun linePrice(line: CrochetRepository.OrderLineInput) = line.unitPrice ?: runCatching { repository.previewProductPricing(line.productId).finalPrice }.getOrDefault(0.0)
    val total = lines.sumOf { it.quantity * linePrice(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo pedido") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Tipo de pedido", fontSize = 12.sp, color = OrderMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == "encomenda", onClick = { kind = "encomenda" }, label = { Text("Encomenda") })
                    FilterChip(selected = kind == "pronta", onClick = { kind = "pronta" }, label = { Text("Pronta-entrega") })
                }
                Box {
                    OutlinedTextField(
                        value = when (customerId) { "__new" -> "+ Novo cliente"; "" -> "Sem cliente"; else -> customers.firstOrNull { it["id"] == customerId }?.get("name").orEmpty() },
                        onValueChange = {}, readOnly = true, label = { Text("Cliente") }, modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.matchParentSize().clickable { customerExpanded = true })
                    DropdownMenu(expanded = customerExpanded, onDismissRequest = { customerExpanded = false }) {
                        DropdownMenuItem(text = { Text("Sem cliente") }, onClick = { customerId = ""; customerExpanded = false })
                        customers.forEach { c -> DropdownMenuItem(text = { Text(c["name"].orEmpty() + if (c["city"].orEmpty().isNotBlank()) " · ${c["city"]}" else "") }, onClick = { customerId = c["id"].orEmpty(); customerExpanded = false }) }
                        DropdownMenuItem(text = { Text("+ Novo cliente…") }, onClick = { customerId = "__new"; customerExpanded = false })
                    }
                }
                if (customerId == "__new") {
                    OutlinedTextField(newCustomerName, { newCustomerName = it }, label = { Text("Nome do novo cliente") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(newCustomerPhone, { newCustomerPhone = it }, label = { Text("Telefone / WhatsApp") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(newCustomerCity, { newCustomerCity = it }, label = { Text("Cidade") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedTextField(value = products.firstOrNull { it["id"] == selectedProduct }?.get("name").orEmpty(), onValueChange = {}, readOnly = true, label = { Text("Peça") }, modifier = Modifier.fillMaxWidth())
                        Box(Modifier.matchParentSize().clickable { productExpanded = true })
                        DropdownMenu(expanded = productExpanded, onDismissRequest = { productExpanded = false }) {
                            products.forEach { p -> DropdownMenuItem(text = { Text(p["name"].orEmpty()) }, onClick = { selectedProduct = p["id"].orEmpty(); productExpanded = false }) }
                        }
                    }
                    OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Qtd.") }, modifier = Modifier.width(90.dp), singleLine = true)
                }
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Preço unitário (opcional)") }, placeholder = { Text("Preço automático") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = itemNote, onValueChange = { itemNote = it }, label = { Text("Observação do item") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = {
                    val pid = selectedProduct.toLongOrNull()
                    val qty = decimal(quantity)
                    if (pid == null || qty == null || qty <= 0) error = "Selecione uma peça e informe uma quantidade válida."
                    else {
                        val customPrice = if (price.isBlank()) null else decimal(price) ?: throw IllegalArgumentException("Preço unitário inválido.")
                        if (customPrice != null && customPrice < 0) throw IllegalArgumentException("Preço unitário não pode ser negativo.")
                        lines.add(CrochetRepository.OrderLineInput(pid, qty, customPrice, itemNote))
                        quantity = "1"; price = ""; itemNote = ""; error = null
                    }
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("Adicionar peça ao pedido") }
                lines.forEachIndexed { index, line ->
                    val name = products.firstOrNull { it["id"] == line.productId.toString() }?.get("name").orEmpty()
                    Row(Modifier.fillMaxWidth().background(Color(0xFFF4FBF3), RoundedCornerShape(10.dp)).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${line.quantity} un · ${money(linePrice(line))}${if (line.unitPrice == null) " (automático)" else ""}", color = OrderMuted, fontSize = 11.sp)
                            if (line.note.isNotBlank()) Text(line.note, color = OrderMuted, fontSize = 10.sp)
                        }
                        IconButton(onClick = { lines.removeAt(index) }) { Icon(Icons.Default.Close, "Remover") }
                    }
                }
                if (lines.isNotEmpty()) Text("Total parcial: ${money(total)}", fontWeight = FontWeight.Bold, color = OrderGreen)
                if (kind == "encomenda") {
                    OutlinedTextField(value = channel, onValueChange = { channel = it }, label = { Text("Canal de venda") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Prazo (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fullPaid, onCheckedChange = { fullPaid = it })
                    Text("Cliente já pagou o valor total", fontSize = 12.sp)
                }
                if (!fullPaid) OutlinedTextField(value = deposit, onValueChange = { deposit = it }, label = { Text("Sinal recebido (R$)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = depositMethod, onValueChange = { depositMethod = it }, label = { Text("Forma do pagamento") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Observações") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = lines.isNotEmpty(), onClick = {
                try {
                    val customer = if (customerId == "__new") {
                        require(newCustomerName.isNotBlank()) { "Informe o nome do novo cliente." }
                        repository.saveCustomer(savedCustomerId, newCustomerName, newCustomerPhone, newCustomerCity).also { savedCustomerId = it }
                    } else customerId.toLongOrNull()
                    val paidNow = if (fullPaid) total else if (deposit.isBlank()) 0.0 else decimal(deposit) ?: throw IllegalArgumentException("Valor do sinal inválido.")
                    repository.createOrder(customer, lines.toList(), kind, channel, if (kind == "encomenda") dueDate else "", notes, paidNow, depositMethod)
                    onSaved()
                } catch (e: Exception) { error = e.message ?: "Não foi possível salvar o pedido." }
            }) { Text("Salvar pedido") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun OrderDetailDialog(db: AppDatabase, repository: CrochetRepository, orderId: Long, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    var showPayment by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val order = remember(orderId, refresh) { db.rows("SELECT o.*,COALESCE(c.name,'Sem cliente') customer FROM orders o LEFT JOIN customers c ON c.id=o.customer_id WHERE o.id=?", arrayOf(orderId.toString())).firstOrNull() }
    val items = remember(orderId, refresh) { db.rows("SELECT i.*,p.name product_name,p.stock product_stock FROM order_items i JOIN products p ON p.id=i.product_id WHERE i.order_id=? ORDER BY i.id", arrayOf(orderId.toString())) }
    val payments = remember(orderId, refresh) { db.rows("SELECT * FROM payments WHERE order_id=? ORDER BY id", arrayOf(orderId.toString())) }
    val production = remember(orderId, refresh) { db.rows("SELECT po.*,p.name product_name FROM production_orders po JOIN products p ON p.id=po.product_id WHERE po.order_id=? ORDER BY po.id", arrayOf(orderId.toString())) }
    val blocked = remember(orderId, refresh) { repository.orderMaterialShortages(orderId) }
    if (order == null) return
    val total = order["total"]?.toDoubleOrNull() ?: 0.0
    val paid = payments.sumOf { it["amount"]?.toDoubleOrNull() ?: 0.0 }
    val activeProduction = production.filter { it["status"] != "cancelada" }
    val progress = CrochetEngine.orderProgress(activeProduction.map { (it["qty"]?.toDoubleOrNull() ?: 0.0) to it["status"].orEmpty() }, order["status"].orEmpty())
    val phase = when (order["status"]) {
        "entregue", "cancelado", "solicitado" -> order["status"].orEmpty()
        else -> when {
            activeProduction.isNotEmpty() && activeProduction.all { it["status"] == "pronta" } -> "pronta"
            activeProduction.any { it["status"] != "fila" } -> "produção"
            activeProduction.isNotEmpty() -> "aguardando"
            items.isNotEmpty() && items.all { (it["product_stock"]?.toDoubleOrNull() ?: 0.0) + CrochetEngine.EPSILON >= (it["qty"]?.toDoubleOrNull() ?: 0.0) } -> "pronta"
            else -> "aguardando"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (order["kind"] == "encomenda") "ENC-" else "VND-") + orderId) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(order["customer"].orEmpty(), fontWeight = FontWeight.SemiBold)
                StatusPill(order["status"].orEmpty())
                Text("Fase: ${phase.replaceFirstChar { it.uppercase() }}", color = OrderMuted, fontSize = 11.sp)
                if (order["due_date"].orEmpty().isNotBlank()) Text("Prazo: ${order["due_date"]}", color = OrderMuted, fontSize = 12.sp)
                if (production.isNotEmpty()) Text("Progresso da produção: $progress%", color = OrderGreen, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                if (order["stage_note"].orEmpty().isNotBlank()) Text(order["stage_note"].orEmpty(), color = OrderMuted, fontSize = 12.sp)
                Text("Itens", fontWeight = FontWeight.Bold)
                items.forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { Text("${line["qty"]}× ${line["product_name"]}", fontSize = 13.sp); if (line["note"].orEmpty().isNotBlank()) Text(line["note"].orEmpty(), color = OrderMuted, fontSize = 10.sp) }
                        Text(money((line["qty"]?.toDoubleOrNull() ?: 0.0) * (line["unit_price"]?.toDoubleOrNull() ?: 0.0)), fontSize = 13.sp)
                    }
                }
                Divider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total"); Text(money(total), fontWeight = FontWeight.Bold) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Pago"); Text(money(paid)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Saldo"); Text(money(total - paid), color = OrderGreen, fontWeight = FontWeight.Bold) }
                if (production.isNotEmpty()) {
                    Divider()
                    Text("Produção", fontWeight = FontWeight.Bold)
                    production.forEach { p -> Text("${p["qty"]}× ${p["product_name"]} · ${p["status"]}", fontSize = 12.sp, color = OrderMuted) }
                }
                if (blocked.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().background(Color(0xFFFFF1E8), RoundedCornerShape(9.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Produção aguardando materiais", color = Color(0xFF8A4C24), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        blocked.forEach { shortage -> Text("${shortage.material.name}: faltam ${shortage.shortage} ${shortage.material.unit}", color = Color(0xFF8A4C24), fontSize = 11.sp) }
                    }
                }
                if (payments.isNotEmpty()) {
                    Divider()
                    Text("Pagamentos", fontWeight = FontWeight.Bold)
                    payments.forEach { p -> Text("${money(p["amount"]?.toDoubleOrNull() ?: 0.0)} · ${p["method"]} · ${p["created_at"]}", fontSize = 11.sp, color = OrderMuted) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Divider()
                if (order["status"] == "solicitado") {
                    Button(onClick = { try { repository.confirmCatalogRequest(orderId); refresh++ } catch (e: Exception) { error = e.message } }, modifier = Modifier.fillMaxWidth()) { Text("Confirmar solicitação") }
                    OutlinedButton(onClick = { try { repository.rejectCatalogRequest(orderId); onChanged() } catch (e: Exception) { error = e.message } }, modifier = Modifier.fillMaxWidth()) { Text("Recusar") }
                }
                if (order["status"] != "solicitado") OutlinedButton(onClick = { showPayment = true }, modifier = Modifier.fillMaxWidth()) { Text("Registrar pagamento / estorno") }
                if (order["status"] == "aberto") {
                    OutlinedButton(onClick = { showEdit = true }, modifier = Modifier.fillMaxWidth()) { Text("Editar detalhes") }
                    if (production.any { it["status"] in listOf("fila", "andamento", "acabamento") }) {
                        OutlinedButton(onClick = { try { repository.advanceOrder(orderId); refresh++ } catch (e: Exception) { error = e.message } }, modifier = Modifier.fillMaxWidth()) { Text("Avançar produção") }
                    }
                    Button(onClick = { try { repository.setOrderStatus(orderId, "entregue"); onChanged() } catch (e: Exception) { error = e.message } }, modifier = Modifier.fillMaxWidth()) { Text("Marcar como entregue") }
                }
                if (order["status"] != "cancelado") TextButton(onClick = { try { repository.setOrderStatus(orderId, "cancelado"); onChanged() } catch (e: Exception) { error = e.message } }) { Text("Cancelar pedido", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
    if (showPayment) PaymentDialog(repository, orderId, total - paid, paid, onDismiss = { showPayment = false }, onSaved = { showPayment = false; refresh++ })
    if (showEdit) OrderEditDialog(repository, order, onDismiss = { showEdit = false }, onSaved = { showEdit = false; refresh++ })
}

@Composable
private fun OrderEditDialog(repository: CrochetRepository, order: Map<String, String>, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val id = order["id"]!!.toLong()
    var dueDate by remember { mutableStateOf(order["due_date"].orEmpty()) }
    var stageNote by remember { mutableStateOf(order["stage_note"].orEmpty()) }
    var notes by remember { mutableStateOf(order["notes"].orEmpty()) }
    var channel by remember { mutableStateOf(order["channel"].orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editar pedido") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Prazo (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(channel, { channel = it }, label = { Text("Canal") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(stageNote, { stageNote = it }, label = { Text("Observação da etapa") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("Observações do pedido") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }, confirmButton = { TextButton(onClick = {
        try { repository.updateOrder(id, dueDate, stageNote, notes, channel); onSaved() }
        catch (e: Exception) { error = e.message ?: "Não foi possível atualizar o pedido." }
    }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun PaymentDialog(repository: CrochetRepository, orderId: Long, balance: Double, paid: Double, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Pix") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar pagamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saldo: ${money(balance)}", color = OrderMuted, fontSize = 12.sp)
                OutlinedTextField(amount, { amount = it }, label = { Text("Valor (negativo para estorno)") }, singleLine = true)
                OutlinedTextField(method, { method = it }, label = { Text("Forma de pagamento") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = {
            try { val value = decimal(amount) ?: throw IllegalArgumentException("Informe um valor válido"); repository.addPayment(orderId, value, method); onSaved() }
            catch (e: Exception) { error = e.message ?: "Pagamento inválido" }
        }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

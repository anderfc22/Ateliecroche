package com.lelecroche.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

private fun moreNumber(value: String) = value.trim().let { raw -> if (raw.contains(",")) raw.replace(".", "").replace(",", ".").toDoubleOrNull() else raw.toDoubleOrNull() }
private fun moreMoney(value: Double) = "R$ " + String.format(Locale("pt", "BR"), "%.2f", value)

@Composable
fun ReportsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase(context.applicationContext) }
    val repo = remember { CrochetRepository(db) }
    val sold = remember {
        db.rows("SELECT p.name,SUM(i.qty) qty,SUM(i.qty*i.unit_price) revenue,SUM(i.qty*i.unit_cost) cost FROM order_items i JOIN orders o ON o.id=i.order_id JOIN products p ON p.id=i.product_id WHERE o.status NOT IN ('cancelado','solicitado') GROUP BY i.product_id,p.name ORDER BY qty DESC")
    }
    val products = remember { db.rows("SELECT id,name,stock FROM products WHERE active=1 ORDER BY name") }
    val margins = remember(products) { products.mapNotNull { p ->
        val pricing = runCatching { repo.previewProductPricing(p["id"]!!.toLong()) }.getOrNull() ?: return@mapNotNull null
        Triple(p, pricing, p["stock"]?.toDoubleOrNull() ?: 0.0)
    } }
    val stockValue = db.scalar("SELECT COALESCE(SUM(stock*avg_cost),0) FROM materials WHERE active=1")
    val finishedValue = margins.sumOf { row -> row.second.cost.total * row.third }
    val revenue = sold.sumOf { it["revenue"]?.toDoubleOrNull() ?: 0.0 }
    val profit = sold.sumOf { (it["revenue"]?.toDoubleOrNull() ?: 0.0) - (it["cost"]?.toDoubleOrNull() ?: 0.0) }
    val receivable = db.scalar("SELECT COALESCE(SUM(MAX(o.total-COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.order_id=o.id),0),0)),0) FROM orders o WHERE o.status NOT IN ('cancelado','solicitado')")
    val pieces = db.scalar("SELECT COALESCE(SUM(po.qty),0) FROM production_orders po WHERE po.status='pronta'")
    val hours = db.scalar("SELECT COALESCE(SUM(po.qty*p.labor_minutes),0)/60.0 FROM production_orders po JOIN products p ON p.id=po.product_id WHERE po.status='pronta'")
    val low = db.scalar("SELECT COUNT(*) FROM materials WHERE active=1 AND min_stock>0 AND stock<=min_stock").toInt()
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Text("Relatórios", fontSize = 24.sp, fontWeight = FontWeight.SemiBold) }
        item {
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(13.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ReportRow("Receita de vendas", moreMoney(revenue))
                ReportRow("Lucro estimado das vendas", moreMoney(profit), true)
                ReportRow("A receber", moreMoney(receivable))
                ReportRow("Materiais em estoque", moreMoney(stockValue))
                ReportRow("Peças prontas (a custo)", moreMoney(finishedValue))
                ReportRow("Materiais com estoque baixo", low.toString())
            }
        }
        item { Text("Mais vendidas", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
        if (sold.isEmpty()) item { Text("Ainda não há vendas concluídas.", color = Color(0xFF424842), fontSize = 12.sp) }
        items(sold) { p ->
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(p["name"].orEmpty(), fontWeight = FontWeight.Medium, fontSize = 13.sp); Text("${p["qty"]} unidade(s)", color = Color(0xFF424842), fontSize = 10.sp) }
                Column(horizontalAlignment = Alignment.End) { Text(moreMoney(p["revenue"]?.toDoubleOrNull() ?: 0.0), fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text("Lucro ${moreMoney((p["revenue"]?.toDoubleOrNull() ?: 0.0) - (p["cost"]?.toDoubleOrNull() ?: 0.0))}", color = Color(0xFF42634C), fontSize = 10.sp) }
            }
        }
        item { Text("Margem por peça", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
        items(margins) { (p, pricing, stock) ->
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(p["name"].orEmpty(), fontWeight = FontWeight.Medium, fontSize = 13.sp); Text("Custo ${moreMoney(pricing.cost.total)} · Preço ${moreMoney(pricing.finalPrice)} · Estoque $stock", color = Color(0xFF424842), fontSize = 10.sp) }
                Text(String.format(Locale("pt","BR"), "%.1f%%", pricing.realMarginPercent), color = if (pricing.realMarginPercent < 30) MaterialTheme.colorScheme.error else Color(0xFF42634C), fontWeight = FontWeight.Bold)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(Color(0xFFEEF6EE), RoundedCornerShape(13.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Produtividade", fontWeight = FontWeight.SemiBold)
                ReportRow("Peças concluídas", String.format(Locale("pt","BR"), "%.0f", pieces))
                ReportRow("Horas de trabalho", String.format(Locale("pt","BR"), "%.1f h", hours))
                ReportRow("Peças por hora", if (hours > 0) String.format(Locale("pt","BR"), "%.2f", pieces / hours) else "0")
                ReportRow("Faturamento por hora", if (hours > 0) moreMoney(revenue / hours) else moreMoney(0.0))
            }
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF424842), fontSize = 12.sp)
        Text(value, color = if (highlight) Color(0xFF42634C) else Color(0xFF161D19), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
fun AtelierSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase(context.applicationContext) }
    val repo = remember { CrochetRepository(db) }
    val settings = remember { db.rows("SELECT skey,svalue FROM settings").associate { it["skey"].orEmpty() to it["svalue"].orEmpty() } }
    var atelier by remember { mutableStateOf(settings["atelier_name"] ?: "Meu Ateliê") }
    var hourly by remember { mutableStateOf(settings["hourly_rate"] ?: "15") }
    var overhead by remember { mutableStateOf(settings["overhead_per_piece"] ?: "2") }
    var margin by remember { mutableStateOf(settings["default_margin_pct"] ?: "100") }
    var goal by remember { mutableStateOf(settings["monthly_goal"] ?: "0") }
    var whatsapp by remember { mutableStateOf(settings["whatsapp"].orEmpty()) }
    var instagram by remember { mutableStateOf(settings["instagram"].orEmpty()) }
    var catalogOn by remember { mutableStateOf(settings["catalog_on"] == "1") }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Preço, meta e ateliê", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item { OutlinedTextField(atelier, { atelier = it }, label = { Text("Nome do ateliê") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(hourly, { hourly = it }, label = { Text("Valor da hora (R$)") }, modifier = Modifier.weight(1f), singleLine = true); OutlinedTextField(overhead, { overhead = it }, label = { Text("Rateio por peça (R$)") }, modifier = Modifier.weight(1f), singleLine = true) } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(margin, { margin = it }, label = { Text("Margem padrão (%)") }, modifier = Modifier.weight(1f), singleLine = true); OutlinedTextField(goal, { goal = it }, label = { Text("Meta mensal (R$)") }, modifier = Modifier.weight(1f), singleLine = true) } }
            item { Text("O preço sugerido considera materiais + mão de obra + rateio, aplicando a margem configurada.", color = Color(0xFF424842), fontSize = 12.sp) }
            item { Divider() }
            item { Text("Contato e catálogo", fontWeight = FontWeight.SemiBold) }
            item { OutlinedTextField(whatsapp, { whatsapp = it }, label = { Text("WhatsApp com DDD") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(instagram, { instagram = it }, label = { Text("Instagram (@usuário)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(catalogOn, { catalogOn = it }); Text("Catálogo habilitado") } }
            item { error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) } }
            item { if (saved) Text("Configurações salvas neste aparelho.", color = Color(0xFF42634C), fontSize = 12.sp) }
            item { Button(onClick = {
                try {
                    val h = moreNumber(hourly) ?: 0.0
                    val o = moreNumber(overhead) ?: 0.0
                    val m = moreNumber(margin) ?: 0.0
                    val g = moreNumber(goal) ?: 0.0
                    repo.saveNumericSettings(h, o, m, g)
                    repo.saveAtelierSettings(atelier, whatsapp, instagram, catalogOn)
                    saved = true; error = null
                } catch (e: Exception) { error = e.message ?: "Não foi possível salvar as configurações."; saved = false }
            }, modifier = Modifier.fillMaxWidth()) { Text("Salvar configurações") } }
        }
    }
}

@Composable
fun CatalogSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase(context.applicationContext) }
    val repo = remember { CrochetRepository(db) }
    var refresh by remember { mutableIntStateOf(0) }
    var catalogOn by remember { mutableStateOf(db.rows("SELECT svalue FROM settings WHERE skey='catalog_on'").firstOrNull()?.get("svalue") == "1") }
    val atelierName = remember { db.rows("SELECT svalue FROM settings WHERE skey='atelier_name'").firstOrNull()?.get("svalue") ?: "Meu Ateliê" }
    var whatsapp by remember { mutableStateOf(db.rows("SELECT svalue FROM settings WHERE skey='whatsapp'").firstOrNull()?.get("svalue").orEmpty()) }
    var instagram by remember { mutableStateOf(db.rows("SELECT svalue FROM settings WHERE skey='instagram'").firstOrNull()?.get("svalue").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val products = remember(refresh) { db.rows("SELECT id,name,category,price,in_catalog FROM products WHERE active=1 ORDER BY name") }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Catálogo para clientes", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("Escolha quais peças ficam visíveis no catálogo do site.", color = Color(0xFF424842), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(catalogOn, { catalogOn = it }); Text("Catálogo habilitado") }
        OutlinedTextField(whatsapp, { whatsapp = it }, label = { Text("WhatsApp do ateliê") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(instagram, { instagram = it }, label = { Text("Instagram") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Button(onClick = { try { repo.saveAtelierSettings(atelierName, whatsapp, instagram, catalogOn); error = null } catch (e: Exception) { error = e.message } }, modifier = Modifier.fillMaxWidth()) { Text("Salvar catálogo e contato") }
        Spacer(Modifier.height(10.dp))
        Text("Peças no catálogo", fontWeight = FontWeight.SemiBold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(products, key = { it["id"].orEmpty() }) { p ->
                Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(p["name"].orEmpty(), fontWeight = FontWeight.Medium, fontSize = 12.sp); Text(p["category"].orEmpty(), color = Color(0xFF424842), fontSize = 10.sp) }
                    Switch(checked = p["in_catalog"] != "0", onCheckedChange = { enabled -> try { repo.setProductCatalog(p["id"]!!.toLong(), enabled); refresh++ } catch (e: Exception) { error = e.message } })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("A publicação do catálogo e o recebimento online dos pedidos serão ativados na etapa final de conexão com o servidor.", color = Color(0xFF424842), fontSize = 11.sp)
    }
}

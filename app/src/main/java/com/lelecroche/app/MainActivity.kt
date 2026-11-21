package com.lelecroche.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SurfaceGreen = Color(0xFFF4FBF3)
private val PrimaryGreen = Color(0xFF42634C)
private val CardWhite = Color(0xFFFFFFFF)
private val TextDark = Color(0xFF161D19)
private val Muted = Color(0xFF424842)
private val Terracotta = Color(0xFF7F5534)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LeleCrocheApp() }
    }
}

private data class Destination(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun LeleCrocheApp() {
    val destinations = listOf(
        Destination("Início", Icons.Default.Home),
        Destination("Encomendas", Icons.Default.ReceiptLong),
        Destination("Peças", Icons.Default.Cottage),
        Destination("Estoque", Icons.Default.Inventory2),
        Destination("Financeiro", Icons.Default.AccountBalanceWallet),
        Destination("Mais", Icons.Default.GridView)
    )
    var selected by remember { mutableIntStateOf(0) }
    var moreSection by remember { mutableStateOf<String?>(null) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PrimaryGreen,
            onPrimary = Color.White,
            background = SurfaceGreen,
            surface = CardWhite,
            onSurface = TextDark,
            secondary = Terracotta
        )
    ) {
        Scaffold(
            containerColor = SurfaceGreen,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().background(SurfaceGreen).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Lelê Crochê", color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(if (selected == 5 && moreSection != null) moreSection!! else destinations[selected].title, color = Muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { if (selected == 5 && moreSection != null) moreSection = null else { selected = 5; moreSection = null } }) { Icon(if (selected == 5 && moreSection != null) Icons.Default.ArrowBack else Icons.Default.Dashboard, if (selected == 5 && moreSection != null) "Voltar" else "Menu") }
                    Box(Modifier.size(34.dp).background(Color(0xFFC6ECCE), CircleShape), contentAlignment = Alignment.Center) {
                        Text("L", color = PrimaryGreen, fontWeight = FontWeight.Bold)
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = SurfaceGreen) {
                    destinations.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index; moreSection = null },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title, fontSize = 9.sp) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        ) { padding ->
            when (selected) {
                0 -> HomeScreen(Modifier.padding(padding))
                1 -> OrdersScreen(Modifier.padding(padding))
                2 -> ProductsScreen(Modifier.padding(padding))
                3 -> MaterialsScreen(Modifier.padding(padding))
                4 -> FinanceScreen(Modifier.padding(padding))
                else -> if (moreSection == null) MoreScreen(Modifier.padding(padding)) { moreSection = it }
                    else if (moreSection == "Clientes") NativeCustomersScreen(Modifier.padding(padding)) { moreSection = null }
                    else if (moreSection == "Produção") ProductionScreen(Modifier.padding(padding))
                    else if (moreSection == "Despesas") FinanceScreen(Modifier.padding(padding))
                    else if (moreSection == "Lista de compras") MaterialsScreen(Modifier.padding(padding))
                    else if (moreSection == "Relatórios") ReportsScreen(Modifier.padding(padding))
                    else if (moreSection == "Catálogo para clientes") CatalogSettingsScreen(Modifier.padding(padding))
                    else if (moreSection == "Preço, meta e ateliê") AtelierSettingsScreen(Modifier.padding(padding))
                    else if (moreSection == "Backup e dados") BackupScreen(Modifier.padding(padding))
                    else DataScreen(AppDatabase(androidx.compose.ui.platform.LocalContext.current), moreSection!!, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase(context.applicationContext) }
    val openOrders = db.scalar("SELECT COUNT(*) FROM orders WHERE status='aberto'").toInt()
    val requests = db.scalar("SELECT COUNT(*) FROM orders WHERE status='solicitado'").toInt()
    val lowMaterials = db.scalar("SELECT COUNT(*) FROM materials WHERE active=1 AND min_stock>0 AND stock<=min_stock").toInt()
    val production = db.scalar("SELECT COUNT(*) FROM production_orders WHERE status IN ('fila','andamento','acabamento')").toInt()
    val readyPieces = db.scalar("SELECT COALESCE(SUM(stock),0) FROM products WHERE active=1")
    val receivable = db.scalar("SELECT COALESCE(SUM(MAX(o.total-COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.order_id=o.id),0),0)),0) FROM orders o WHERE o.status NOT IN ('cancelado','solicitado')")
    val cashIn = db.scalar("SELECT COALESCE(SUM(amount),0) FROM payments") + db.scalar("SELECT COALESCE(SUM(amount),0) FROM incomes")
    val cashOut = db.scalar("SELECT COALESCE(SUM(total_cost),0) FROM purchases") + db.scalar("SELECT COALESCE(SUM(amount),0) FROM expenses")
    val cash = cashIn - cashOut
    val monthKey = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
    val monthRevenue = db.scalar("SELECT COALESCE(SUM(amount),0) FROM payments WHERE substr(created_at,1,7)='$monthKey'") + db.scalar("SELECT COALESCE(SUM(amount),0) FROM incomes WHERE substr(created_at,1,7)='$monthKey'")
    val monthlyGoal = db.rows("SELECT svalue FROM settings WHERE skey='monthly_goal'").firstOrNull()?.get("svalue")?.toDoubleOrNull() ?: 0.0
    val goalPercent = if (monthlyGoal > 0) (monthRevenue / monthlyGoal * 100).toInt() else 0
    val dueWeek = db.scalar("SELECT COUNT(*) FROM orders WHERE status='aberto' AND due_date<>'' AND date(due_date)>=date('now','localtime') AND date(due_date)<=date('now','localtime','+7 day')").toInt()
    val overdue = db.scalar("SELECT COUNT(*) FROM orders WHERE status='aberto' AND due_date<>'' AND date(due_date)<date('now','localtime')").toInt()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(
                Modifier.fillMaxWidth().background(Color(0xFFEEF6EE), RoundedCornerShape(18.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("ATELIÊ ARTESANAL", color = PrimaryGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Seu ateliê, em ordem.", color = TextDark, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text("Acompanhe pedidos, materiais e produção em um só lugar.", color = Muted, fontSize = 13.sp)
            }
        }
        item { Text("Visão geral", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = TextDark) }
        item {
            Column(Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Faturamento do mês", color = Muted, fontSize = 12.sp)
                Text("R$ " + String.format(java.util.Locale("pt","BR"), "%.2f", monthRevenue), color = PrimaryGreen, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                if (monthlyGoal > 0) Text("$goalPercent% da meta de R$ " + String.format(java.util.Locale("pt","BR"), "%.2f", monthlyGoal), color = Muted, fontSize = 11.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Encomendas abertas", openOrders.toString(), "Em acompanhamento", Modifier.weight(1f))
                MetricCard("Estoque baixo", lowMaterials.toString(), "Materiais para repor", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("A receber", "R$ " + String.format(java.util.Locale("pt","BR"), "%.2f", receivable), "Saldo dos pedidos", Modifier.weight(1f))
                MetricCard("Em produção", production.toString(), "Ordens em andamento", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Peças prontas", String.format(java.util.Locale("pt","BR"), "%.0f", readyPieces), "Disponíveis no estoque", Modifier.weight(1f))
                MetricCard("Caixa acumulado", "R$ " + String.format(java.util.Locale("pt","BR"), "%.2f", cash), "Pagamentos + receitas − saídas", Modifier.weight(1f))
            }
        }
        if (requests > 0 || lowMaterials > 0 || dueWeek > 0 || overdue > 0) {
            item {
                Column(Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(14.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Atenção", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (requests > 0) Text("$requests pedido(s) do catálogo aguardando confirmação", color = Terracotta, fontSize = 13.sp)
                    if (lowMaterials > 0) Text("$lowMaterials material(is) com estoque baixo", color = Terracotta, fontSize = 13.sp)
                    if (dueWeek > 0) Text("$dueWeek encomenda(s) vencem nos próximos 7 dias", color = Terracotta, fontSize = 13.sp)
                    if (overdue > 0) Text("$overdue encomenda(s) atrasada(s)", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(14.dp)).padding(16.dp)) {
                Text("Resumo do ateliê", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text("$openOrders encomenda(s) abertas · $production ordem(ns) em produção · $readyPieces peça(s) prontas.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier.background(CardWhite, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Text(title, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Text(value, color = PrimaryGreen, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text(caption, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun ModuleScreen(modifier: Modifier, title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = PrimaryGreen, modifier = Modifier.size(30.dp))
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
        }
        Text(description, color = Muted, fontSize = 14.sp)
        Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Módulo preparado", fontWeight = FontWeight.SemiBold, color = PrimaryGreen)
                Spacer(Modifier.height(6.dp))
                Text("A interface e as operações deste módulo serão implementadas a partir das regras e telas do site original. Nenhum dado fictício será exibido.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MoreScreen(modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val entries = listOf("Clientes", "Produção", "Despesas", "Lista de compras", "Relatórios", "Catálogo para clientes", "Preço, meta e ateliê", "Backup e dados")
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Mais opções", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
        entries.forEach { entry ->
            Row(
                Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(12.dp)).clickable { onSelect(entry) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(entry, Modifier.weight(1f), color = TextDark, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
    }
}


@Composable
private fun CustomersScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember { AppDatabase(context.applicationContext) }
    var customers by remember { mutableStateOf(database.getCustomers()) }
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Customer?>(null) }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
            Column(Modifier.weight(1f)) {
                Text("Clientes", fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                Text("${customers.size} cliente(s) cadastrados neste aparelho", color = Muted, fontSize = 12.sp)
            }
            Button(onClick = { editing = null; showDialog = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Novo")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (customers.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = CardWhite)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PeopleAlt, null, tint = PrimaryGreen, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Nenhum cliente cadastrado", fontWeight = FontWeight.SemiBold)
                    Text("Toque em Novo para adicionar o primeiro cliente.", color = Muted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(customers) { customer ->
                    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(13.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(Color(0xFFE5F1E5), CircleShape), contentAlignment = Alignment.Center) {
                                Text(customer.name.take(1).uppercase(), color = PrimaryGreen, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(customer.name, fontWeight = FontWeight.SemiBold, color = TextDark)
                                if (customer.phone.isNotBlank()) Text(customer.phone, color = Muted, fontSize = 12.sp)
                                if (customer.notes.isNotBlank()) Text(customer.notes, color = Muted, fontSize = 11.sp, maxLines = 2)
                            }
                            IconButton(onClick = { editing = customer; showDialog = true }) {
                                Icon(Icons.Default.Edit, "Editar", tint = PrimaryGreen)
                            }
                            IconButton(onClick = {
                                database.deleteCustomer(customer.id)
                                customers = database.getCustomers()
                            }) {
                                Icon(Icons.Default.DeleteOutline, "Excluir", tint = Terracotta)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        var name by remember(editing) { mutableStateOf(editing?.name.orEmpty()) }
        var phone by remember(editing) { mutableStateOf(editing?.phone.orEmpty()) }
        var notes by remember(editing) { mutableStateOf(editing?.notes.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editing == null) "Novo cliente" else "Editar cliente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                    OutlinedTextField(phone, { phone = it }, label = { Text("Telefone") }, singleLine = true)
                    OutlinedTextField(notes, { notes = it }, label = { Text("Observações") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        database.saveCustomer(editing?.id, name, phone, notes)
                        customers = database.getCustomers()
                        showDialog = false
                    }
                ) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancelar") } }
        )
    }
}


@Composable
private fun DataScreen(db: AppDatabase, section: String, modifier: Modifier = Modifier) {
    var refresh by remember { mutableIntStateOf(0) }
    var show by remember { mutableStateOf(false) }
    val table = when(section) {
        "Pedidos" -> "orders"
        "Peças" -> "products"
        "Estoque" -> "materials"
        "Produção" -> "production_orders"
        "Despesas" -> "expenses"
        "Compras" -> "purchases"
        "Histórico de estoque" -> "stock_movements"
        else -> "customers"
    }
    val rows = remember(refresh, section) {
        when(section) {
            "Pedidos" -> db.rows("SELECT o.id,o.kind,o.status,o.due_date,o.total,COALESCE(c.name,'Sem cliente') AS person FROM orders o LEFT JOIN customers c ON c.id=o.customer_id ORDER BY o.id DESC")
            "Peças" -> db.rows("SELECT * FROM products ORDER BY name")
            "Estoque" -> db.rows("SELECT * FROM materials ORDER BY name")
            "Produção" -> db.rows("SELECT po.id,po.qty,po.status,p.name AS person FROM production_orders po JOIN products p ON p.id=po.product_id ORDER BY po.id DESC")
            "Despesas" -> db.rows("SELECT * FROM expenses ORDER BY id DESC")
            "Compras" -> db.rows("SELECT pu.id,pu.qty,pu.total_cost,m.name AS person FROM purchases pu JOIN materials m ON m.id=pu.material_id ORDER BY pu.id DESC")
            "Histórico de estoque" -> db.rows("SELECT sm.id,sm.qty,sm.reason,sm.created_at,m.name AS person FROM stock_movements sm JOIN materials m ON m.id=sm.material_id ORDER BY sm.id DESC")
            else -> emptyList()
        }
    }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(section, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                Text(rows.size.toString() + " registro(s)", color = Muted, fontSize = 12.sp)
            }
            if (section in listOf("Pedidos","Peças","Estoque","Despesas","Produção","Compras")) Button(onClick = { show = true }) { Icon(Icons.Default.Add,null); Text(" Novo") }
        }
        Spacer(Modifier.height(12.dp))
        if (section == "Financeiro") {
            val paid = db.scalar("SELECT COALESCE(SUM(amount),0) FROM payments")
            val costs = db.scalar("SELECT COALESCE(SUM(amount),0) FROM expenses")
            val open = db.scalar("SELECT COALESCE(SUM(total),0) FROM orders WHERE status!='cancelado'") - paid
            listOf("Recebido" to paid, "Despesas" to costs, "A receber" to open.coerceAtLeast(0.0), "Resultado" to paid-costs).forEach {
                Card(Modifier.fillMaxWidth().padding(bottom=9.dp), colors=CardDefaults.cardColors(containerColor=CardWhite)) {
                    Column(Modifier.padding(16.dp)) { Text(it.first,color=Muted,fontSize=12.sp); Text("R$ " + String.format(java.util.Locale("pt","BR"),"%.2f",it.second),fontSize=23.sp,color=PrimaryGreen,fontWeight=FontWeight.SemiBold) }
                }
            }
        } else if (rows.isEmpty()) {
            Card(colors=CardDefaults.cardColors(containerColor=CardWhite)) { Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) { Icon(Icons.Default.Inbox,null,tint=PrimaryGreen,modifier=Modifier.size(34.dp)); Text("Nenhum registro ainda",fontWeight=FontWeight.SemiBold); Text("Use Novo para adicionar informações.",color=Muted,fontSize=13.sp) } }
        } else {
            LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                items(rows) { row ->
                    Card(colors=CardDefaults.cardColors(containerColor=CardWhite),shape=RoundedCornerShape(13.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(row["name"] ?: row["description"] ?: row["person"] ?: ("Registro #" + (row["id"] ?: "")),fontWeight=FontWeight.SemiBold,color=TextDark)
                            Text(when(section) {
                                "Pedidos" -> (row["kind"] ?: "") + " • " + (row["status"] ?: "") + " • " + (row["due_date"] ?: "") + " • R$ " + (row["total"] ?: "0")
                                "Peças" -> "Preço: R$ " + (row["price"] ?: "0") + " • Estoque: " + (row["stock"] ?: "0")
                                "Estoque" -> (row["kind"] ?: "") + " • " + (row["color"] ?: "") + " • Saldo: " + (row["stock"] ?: "0") + " " + (row["unit"] ?: "")
                                "Produção" -> "Quantidade: " + (row["qty"] ?: "") + " • Etapa: " + (row["status"] ?: "")
                                "Despesas" -> (row["category"] ?: "") + " • R$ " + (row["amount"] ?: "0")
                                "Compras" -> "Quantidade: " + (row["qty"] ?: "") + " • Total: R$ " + (row["total_cost"] ?: "0")
                                else -> (row["reason"] ?: "") + " • " + (row["qty"] ?: "") + " • " + (row["created_at"] ?: "")
                            },color=Muted,fontSize=12.sp)
                        }
                    }
                }
            }
        }
    }
    if (show) {
        var name by remember { mutableStateOf("") }
        var extra by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        var qty by remember { mutableStateOf("0") }
        var unit by remember { mutableStateOf("un") }
        var customer by remember { mutableStateOf("") }
        var kind by remember { mutableStateOf("encomenda") }
        var due by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest={show=false},title={Text("Novo " + section.lowercase())},text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name,{name=it},label={Text(when(section){"Peças"->"Nome da peça";"Estoque"->"Nome do material";"Despesas"->"Descrição";else->"ID da peça"})},singleLine=true)
                if(section=="Peças") { OutlinedTextField(extra,{extra=it},label={Text("Descrição")},singleLine=true); OutlinedTextField(value,{value=it},label={Text("Preço de venda")},singleLine=true); OutlinedTextField(qty,{qty=it},label={Text("Estoque pronto")},singleLine=true) }
                if(section=="Estoque") { OutlinedTextField(extra,{extra=it},label={Text("Tipo / categoria")},singleLine=true); OutlinedTextField(unit,{unit=it},label={Text("Unidade (g, m, un)")},singleLine=true); OutlinedTextField(qty,{qty=it},label={Text("Saldo inicial")},singleLine=true); OutlinedTextField(value,{value=it},label={Text("Estoque mínimo")},singleLine=true) }
                if(section=="Despesas") { OutlinedTextField(value,{value=it},label={Text("Valor")},singleLine=true); OutlinedTextField(extra,{extra=it},label={Text("Categoria")},singleLine=true) }
                if(section=="Produção") { Text("Peças: " + db.rows("SELECT id,name FROM products").joinToString { (it["id"] ?: "") + " - " + (it["name"] ?: "") }); OutlinedTextField(name,{name=it},label={Text("ID da peça")},singleLine=true); OutlinedTextField(qty,{qty=it},label={Text("Quantidade")},singleLine=true) }
                if(section=="Pedidos") { Text("Peças: " + db.rows("SELECT id,name FROM products").joinToString { (it["id"] ?: "") + " - " + (it["name"] ?: "") }); Text("Clientes: " + db.rows("SELECT id,name FROM customers").joinToString { (it["id"] ?: "") + " - " + (it["name"] ?: "") }); OutlinedTextField(name,{name=it},label={Text("ID da peça")},singleLine=true); OutlinedTextField(customer,{customer=it},label={Text("ID do cliente (opcional)")},singleLine=true); OutlinedTextField(qty,{qty=it},label={Text("Quantidade")},singleLine=true); OutlinedTextField(value,{value=it},label={Text("Preço por unidade")},singleLine=true); OutlinedTextField(due,{due=it},label={Text("Prazo AAAA-MM-DD")},singleLine=true); Row { TextButton(onClick={kind="encomenda"}){Text("Encomenda")}; TextButton(onClick={kind="pronta"}){Text("Pronta entrega")} } }
                if(section=="Compras") { Text("Materiais: " + db.rows("SELECT id,name FROM materials").joinToString { (it["id"] ?: "") + " - " + (it["name"] ?: "") }); OutlinedTextField(name,{name=it},label={Text("ID do material")},singleLine=true); OutlinedTextField(qty,{qty=it},label={Text("Quantidade comprada")},singleLine=true); OutlinedTextField(value,{value=it},label={Text("Custo total")},singleLine=true); OutlinedTextField(extra,{extra=it},label={Text("Fornecedor")},singleLine=true) }
            }
        },confirmButton={TextButton(onClick={
            when(section) {
                "Peças" -> if(name.isNotBlank()) db.saveProduct(name,extra,"",value.toDoubleOrNull()?:0.0,qty.toDoubleOrNull()?:0.0,0.0)
                "Estoque" -> if(name.isNotBlank()) db.saveMaterial(name,extra,"","",unit,qty.toDoubleOrNull()?:0.0,value.toDoubleOrNull()?:0.0,0.0)
                "Despesas" -> if(name.isNotBlank() && (value.toDoubleOrNull()?:0.0)>0) db.saveExpense(name,value.toDouble(),extra)
                "Produção" -> { val id=name.toLongOrNull(); val q=qty.toDoubleOrNull()?:0.0; if(id!=null&&q>0) db.addProduction(id,q) }
                "Pedidos" -> { val id=name.toLongOrNull(); val q=qty.toDoubleOrNull()?:0.0; val p=value.toDoubleOrNull()?:0.0; if(id!=null&&q>0&&p>=0) db.addOrder(customer.toLongOrNull(),id,q,p,kind,due,"",0.0) }
                "Compras" -> { val id=name.toLongOrNull(); val q=qty.toDoubleOrNull()?:0.0; val p=value.toDoubleOrNull()?:0.0; if(id!=null&&q>0&&p>=0) db.purchase(id,q,p,extra) }
            }
            show=false; refresh++
        }){Text("Salvar")}},dismissButton={TextButton(onClick={show=false}){Text("Cancelar")}})
    }
}

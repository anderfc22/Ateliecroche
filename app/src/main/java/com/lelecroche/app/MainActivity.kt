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
import androidx.compose.ui.text.font.FontFamily
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
                        Text("Lelê Crochê", color = PrimaryGreen, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 20.sp)
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
                0 -> HomeScreen(Modifier.padding(padding)) { target -> when (target) { "orders" -> { selected = 1; moreSection = null }; "materials" -> { selected = 3; moreSection = null }; "finance" -> { selected = 4; moreSection = null }; "settings" -> { selected = 5; moreSection = "Preço, meta e ateliê" } } }
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
private fun HomeScreen(modifier: Modifier = Modifier, onNavigate: (String) -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase(context.applicationContext) }
    val repository = remember { CrochetRepository(db) }
    val openOrders = db.scalar("SELECT COUNT(*) FROM orders WHERE status='aberto'").toInt()
    val requests = db.scalar("SELECT COUNT(*) FROM orders WHERE status='solicitado'").toInt()
    val production = db.scalar("SELECT COUNT(DISTINCT o.id) FROM orders o WHERE o.status='aberto' AND EXISTS(SELECT 1 FROM production_orders p WHERE p.order_id=o.id AND p.status IN ('andamento','acabamento','pronta')) AND EXISTS(SELECT 1 FROM production_orders p WHERE p.order_id=o.id AND p.status IN ('fila','andamento','acabamento'))").toInt()
    val readyPieces = db.scalar("SELECT COALESCE(SUM(stock),0) FROM products WHERE active=1")
    val receivable = db.scalar("SELECT COALESCE(SUM(MAX(o.total-COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.order_id=o.id),0),0)),0) FROM orders o WHERE o.status NOT IN ('cancelado','solicitado')")
    val cashIn = db.scalar("SELECT COALESCE(SUM(amount),0) FROM payments") + db.scalar("SELECT COALESCE(SUM(amount),0) FROM incomes")
    val cashOut = db.scalar("SELECT COALESCE(SUM(total_cost),0) FROM purchases") + db.scalar("SELECT COALESCE(SUM(amount),0) FROM expenses")
    val cash = cashIn - cashOut
    val monthKey = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
    val previousCalendar = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -1) }
    val previousMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(previousCalendar.time)
    val monthRevenue = db.scalar("SELECT COALESCE(SUM(amount),0) FROM payments WHERE substr(created_at,1,7)='$monthKey'") + db.scalar("SELECT COALESCE(SUM(amount),0) FROM incomes WHERE substr(created_at,1,7)='$monthKey'")
    val previousRevenue = db.scalar("SELECT COALESCE(SUM(amount),0) FROM payments WHERE substr(created_at,1,7)='$previousMonth'") + db.scalar("SELECT COALESCE(SUM(amount),0) FROM incomes WHERE substr(created_at,1,7)='$previousMonth'")
    val vsPrevious = if (previousRevenue > 0) kotlin.math.round((monthRevenue - previousRevenue) / previousRevenue * 100).toInt() else null
    val goal = db.rows("SELECT svalue FROM settings WHERE skey='monthly_goal'").firstOrNull()?.get("svalue")?.toDoubleOrNull() ?: 0.0
    val goalPercent = if (goal > 0) kotlin.math.round(monthRevenue / goal * 100).toInt() else 0
    val dueWeek = db.scalar("SELECT COUNT(*) FROM orders WHERE status='aberto' AND due_date<>'' AND date(due_date)<=date('now','localtime','+7 day')").toInt()
    val overdue = db.scalar("SELECT COUNT(*) FROM orders WHERE status='aberto' AND due_date<>'' AND date(due_date)<date('now','localtime')").toInt()
    val lowByMinimum = db.rows("SELECT id,name FROM materials WHERE active=1 AND min_stock>0 AND stock<=min_stock ORDER BY stock/min_stock")
    val lowByDemand = repository.materialDemand().filter { it.shortage > CrochetEngine.EPSILON }.map { mapOf("id" to it.material.id.toString(), "name" to it.material.name) }
    val lowMaterials = (lowByMinimum + lowByDemand).distinctBy { it["id"] }
    val lowCount = lowMaterials.size
    val atelierName = db.rows("SELECT svalue FROM settings WHERE skey='atelier_name'").firstOrNull()?.get("svalue") ?: "Meu Ateliê"
    val dateLabel = java.text.SimpleDateFormat("d 'de' MMMM", java.util.Locale("pt","BR")).format(java.util.Date())
    val empty = db.scalar("SELECT (SELECT COUNT(*) FROM materials)+(SELECT COUNT(*) FROM products)+(SELECT COUNT(*) FROM orders)") == 0.0
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(18.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("● ATELIÊ ARTESANAL", color = PrimaryGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.weight(1f))
                    Text(dateLabel, color = Muted, fontSize = 11.sp)
                }
                Text("Olá, $atelierName", color = TextDark, fontFamily = FontFamily.Serif, fontSize = 25.sp, fontWeight = FontWeight.Medium)
                Text(if (openOrders > 0) "$openOrders encomenda(s) em aberto" + if (dueWeek > 0) ", $dueWeek com prazo nos próximos 7 dias." else ". Bom trabalho nas agulhas!" else "Nenhuma encomenda em aberto agora. Bom trabalho nas agulhas!", color = Muted, fontSize = 13.sp)
            }
        }
        if (requests > 0) item {
            Card(modifier = Modifier.fillMaxWidth().clickable { onNavigate("orders") }, colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE9D8))) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("$requests pedido(s) novo(s) do catálogo", fontWeight = FontWeight.SemiBold, color = Terracotta); Text("Toque para ver e confirmar", color = Muted, fontSize = 11.sp) }
                    Text("›", fontSize = 25.sp, color = Terracotta)
                }
            }
        }
        if (empty) item {
            Column(Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(14.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Comece por aqui", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Cadastre seu primeiro material e suas peças para acompanhar custos, estoque e encomendas.", color = Muted, fontSize = 12.sp)
                Button(onClick = { onNavigate("materials") }, modifier = Modifier.fillMaxWidth()) { Text("Cadastrar primeiro material") }
            }
        }
        if (lowCount > 0) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE9D8)), modifier = Modifier.fillMaxWidth().clickable { onNavigate("materials") }) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Atenção aos materiais", color = Terracotta, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("$lowCount material(is) com estoque baixo: " + lowMaterials.joinToString { it["name"].orEmpty() }, color = Terracotta, fontSize = 12.sp)
                    Text("Toque para repor", color = Terracotta, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }
        if (overdue > 0) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE4E1)), modifier = Modifier.fillMaxWidth().clickable { onNavigate("orders") }) {
                Text("$overdue encomenda(s) com prazo atrasado. Toque para revisar.", modifier = Modifier.padding(13.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (goal > 0) item {
            Column(Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Meta mensal de vendas", color = Muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("$goalPercent%", color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).background(Color(0xFFE3EAE2), RoundedCornerShape(10.dp))) {
                    Box(Modifier.fillMaxWidth((goalPercent.coerceIn(0, 100)) / 100f).fillMaxHeight().background(PrimaryGreen, RoundedCornerShape(10.dp)))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Recebido: " + homeMoney(monthRevenue), color = Muted, fontSize = 10.sp)
                    Text("Meta: " + homeMoney(goal), color = Muted, fontSize = 10.sp)
                }
            }
        } else item {
            Row(Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(12.dp)).clickable { onNavigate("settings") }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Defina a meta mensal de vendas", fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text("Acompanhe quanto falta para chegar lá", color = Muted, fontSize = 10.sp) }
                Text("›", color = PrimaryGreen, fontSize = 22.sp)
            }
        }
        item { Text("Visão geral", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextDark) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HomeMetric("Recebido no mês", homeMoney(monthRevenue), vsPrevious?.let { "$it% vs anterior" } ?: "sem mês anterior", Modifier.weight(1f))
                HomeMetric("Em produção", "$production ativa(s)", "$dueWeek entrega(s) na semana", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HomeMetric("Pronta-entrega", String.format(java.util.Locale("pt","BR"), "%.0f itens", readyPieces), "Prontos para vitrine", Modifier.weight(1f))
                HomeMetric("Saldo em caixa", homeMoney(cash), "A receber: " + homeMoney(receivable), Modifier.weight(1f))
            }
        }
        item { Text("Ações rápidas", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextDark) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = { onNavigate("orders") }, modifier = Modifier.weight(1f)) { Text("＋ Nova encomenda", fontSize = 11.sp) }
                OutlinedButton(onClick = { onNavigate("materials") }, modifier = Modifier.weight(1f)) { Text("＋ Novo material", fontSize = 11.sp) }
            }
        }
        item {
            TextButton(onClick = { onNavigate("finance") }, modifier = Modifier.fillMaxWidth()) { Text("Ver financeiro completo") }
        }
    }
}

private fun homeMoney(value: Double): String = "R$ " + String.format(java.util.Locale("pt","BR"), "%.2f", value)

@Composable
private fun HomeMetric(title: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier.background(CardWhite, RoundedCornerShape(14.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = PrimaryGreen, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(caption, color = Muted, fontSize = 9.sp)
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

package com.lelecroche.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun financeMoney(v: Double) = "R$ " + String.format(Locale("pt", "BR"), "%.2f", v)
private fun financeNumber(v: String) = v.trim().let { raw -> if (raw.contains(",")) raw.replace(".", "").replace(",", ".").toDoubleOrNull() else raw.toDoubleOrNull() }

@Composable
fun FinanceScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase(context.applicationContext) }
    val repo = remember { CrochetRepository(db) }
    var refresh by remember { mutableIntStateOf(0) }
    var month by remember { mutableStateOf(SimpleDateFormat("yyyy-MM", Locale.US).format(Calendar.getInstance().time)) }
    var entryType by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val incoming = remember(refresh, month) {
        db.scalar("SELECT COALESCE(SUM(amount),0) FROM payments WHERE substr(created_at,1,7)='$month' AND amount>0") +
            db.scalar("SELECT COALESCE(SUM(amount),0) FROM incomes WHERE substr(created_at,1,7)='$month'")
    }
    val outgoing = remember(refresh, month) {
        db.scalar("SELECT COALESCE(SUM(total_cost),0) FROM purchases WHERE substr(created_at,1,7)='$month'") +
            db.scalar("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE substr(created_at,1,7)='$month'") -
            db.scalar("SELECT COALESCE(SUM(amount),0) FROM payments WHERE substr(created_at,1,7)='$month' AND amount<0")
    }
    val cash = remember(refresh, month) {
        db.scalar("SELECT COALESCE(SUM(amount),0) FROM payments WHERE substr(created_at,1,7)<='$month'") +
            db.scalar("SELECT COALESCE(SUM(amount),0) FROM incomes WHERE substr(created_at,1,7)<='$month'") -
            db.scalar("SELECT COALESCE(SUM(total_cost),0) FROM purchases WHERE substr(created_at,1,7)<='$month'") -
            db.scalar("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE substr(created_at,1,7)<='$month'")
    }
    val receivable = remember(refresh) {
        db.scalar("SELECT COALESCE(SUM(MAX(o.total-COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.order_id=o.id),0),0)),0) FROM orders o WHERE o.status NOT IN ('cancelado','solicitado')")
    }
    val entries = remember(refresh, month) {
        db.rows("SELECT * FROM (SELECT 'payment' type,p.id,p.amount,p.method,p.created_at,'Pagamento pedido #'||p.order_id title FROM payments p WHERE substr(p.created_at,1,7)='$month' UNION ALL SELECT 'income' type,i.id,i.amount,i.method,i.created_at,i.description title FROM incomes i WHERE substr(i.created_at,1,7)='$month' UNION ALL SELECT 'purchase' type,pu.id,-pu.total_cost amount,pu.method,pu.created_at,'Compra · '||m.name title FROM purchases pu JOIN materials m ON m.id=pu.material_id WHERE substr(pu.created_at,1,7)='$month' UNION ALL SELECT 'expense' type,e.id,-e.amount amount,e.method,e.created_at,e.description title FROM expenses e WHERE substr(e.created_at,1,7)='$month') ORDER BY created_at DESC")
    }
    val previousMonth = shiftFinanceMonth(month, -1)
    val previousIncoming = db.scalar("SELECT COALESCE(SUM(amount),0) FROM payments WHERE substr(created_at,1,7)='$previousMonth'") + db.scalar("SELECT COALESCE(SUM(amount),0) FROM incomes WHERE substr(created_at,1,7)='$previousMonth'")
    val marginPercent = if (incoming > 0) (incoming - outgoing) / incoming * 100.0 else 0.0
    val weeks = (0..3).map { index ->
        val weekEntries = entries.filter { row -> ((row["created_at"]?.substringAfterLast("-")?.take(2)?.toIntOrNull() ?: 1) - 1) / 7 == index }
        val weekIn = weekEntries.filter { it["type"] == "payment" || it["type"] == "income" }.sumOf { (it["amount"]?.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) }
        val weekOut = -weekEntries.filter { it["type"] == "purchase" || it["type"] == "expense" || (it["type"] == "payment" && (it["amount"]?.toDoubleOrNull() ?: 0.0) < 0) }.sumOf { it["amount"]?.toDoubleOrNull() ?: 0.0 }
        weekIn to weekOut
    }
    val bestWeek = weeks.indices.maxByOrNull { weeks[it].first } ?: 0
    val maxWeek = weeks.maxOfOrNull { maxOf(it.first, it.second) }?.coerceAtLeast(1.0) ?: 1.0
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Financeiro", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF161D19))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { month = shiftFinanceMonth(month, -1) }) { Icon(Icons.Default.ChevronLeft, "Mês anterior") }
            Text(financeMonthLabel(month), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            IconButton(onClick = { month = shiftFinanceMonth(month, 1) }) { Icon(Icons.Default.ChevronRight, "Próximo mês") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FinanceMetric("Entradas", financeMoney(incoming), Modifier.weight(1f), Color(0xFF42634C))
            FinanceMetric("Saídas", financeMoney(outgoing), Modifier.weight(1f), Color(0xFF7F5534))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FinanceMetric("Saldo do mês", financeMoney(incoming - outgoing), Modifier.weight(1f), Color(0xFF42634C))
            FinanceMetric("A receber", financeMoney(receivable), Modifier.weight(1f), Color(0xFF7F5534))
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Resultado de caixa (%)", color = Color(0xFF424842), fontSize = 10.sp); Text(String.format(Locale("pt","BR"), "%.1f%%", marginPercent), color = if (marginPercent >= 0) Color(0xFF42634C) else MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
            Column(horizontalAlignment = Alignment.End) { Text("Recebido no mês anterior", color = Color(0xFF424842), fontSize = 10.sp); Text(financeMoney(previousIncoming), fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Movimentação por semana", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            weeks.forEachIndexed { index, values ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Semana ${index + 1}" + if (index == bestWeek && values.first > 0) " · melhor entrada" else "", fontSize = 10.sp, color = Color(0xFF424842))
                        Text("+ ${financeMoney(values.first)}  − ${financeMoney(values.second)}", fontSize = 10.sp, color = Color(0xFF424842))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.weight((values.first.coerceAtLeast(0.0) / maxWeek).coerceAtLeast(0.02).toFloat()).height(5.dp).background(Color(0xFF42634C), RoundedCornerShape(5.dp)))
                        Box(Modifier.weight((values.second.coerceAtLeast(0.0) / maxWeek).coerceAtLeast(0.02).toFloat()).height(5.dp).background(Color(0xFF7F5534), RoundedCornerShape(5.dp)))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF6EE)), shape = RoundedCornerShape(13.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("Caixa acumulado", color = Color(0xFF424842), fontSize = 11.sp); Text(financeMoney(cash), color = Color(0xFF42634C), fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { entryType = "receita" }) { Text("+ Receita", fontSize = 11.sp) }
                    Button(onClick = { entryType = "despesa" }) { Text("+ Despesa", fontSize = 11.sp) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Movimentações do mês", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(7.dp))
        if (entries.isEmpty()) Text("Nenhuma movimentação neste mês.", color = Color(0xFF424842), fontSize = 13.sp)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(entries, key = { (it["type"] ?: "") + (it["id"] ?: "") }) { entry ->
                val amount = entry["amount"]?.toDoubleOrNull() ?: 0.0
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(11.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(entry["title"].orEmpty(), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text("${entry["created_at"]} · ${entry["method"].orEmpty()}", color = Color(0xFF727972), fontSize = 10.sp)
                        }
                        Text(financeMoney(amount), color = if (amount >= 0) Color(0xFF42634C) else Color(0xFF7F5534), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        if (entry["type"] == "income" || entry["type"] == "expense") IconButton(onClick = { deleteTarget = entry["type"]!! to entry["id"]!!.toLong() }) { Icon(Icons.Default.DeleteOutline, "Excluir lançamento", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    entryType?.let { type -> FinanceEntryDialog(type, repo, onDismiss = { entryType = null }, onSaved = { entryType = null; refresh++ }) }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Excluir lançamento?") },
            text = { Text("Esta ação remove a receita ou despesa avulsa do histórico.") },
            confirmButton = { TextButton(onClick = {
                try { repo.deleteFinanceEntry(target.first, target.second); deleteTarget = null; refresh++ }
                catch (e: Exception) { deleteError = e.message; deleteTarget = null }
            }) { Text("Excluir", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }
        )
    }
    deleteError?.let { msg ->
        AlertDialog(onDismissRequest = { deleteError = null }, title = { Text("Não foi possível excluir") }, text = { Text(msg) }, confirmButton = { TextButton(onClick = { deleteError = null }) { Text("OK") } })
    }
}

@Composable
private fun FinanceMetric(title: String, value: String, modifier: Modifier, tint: Color) {
    Column(modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(title, color = Color(0xFF424842), fontSize = 11.sp)
        Text(value, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun FinanceEntryDialog(type: String, repo: CrochetRepository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Pix") }
    var category by remember { mutableStateOf("Outros") }
    var error by remember { mutableStateOf<String?>(null) }
    val income = type == "receita"
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (income) "Nova receita avulsa" else "Nova despesa") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(description, { description = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(amount, { amount = it }, label = { Text("Valor (R$)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(method, { method = it }, label = { Text("Forma de pagamento") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (!income) OutlinedTextField(category, { category = it }, label = { Text("Categoria") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }, confirmButton = { TextButton(onClick = {
        try {
            val value = financeNumber(amount) ?: 0.0
            if (income) repo.addIncome(description, value, method) else repo.addExpense(description, value, category, method)
            onSaved()
        } catch (e: Exception) { error = e.message ?: "Não foi possível salvar." }
    }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

private fun shiftFinanceMonth(value: String, delta: Int): String {
    val parts = value.split("-")
    val c = Calendar.getInstance().apply { set(parts[0].toInt(), parts[1].toInt() - 1, 1); add(Calendar.MONTH, delta) }
    return SimpleDateFormat("yyyy-MM", Locale.US).format(c.time)
}
private fun financeMonthLabel(value: String): String {
    val c = Calendar.getInstance()
    val parts = value.split("-")
    c.set(parts[0].toInt(), parts[1].toInt() - 1, 1)
    return SimpleDateFormat("MMMM 'de' yyyy", Locale("pt", "BR")).format(c.time).replaceFirstChar { it.uppercase() }
}

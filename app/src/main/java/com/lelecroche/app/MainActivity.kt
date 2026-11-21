package com.lelecroche.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
        Destination("Pedidos", Icons.Default.ReceiptLong),
        Destination("Peças", Icons.Default.Cottage),
        Destination("Estoque", Icons.Default.Inventory2),
        Destination("Financeiro", Icons.Default.AccountBalanceWallet),
        Destination("Mais", Icons.Default.GridView)
    )
    var selected by remember { mutableIntStateOf(0) }

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
                        Text(destinations[selected].title, color = Muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { selected = 5 }) { Icon(Icons.Default.Dashboard, "Menu") }
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
                            onClick = { selected = index },
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
                1 -> ModuleScreen(Modifier.padding(padding), "Encomendas", "Acompanhe pedidos, prazos, pagamentos e andamento da produção.", Icons.Default.ReceiptLong)
                2 -> ModuleScreen(Modifier.padding(padding), "Peças", "Cadastre peças, receitas, materiais utilizados e preços.", Icons.Default.Cottage)
                3 -> ModuleScreen(Modifier.padding(padding), "Estoque", "Controle fios, materiais, compras e movimentações.", Icons.Default.Inventory2)
                4 -> ModuleScreen(Modifier.padding(padding), "Financeiro", "Consulte receitas, despesas, fluxo de caixa e relatórios.", Icons.Default.AccountBalanceWallet)
                else -> MoreScreen(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(
                Modifier.fillMaxWidth().background(Color(0xFFEEF6EE), RoundedCornerShape(18.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("ATELIÊ ARTESANAL", color = PrimaryGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Olá! Que bom ter você por aqui.", color = TextDark, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text("Seu ateliê, organizado em um só lugar.", color = Muted, fontSize = 13.sp)
            }
        }
        item { Text("Visão geral", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = TextDark) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Pedidos", "—", "Em acompanhamento", Modifier.weight(1f))
                MetricCard("Estoque baixo", "—", "Materiais para repor", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("A receber", "R$ —", "Pagamentos pendentes", Modifier.weight(1f))
                MetricCard("Produção", "—", "Peças em andamento", Modifier.weight(1f))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(14.dp)).padding(16.dp)) {
                Text("Comece por aqui", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text("Use o menu inferior para acessar pedidos, peças, estoque e financeiro. Os dados serão conectados ao sistema original na próxima etapa.", color = Muted, fontSize = 13.sp)
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
private fun MoreScreen(modifier: Modifier = Modifier) {
    val entries = listOf("Clientes", "Produção", "Relatórios", "Catálogo público", "Configurações", "Backup e dados")
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Mais opções", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
        entries.forEach { entry ->
            Row(
                Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(12.dp)).clickable { }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(entry, Modifier.weight(1f), color = TextDark, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
    }
}

package com.example.presentation.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.domain.model.SyncState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ClientOrigins
import com.example.domain.model.Shift
import com.example.domain.util.DateTimeUtils
import com.example.presentation.components.PaoPrimaryButton
import com.example.presentation.components.SyncStatusPill
import com.example.ui.theme.PaoAnthracite
import com.example.ui.theme.PaoAnthraciteMuted
import com.example.ui.theme.PaoBackground
import com.example.ui.theme.PaoBorder
import com.example.ui.theme.PaoBorderLight
import com.example.ui.theme.PaoCream
import com.example.ui.theme.PaoCreamLight
import com.example.ui.theme.PaoRed
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val context = LocalContext.current

    val metrics = uiState.metrics

    Scaffold(
        containerColor = PaoBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Administração",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        Text(
                            text = "Métricas operacionais e exportação de relatórios",
                            fontSize = 12.sp,
                            color = PaoAnthraciteMuted
                        )
                    }

                    SyncStatusPill(
                        syncState = syncState,
                        pendingCount = pendingCount
                    )
                }
            }

            // Banner de aviso sobre informações em cache e sincronização
            if (syncState != SyncState.SINCRONIZADO || pendingCount > 0) {
                item {
                    val opener = com.example.presentation.components.LocalSyncLogsOpener.current
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { opener() },
                        color = StatusAmber.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusAmber.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "As informações podem estar desatualizadas, sincronize-as",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PaoAnthracite
                                )
                                Text(
                                    text = "Somando registros em cache com alterações locais.",
                                    fontSize = 11.sp,
                                    color = PaoAnthraciteMuted
                                )
                            }
                            TextButton(onClick = { opener() }) {
                                Text("Ver Logs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PaoRed)
                            }
                        }
                    }
                }
            }

            // Export Result Banners
            if (uiState.exportMessage != null) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        color = StatusGreen.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusGreen.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = uiState.exportMessage ?: "",
                                    fontSize = 12.sp,
                                    color = PaoAnthracite,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (uiState.exportFileToShare != null) {
                                Button(
                                    onClick = { viewModel.shareExportedFile(context, uiState.exportFileToShare!!) },
                                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Abrir", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.exportErrorMessage != null) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        color = StatusRed.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusRed.copy(alpha = 0.4f))
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = StatusRed)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = uiState.exportErrorMessage ?: "",
                                fontSize = 12.sp,
                                color = StatusRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Filter Bar Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PaoBorder, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FilterList, contentDescription = null, tint = PaoRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Filtros de Período e Dados",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        // Date Filter chips
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DateFilterOption.values().forEach { opt ->
                                val isSelected = uiState.filter.dateOption == opt
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { viewModel.setDateFilter(opt) },
                                    color = if (isSelected) PaoRed else PaoCreamLight,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = opt.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else PaoAnthracite,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Period filter (Todos, Manhã, Noite) and Preferential toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PeriodChip("Todos Turnos", isSelected = uiState.filter.selectedPeriod == null) {
                                viewModel.setPeriodFilter(null)
                            }
                            PeriodChip("Manhã", isSelected = uiState.filter.selectedPeriod == "MANHA") {
                                viewModel.setPeriodFilter("MANHA")
                            }
                            PeriodChip("Noite", isSelected = uiState.filter.selectedPeriod == "NOITE") {
                                viewModel.setPeriodFilter("NOITE")
                            }
                            PeriodChip(
                                title = "★ Preferencial",
                                isSelected = uiState.filter.preferentialOnly,
                                onClick = { viewModel.togglePreferentialFilter() }
                            )
                        }
                    }
                }
            }

            // Key Metrics Grid
            item {
                Text(
                    text = "Indicadores Principais",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaoAnthracite
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdminMetricCard(
                        title = "Pessoas Atendidas",
                        value = metrics.totalPeople.toString(),
                        subtitle = "${metrics.totalAttendances} atendimentos",
                        modifier = Modifier.weight(1f)
                    )
                    AdminMetricCard(
                        title = "Clientes Novos",
                        value = metrics.totalNewClients.toString(),
                        valueColor = StatusGreen,
                        subtitle = "${if (metrics.totalPeople > 0) (metrics.totalNewClients * 100 / metrics.totalPeople) else 0}% do total",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdminMetricCard(
                        title = "Recorrentes",
                        value = metrics.totalReturningClients.toString(),
                        subtitle = "Clientes fiéis",
                        modifier = Modifier.weight(1f)
                    )
                    AdminMetricCard(
                        title = "Preferencial (★)",
                        value = metrics.preferentialCount.toString(),
                        valueColor = StatusAmber,
                        subtitle = "Atendimentos prioritários",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Distribution of new clients by origin
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Distribuição de Clientes por Origem",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val totalOriginNew = metrics.originDistribution.values.sum().coerceAtLeast(1)
                        ClientOrigins.ALL.forEach { origin ->
                            val count = metrics.originDistribution[origin] ?: 0
                            val pct = (count * 100) / totalOriginNew

                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = origin, fontSize = 12.sp, color = PaoAnthracite)
                                    Text(
                                        text = "$count ($pct%)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PaoAnthracite
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                // Visual bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(PaoBorderLight)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((pct / 100f).coerceIn(0f, 1f))
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (count > 0) PaoRed else Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Hoster Performance
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Desempenho por Hoster",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (metrics.hosterPerformance.isEmpty()) {
                            Text(
                                text = "Nenhum atendimento registrado no período.",
                                fontSize = 12.sp,
                                color = PaoAnthraciteMuted
                            )
                        } else {
                            metrics.hosterPerformance.forEach { (name, totalPpl) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = PaoRed, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = PaoAnthracite)
                                    }
                                    Text(
                                        text = "$totalPpl pessoas",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PaoAnthracite
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Turnos em Aberto (with warning for export blocking)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (uiState.openShifts.isNotEmpty()) StatusAmber else PaoBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Turnos em Aberto (${uiState.openShifts.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                            if (uiState.openShifts.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = StatusAmber.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Bloqueia Exportação",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusAmber,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (uiState.openShifts.isEmpty()) {
                            Text(
                                text = "Todos os turnos estão devidamente encerrados. Exportação liberada.",
                                fontSize = 12.sp,
                                color = StatusGreen,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "Turnos que ainda impedem a exportação consolidada:",
                                fontSize = 11.sp,
                                color = PaoAnthraciteMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            uiState.openShifts.forEach { s ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    color = PaoCreamLight,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(text = s.hosterName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(text = "Turno ${s.period.displayName} • Aberto ${DateTimeUtils.formatTime(s.openedAt)}", fontSize = 11.sp, color = PaoAnthraciteMuted)
                                        }
                                        Text(text = "Aberto", fontSize = 11.sp, color = StatusAmber, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Export Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = PaoCreamLight),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = PaoRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Exportar Relatório Gerencial (.xlsx)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Gera uma planilha Excel com duas abas completas (Atendimentos e Clientes) de todos os hosters no período selecionado.",
                            fontSize = 12.sp,
                            color = PaoAnthraciteMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        PaoPrimaryButton(
                            text = "Gerar e Baixar XLSX",
                            onClick = { viewModel.exportExcel() },
                            isLoading = uiState.isExporting,
                            testTag = "btn_exportar_excel"
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun PeriodChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (isSelected) PaoAnthracite else PaoCreamLight,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else PaoAnthracite,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AdminMetricCard(
    title: String,
    value: String,
    subtitle: String,
    valueColor: Color = PaoAnthracite,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(1.dp, PaoBorderLight, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, fontSize = 12.sp, color = PaoAnthraciteMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 11.sp, color = PaoAnthraciteMuted)
        }
    }
}

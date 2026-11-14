package com.example.presentation.admin

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.domain.model.SyncState
import com.example.domain.util.DateTimeUtils
import com.example.presentation.components.LocalSyncLogsOpener
import com.example.presentation.components.PaoPrimaryButton
import com.example.presentation.components.SyncStatusPill
import com.example.presentation.components.motionDuration
import com.example.presentation.components.rememberReducedMotion
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
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    actorId: String? = null,
    actorName: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val context = LocalContext.current
    val reducedMotion = rememberReducedMotion()

    LaunchedEffect(Unit) {
        viewModel.onAdminScreenOpened(actorId, actorName)
    }

    val metrics = uiState.metrics

    // Modal de Confirmação e Compartilhamento ao Gerar Relatório
    if (uiState.exportSuccessData != null) {
        val successData = uiState.exportSuccessData!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissExportDialog() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusGreen,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Relatório Gerado!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaoAnthracite
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Planilha XLSX consolidada pronta para compartilhamento e análise.",
                        fontSize = 13.sp,
                        color = PaoAnthraciteMuted
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = PaoCreamLight,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Período:", fontSize = 12.sp, color = PaoAnthraciteMuted)
                                Text(
                                    text = successData.periodLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PaoAnthracite
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total de Clientes:", fontSize = 12.sp, color = PaoAnthraciteMuted)
                                Text(
                                    text = "${successData.totalClients} pessoas",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PaoRed
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Atendimentos:", fontSize = 12.sp, color = PaoAnthraciteMuted)
                                Text(
                                    text = "${successData.rowCount} registros",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PaoAnthracite
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Solo / Dupla / Grupo:", fontSize = 12.sp, color = PaoAnthraciteMuted)
                                Text(
                                    text = "${successData.soloCount} / ${successData.duplaCount} / ${successData.grupoCount}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PaoAnthracite
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        border = BorderStroke(1.dp, PaoBorderLight),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Abas incluídas na planilha:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("1. Atendimentos (Turno, hoster, membros, tipo)", fontSize = 11.sp, color = PaoAnthraciteMuted)
                            Text("2. Clientes (Individual, origem, classificação)", fontSize = 11.sp, color = PaoAnthraciteMuted)
                            Text("3. Resumo Diário & Origens (Métricas por dia, turnos e canais)", fontSize = 11.sp, color = PaoAnthraciteMuted)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.shareExportedFile(context, successData.file)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PaoRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_modal_compartilhar")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Compartilhar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.openExportedFile(context, successData.file)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("btn_modal_abrir")
                    ) {
                        Text("Abrir", fontSize = 13.sp, color = PaoAnthracite)
                    }
                    TextButton(
                        onClick = { viewModel.dismissExportDialog() },
                        modifier = Modifier.testTag("btn_modal_fechar")
                    ) {
                        Text("Fechar", fontSize = 13.sp, color = PaoAnthraciteMuted)
                    }
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Modal de Erro/Aviso na Exportação
    if (uiState.exportErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissExportError() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = StatusRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Não Foi Possível Exportar",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusRed
                    )
                }
            },
            text = {
                Text(
                    text = uiState.exportErrorMessage ?: "",
                    fontSize = 13.sp,
                    color = PaoAnthracite
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissExportError() },
                    colors = ButtonDefaults.buttonColors(containerColor = PaoAnthracite),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Entendido", fontSize = 13.sp)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(18.dp)
        )
    }

    Scaffold(
        containerColor = PaoBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            text = "Filtros de período, métricas, relatórios e logs",
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

            // Sub-navigation Segmented Control: Indicadores & Relatórios | Logs do Sistema
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    border = BorderStroke(1.dp, PaoBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AdminSubTab.values().forEach { subTab ->
                            val selected = uiState.activeSubTab == subTab
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.setSubTab(subTab) }
                                    .testTag("admin_subtab_${subTab.name.lowercase()}"),
                                color = if (selected) PaoRed else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (subTab == AdminSubTab.LOGS) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = if (selected) Color.White else PaoAnthraciteMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                    }
                                    Text(
                                        text = subTab.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (selected) Color.White else PaoAnthraciteMuted,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Banner de aviso sobre informações em cache e sincronização
            if (syncState != SyncState.SINCRONIZADO || pendingCount > 0) {
                item {
                    val opener = LocalSyncLogsOpener.current
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { opener() },
                        color = StatusAmber.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.4f)),
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
                                Text("Ver Fila", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PaoRed)
                            }
                        }
                    }
                }
            }

            if (uiState.activeSubTab == AdminSubTab.LOGS) {
                // Logs do Sistema View
                item {
                    SystemLogsFilterCard(
                        filter = uiState.logsFilter,
                        availableUsers = uiState.availableLogUsers,
                        availableActions = uiState.availableLogActions,
                        availableDevices = uiState.availableLogDevices,
                        onStartDateChange = { viewModel.setLogsStartDate(it) },
                        onEndDateChange = { viewModel.setLogsEndDate(it) },
                        onUserChange = { viewModel.setLogsUserFilter(it) },
                        onActionChange = { viewModel.setLogsActionFilter(it) },
                        onDeviceChange = { viewModel.setLogsDeviceFilter(it) },
                        onResultChange = { viewModel.setLogsResultFilter(it) },
                        onClearFilters = { viewModel.clearLogsFilters() }
                    )
                }

                item {
                    Text(
                        text = "Registros de Atividade (${uiState.filteredLogs.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaoAnthracite
                    )
                }

                if (uiState.filteredLogs.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PaoBorderLight, RoundedCornerShape(14.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "Nenhum log encontrado para os filtros aplicados.",
                                fontSize = 13.sp,
                                color = PaoAnthraciteMuted,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(uiState.filteredLogs, key = { it.id }) { logItem ->
                        SystemLogItemCard(log = logItem)
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            } else {
                // 1. PROMINENT TOP PERIOD & FILTERS CARD ("Período da consulta")
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = tween(motionDuration(reducedMotion, 200)))
                            .border(1.5.dp, PaoRed.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(18.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = PaoCream
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.CalendarToday,
                                                contentDescription = null,
                                                tint = PaoRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Período da consulta",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PaoAnthracite
                                        )
                                        Text(
                                            text = "Consultando: ${uiState.activePeriodSummary}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PaoRed
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { viewModel.clearAllFilters(actorId, actorName) },
                                    modifier = Modifier.testTag("btn_limpar_periodo")
                                ) {
                                    Icon(
                                        Icons.Default.ClearAll,
                                        contentDescription = null,
                                        tint = PaoAnthraciteMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Limpar", fontSize = 11.sp, color = PaoAnthraciteMuted)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Prominent Data inicial + Data final boxes ALWAYS visible at the top
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DatePickerFieldBox(
                                    label = "Data inicial",
                                    dateText = if (uiState.filter.dateOption == DateFilterOption.TODOS) {
                                        "Início"
                                    } else {
                                        DateTimeUtils.formatDate(uiState.filter.draftStartMillis)
                                    },
                                    initialMillis = uiState.filter.draftStartMillis,
                                    onDateSelected = { viewModel.setDraftStartDate(it) },
                                    testTag = "admin_data_inicial",
                                    modifier = Modifier.weight(1f)
                                )

                                DatePickerFieldBox(
                                    label = "Data final",
                                    dateText = if (uiState.filter.dateOption == DateFilterOption.TODOS) {
                                        "Fim"
                                    } else {
                                        DateTimeUtils.formatDate(uiState.filter.draftEndMillis)
                                    },
                                    initialMillis = uiState.filter.draftEndMillis,
                                    onDateSelected = { viewModel.setDraftEndDate(it) },
                                    testTag = "admin_data_final",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (!uiState.dateValidationError.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = StatusRed.copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = uiState.dateValidationError!!,
                                        fontSize = 11.sp,
                                        color = StatusRed,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { viewModel.applyDraftFilters(actorId, actorName) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PaoRed,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("btn_aplicar_filtros")
                            ) {
                                Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aplicar filtros", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Atalhos de período",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PaoAnthraciteMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))

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
                                            .clickable { viewModel.setDateFilter(opt, actorId, actorName) }
                                            .testTag("filter_date_${opt.name.lowercase()}"),
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

                            HorizontalDivider(
                                color = PaoBorderLight,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )

                            // Turno & Preferencial
                            Text(
                                text = "Turno e Preferência",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PaoAnthraciteMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChipPill("Todos Turnos", uiState.filter.selectedPeriod == null) {
                                    viewModel.setPeriodFilter(null)
                                }
                                FilterChipPill("Manhã", uiState.filter.selectedPeriod == "MANHA") {
                                    viewModel.setPeriodFilter("MANHA")
                                }
                                FilterChipPill("Noite", uiState.filter.selectedPeriod == "NOITE") {
                                    viewModel.setPeriodFilter("NOITE")
                                }
                                FilterChipPill("★ Preferencial", uiState.filter.preferentialOnly) {
                                    viewModel.togglePreferentialFilter()
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Classificação
                            Text(
                                text = "Classificação",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PaoAnthraciteMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChipPill("Todas", uiState.filter.selectedClassification == null) {
                                    viewModel.setClassificationFilter(null)
                                }
                                FilterChipPill("Novos", uiState.filter.selectedClassification == "NOVO") {
                                    viewModel.setClassificationFilter("NOVO")
                                }
                                FilterChipPill("Recorrentes", uiState.filter.selectedClassification == "RECORRENTE") {
                                    viewModel.setClassificationFilter("RECORRENTE")
                                }
                            }

                            // Hoster filter
                            if (uiState.availableHosters.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Hoster",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PaoAnthraciteMuted
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChipPill("Todos Hosters", uiState.filter.selectedHosterId == null) {
                                        viewModel.setHosterFilter(null)
                                    }
                                    uiState.availableHosters.forEach { hosterItem ->
                                        FilterChipPill(
                                            title = hosterItem.name,
                                            isSelected = uiState.filter.selectedHosterId == hosterItem.id
                                        ) {
                                            viewModel.setHosterFilter(hosterItem.id)
                                        }
                                    }
                                }
                            }

                            // Origem filter
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Origem",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PaoAnthraciteMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChipPill("Todas Origens", uiState.filter.selectedOrigin == null) {
                                    viewModel.setOriginFilter(null)
                                }
                                ClientOrigins.ALL.forEach { orig ->
                                    FilterChipPill(
                                        title = orig,
                                        isSelected = uiState.filter.selectedOrigin == orig
                                    ) {
                                        viewModel.setOriginFilter(orig)
                                    }
                                }
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

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AdminMetricCard(
                            title = "Clientes Manhã",
                            value = metrics.morningAttendances.toString(),
                            subtitle = "Turno Manhã",
                            modifier = Modifier.weight(1f)
                        )
                        AdminMetricCard(
                            title = "Clientes Noite",
                            value = metrics.nightAttendances.toString(),
                            subtitle = "Turno Noite",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    val totalAtt = metrics.totalAttendances.coerceAtLeast(1)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdminMetricCard(
                            title = "Solo",
                            value = metrics.soloCount.toString(),
                            valueColor = PaoRed,
                            subtitle = "${if (metrics.totalAttendances > 0) (metrics.soloCount * 100 / totalAtt) else 0}% dos atend.",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_metric_solo")
                        )
                        AdminMetricCard(
                            title = "Dupla",
                            value = metrics.duplaCount.toString(),
                            valueColor = PaoAnthracite,
                            subtitle = "${if (metrics.totalAttendances > 0) (metrics.duplaCount * 100 / totalAtt) else 0}% dos atend.",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_metric_dupla")
                        )
                        AdminMetricCard(
                            title = "Grupo",
                            value = metrics.grupoCount.toString(),
                            valueColor = StatusAmber,
                            subtitle = "${if (metrics.totalAttendances > 0) (metrics.grupoCount * 100 / totalAtt) else 0}% dos atend.",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_metric_grupo")
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
                                text = "De Onde Conheceram (Clientes Novos)",
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

                // Turnos em Aberto
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
                                    text = "Turnos abertos que precisam ser encerrados antes do fechamento:",
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
                                                Text(
                                                    text = "Turno ${s.period.displayName} • Aberto ${DateTimeUtils.formatTime(s.openedAt)}",
                                                    fontSize = 11.sp,
                                                    color = PaoAnthraciteMuted
                                                )
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
                                    text = "Gerar Planilha Gerencial (.xlsx)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PaoAnthracite
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Gera uma planilha Excel com 3 abas completas e totais de conclusão no final (Atendimentos com Solo/Dupla/Grupo, Clientes com Total de Clientes e Resumo Diário & Origens consolidado).",
                                fontSize = 12.sp,
                                color = PaoAnthraciteMuted
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            PaoPrimaryButton(
                                text = "Gerar Relatório XLSX",
                                onClick = { viewModel.exportExcel(actorId, actorName) },
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
}

@Composable
private fun DatePickerFieldBox(
    label: String,
    dateText: String,
    initialMillis: Long,
    onDateSelected: (Long) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        val sel = Calendar.getInstance().apply {
                            set(y, m, d)
                        }
                        onDateSelected(sel.timeInMillis)
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
            .testTag(testTag),
        color = PaoCreamLight,
        border = BorderStroke(1.dp, PaoBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CalendarToday,
                contentDescription = label,
                tint = PaoRed,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(label, fontSize = 10.sp, color = PaoAnthraciteMuted)
                Text(
                    dateText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaoAnthracite
                )
            }
        }
    }
}

@Composable
private fun FilterChipPill(
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
            AnimatedContent(
                targetState = value,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "metric_value"
            ) { animatedVal ->
                Text(text = animatedVal, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 11.sp, color = PaoAnthraciteMuted)
        }
    }
}

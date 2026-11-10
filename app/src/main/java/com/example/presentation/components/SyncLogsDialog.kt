package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.SyncQueueEntity
import com.example.data.sync.SyncLogEntry
import com.example.data.sync.SyncManager
import com.example.domain.model.SyncState
import com.example.domain.util.DateTimeUtils
import com.example.ui.theme.PaoAnthracite
import com.example.ui.theme.PaoAnthraciteMuted
import com.example.ui.theme.PaoBackground
import com.example.ui.theme.PaoBorder
import com.example.ui.theme.PaoBorderLight
import com.example.ui.theme.PaoCream
import com.example.ui.theme.PaoCreamLight
import com.example.ui.theme.PaoRed
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import kotlinx.coroutines.launch

@Composable
fun SyncLogsDialog(
    syncManager: SyncManager,
    onDismiss: () -> Unit
) {
    val syncState by syncManager.syncState.collectAsState()
    val pendingCount by syncManager.pendingCount.collectAsState()
    val lastSyncTime by syncManager.lastSyncTime.collectAsState()
    val syncLogs by syncManager.syncLogs.collectAsState()
    val queueItems by syncManager.allQueueItems.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Fila de Operações, 1: Logs em Tempo Real

    val failedCount = queueItems.count { it.status == "FAILED" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = PaoBackground
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 24.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            color = PaoCream,
                            border = androidx.compose.foundation.BorderStroke(1.dp, PaoBorderLight)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = PaoRed,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Central de Sincronização",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                            Text(
                                text = "Supabase PostgreSQL • Cache & Logs",
                                fontSize = 12.sp,
                                color = PaoAnthraciteMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_sync_dialog_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = PaoAnthracite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Status Overview Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PaoBorder, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Status Pill
                            val (dotColor, statusIcon, statusLabel) = when (syncState) {
                                SyncState.SINCRONIZADO -> Triple(StatusGreen, Icons.Default.CloudDone, "Sincronizado")
                                SyncState.OFFLINE -> Triple(StatusAmber, Icons.Default.CloudOff, "Modo Offline")
                                SyncState.PENDENTE -> Triple(StatusAmber, Icons.Default.CloudQueue, "Pendente ($pendingCount)")
                                SyncState.SINCRONIZANDO -> Triple(StatusBlue, Icons.Default.CloudSync, "Sincronizando...")
                                SyncState.ERRO -> Triple(StatusRed, Icons.Default.Warning, "Erro de Sincronização")
                            }

                            Surface(
                                color = dotColor.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, dotColor.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = null,
                                        tint = dotColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = statusLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = dotColor
                                    )
                                }
                            }

                            // Network state
                            Text(
                                text = if (syncManager.isOnline) "🟢 Rede Conectada" else "⚪ Sem Internet",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (syncManager.isOnline) StatusGreen else PaoAnthraciteMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Last Sync Info
                        Text(
                            text = if (lastSyncTime != null) {
                                "Última sincronização com sucesso: ${DateTimeUtils.formatDateTime(lastSyncTime!!)}"
                            } else {
                                "Nenhuma sincronização completa registrada nesta sessão."
                            },
                            fontSize = 12.sp,
                            color = PaoAnthraciteMuted
                        )

                        // Offline note / warning
                        if (syncState != SyncState.SINCRONIZADO || pendingCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                color = StatusAmber.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusAmber.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = StatusAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "As informações podem estar desatualizadas, sincronize-as. Os dados locais estão protegidos em cache.",
                                        fontSize = 11.sp,
                                        color = PaoAnthracite
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons Inside Card
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PaoPrimaryButton(
                                text = "Sincronizar Agora",
                                onClick = { syncManager.triggerSync() },
                                isLoading = syncState == SyncState.SINCRONIZANDO,
                                modifier = Modifier.weight(1f),
                                testTag = "force_sync_btn"
                            )

                            if (failedCount > 0) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            scope.launch { syncManager.retryFailedOperations() }
                                        }
                                        .testTag("retry_failed_sync_btn"),
                                    color = StatusRed.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusRed.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = StatusRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Reenviar Falhas ($failedCount)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Selector
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = PaoRed,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = PaoRed,
                            height = 3.dp
                        )
                    },
                    divider = {
                        HorizontalDivider(color = PaoBorderLight)
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "Fila de Envio (${queueItems.size})",
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) PaoRed else PaoAnthraciteMuted
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "Logs em Tempo Real (${syncLogs.size})",
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) PaoRed else PaoAnthraciteMuted
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (selectedTab == 0) {
                        // Fila de Envio
                        if (queueItems.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = StatusGreen,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Fila Vazia e Sincronizada",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PaoAnthracite
                                    )
                                    Text(
                                        text = "Não há operações pendentes no momento. Todos os dados estão no Supabase.",
                                        fontSize = 12.sp,
                                        color = PaoAnthraciteMuted,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(queueItems, key = { it.id }) { item ->
                                    QueueItemCard(item)
                                }
                            }
                        }
                    } else {
                        // Logs em Tempo Real
                        if (syncLogs.isEmpty()) {
                            Text(
                                text = "Nenhum log registrado ainda.",
                                fontSize = 13.sp,
                                color = PaoAnthraciteMuted,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(syncLogs, key = { it.id }) { logItem ->
                                    LogEntryCard(logItem)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                PaoSecondaryButton(
                    text = "Fechar",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "dismiss_sync_dialog_btn"
                )
            }
        }
    }
}

@Composable
fun QueueItemCard(item: SyncQueueEntity) {
    val friendlyType = when (item.entityType) {
        "ATTENDANCE" -> "Registro de Atendimento"
        "ATTENDANCE_MEMBERS" -> "Integrantes da Dupla/Grupo"
        "ATTENDANCE_AUDIT" -> "Auditoria de Correção"
        "SHIFT" -> "Abertura de Turno"
        "SHIFT_CLOSE" -> "Fechamento de Turno"
        "QUEUE_EVENT" -> "Atualização de Fila"
        "HOSTER_INSERT" -> "Cadastro de Hoster"
        "HOSTER_UPDATE" -> "Atualização de Hoster"
        "HOSTER_ACTIVATION" -> "Primeiro Acesso Hoster"
        "HOSTER_DELETE" -> "Exclusão de Hoster"
        else -> item.entityType
    }

    val (badgeBg, badgeFg, badgeText) = when (item.status) {
        "FAILED" -> Triple(StatusRed.copy(alpha = 0.15f), StatusRed, "FALHA")
        "SYNCING" -> Triple(StatusBlue.copy(alpha = 0.15f), StatusBlue, "ENVIANDO")
        else -> Triple(StatusAmber.copy(alpha = 0.15f), StatusAmber, "PENDENTE")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (item.status == "FAILED") StatusRed.copy(alpha = 0.4f) else PaoBorderLight, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = friendlyType,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaoAnthracite
                )

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ID: ${item.entityId.take(12)} • Op: ${item.operation}",
                    fontSize = 11.sp,
                    color = PaoAnthraciteMuted,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Tentativas: ${item.attempts}",
                    fontSize = 11.sp,
                    color = PaoAnthraciteMuted
                )
            }

            if (!item.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = StatusRed.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = StatusRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.lastError,
                            fontSize = 11.sp,
                            color = StatusRed,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryCard(logItem: SyncLogEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (logItem.isError) StatusRed.copy(alpha = 0.08f) else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (logItem.isError) StatusRed.copy(alpha = 0.3f) else PaoBorderLight
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = DateTimeUtils.formatTime(logItem.timestamp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (logItem.isError) StatusRed else PaoAnthraciteMuted,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = logItem.message,
                fontSize = 12.sp,
                color = if (logItem.isError) StatusRed else PaoAnthracite,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

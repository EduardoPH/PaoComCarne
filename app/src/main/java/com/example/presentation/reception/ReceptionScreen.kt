package com.example.presentation.reception

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AttendanceWithMembers
import com.example.domain.model.Shift
import com.example.domain.util.DateTimeUtils
import com.example.presentation.attendance.CorrectionDialog
import com.example.presentation.attendance.NewAttendanceDialog
import com.example.presentation.components.PaoPrimaryButton
import com.example.presentation.components.SyncStatusPill
import com.example.presentation.queue.UpdateQueueDialog
import com.example.presentation.shift.CloseShiftDialog
import com.example.presentation.shift.ShiftSelectDialog
import com.example.ui.theme.PaoAnthracite
import com.example.ui.theme.PaoAnthraciteMuted
import com.example.ui.theme.PaoBackground
import com.example.ui.theme.PaoBorder
import com.example.ui.theme.PaoBorderLight
import com.example.ui.theme.PaoCream
import com.example.ui.theme.PaoCreamLight
import com.example.ui.theme.PaoRed
import com.example.ui.theme.PaoRedDark
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed

@Composable
fun ReceptionScreen(
    viewModel: ReceptionViewModel,
    onNavigateToInspection: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToShift: () -> Unit,
    onShiftClosed: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val hoster by viewModel.currentHoster.collectAsState()
    val shift by viewModel.currentShift.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val queue by viewModel.latestQueue.collectAsState()
    val attendances by viewModel.shiftAttendances.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage, uiState.errorMessage) {
        val msg = uiState.feedbackMessage ?: uiState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        containerColor = PaoBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            // Header Bar: Hoster, Period, Opening Time & Sync Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hoster?.name ?: "Recepção",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaoAnthracite
                    )
                    if (shift != null) {
                        Text(
                            text = "Turno ${shift?.period?.displayName} • Aberto às ${DateTimeUtils.formatTime(shift?.openedAt ?: 0)}",
                            fontSize = 12.sp,
                            color = PaoAnthraciteMuted
                        )
                    } else {
                        Text(
                            text = "Nenhum turno em andamento",
                            fontSize = 12.sp,
                            color = StatusAmber,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                SyncStatusPill(
                    syncState = syncState,
                    pendingCount = pendingCount,
                    onClick = { viewModel.triggerSync() }
                )
            }

            HorizontalDivider(color = PaoBorderLight, modifier = Modifier.padding(bottom = 14.dp))

            if (shift == null) {
                // No active shift state: prompt to open shift
                NoActiveShiftCard(
                    hosterName = hoster?.name ?: "",
                    onOpenShift = { viewModel.setShowPeriodSelect(true) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. O ELEMENTO MAIS IMPORTANTE: Botão Gigante Destacado "Novo Atendimento"
                    item {
                        Button(
                            onClick = { viewModel.setShowNewAttendance(true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(76.dp)
                                .testTag("btn_novo_atendimento"),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PaoRed,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape),
                                    color = Color.White.copy(alpha = 0.2f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Novo Atendimento",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "Solo • Dupla • Grupo",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PaoCream
                                    )
                                }
                            }
                        }
                    }

                    // 2. Painel Compacto da Fila de Espera
                    item {
                        WaitlistCompactCard(
                            duos = queue?.waitingDuos ?: 0,
                            groups = queue?.waitingGroups ?: 0,
                            lastUpdate = queue?.createdAt,
                            onUpdateQueue = { viewModel.setShowUpdateQueue(true) }
                        )
                    }

                    // 3. Resumo Rápido do Turno Atual
                    item {
                        val totalAtt = attendances.size
                        val totalPpl = attendances.sumOf { it.attendance.totalMembers }
                        val totalNew = attendances.sumOf { it.attendance.newMembers }
                        val totalRet = attendances.sumOf { it.attendance.returningMembers }

                        ShiftQuickStatsCard(
                            totalAttendances = totalAtt,
                            totalPeople = totalPpl,
                            newClients = totalNew,
                            returningClients = totalRet
                        )
                    }

                    // 4. Último Atendimento Registrado e Ação de Correção
                    item {
                        val lastAttendance = attendances.lastOrNull()
                        if (lastAttendance != null) {
                            LastAttendanceCard(
                                item = lastAttendance,
                                onCorrect = { viewModel.setShowCorrection(true) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (uiState.showPeriodSelectDialog) {
        ShiftSelectDialog(
            hosterName = hoster?.name ?: "",
            onSelectPeriod = { viewModel.openShift(it) },
            onCancel = { viewModel.setShowPeriodSelect(false) }
        )
    }

    if (uiState.showNewAttendanceDialog && shift != null) {
        NewAttendanceDialog(
            shift = shift!!,
            hosterName = hoster?.name ?: "",
            onDismiss = { viewModel.setShowNewAttendance(false) },
            onConfirm = { type, total, newCount, returningCount, isPreferential, members ->
                viewModel.registerAttendance(type, total, newCount, returningCount, isPreferential, members)
            }
        )
    }

    if (uiState.showUpdateQueueDialog && shift != null) {
        UpdateQueueDialog(
            initialDuos = queue?.waitingDuos ?: 0,
            initialGroups = queue?.waitingGroups ?: 0,
            onDismiss = { viewModel.setShowUpdateQueue(false) },
            onSave = { duos, groups ->
                viewModel.updateQueue(duos, groups)
            }
        )
    }

    if (uiState.showCorrectionDialog && shift != null) {
        val lastAttendance = attendances.lastOrNull()
        if (lastAttendance != null) {
            CorrectionDialog(
                attendanceWithMembers = lastAttendance,
                shift = shift!!,
                editorHosterName = hoster?.name ?: "",
                onDismiss = { viewModel.setShowCorrection(false) },
                onConfirmCorrection = { newType, newTotal, newNewCount, newReturningCount, newIsPreferential, newMembers ->
                    viewModel.correctLastAttendance(newType, newTotal, newNewCount, newReturningCount, newIsPreferential, newMembers)
                }
            )
        }
    }

    if (uiState.showCloseShiftDialog && shift != null) {
        val totalAtt = attendances.size
        val totalPpl = attendances.sumOf { it.attendance.totalMembers }
        val totalNew = attendances.sumOf { it.attendance.newMembers }
        val totalRet = attendances.sumOf { it.attendance.returningMembers }

        CloseShiftDialog(
            shift = shift!!,
            totalAttendances = totalAtt,
            totalPeople = totalPpl,
            newClients = totalNew,
            returningClients = totalRet,
            onDismiss = { viewModel.setShowCloseShift(false) },
            onConfirmClose = { viewModel.closeShift(onShiftClosed) }
        )
    }
}

@Composable
private fun NoActiveShiftCard(
    hosterName: String,
    onOpenShift: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape),
                color = PaoCream
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = PaoRed,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Turno Não Iniciado",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PaoAnthracite
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Olá, $hosterName. Para começar a registrar atendimentos e controlar a fila de espera, abra o turno atual.",
                fontSize = 13.sp,
                color = PaoAnthraciteMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            PaoPrimaryButton(
                text = "Abrir Turno Agora",
                onClick = onOpenShift,
                testTag = "open_shift_button"
            )
        }
    }
}

@Composable
private fun WaitlistCompactCard(
    duos: Int,
    groups: Int,
    lastUpdate: Long?,
    onUpdateQueue: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = PaoRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Fila de Espera",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaoAnthracite
                    )
                }

                Text(
                    text = if (lastUpdate != null) "Atualizado às ${DateTimeUtils.formatTime(lastUpdate)}" else "Sem atualização",
                    fontSize = 11.sp,
                    color = PaoAnthraciteMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Duplas
                QueueCounterBox(
                    label = "Duplas",
                    count = duos,
                    icon = Icons.Default.Group,
                    modifier = Modifier.weight(1f)
                )

                // Grupos
                QueueCounterBox(
                    label = "Grupos",
                    count = groups,
                    icon = Icons.Default.Groups,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onUpdateQueue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("btn_atualizar_fila"),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PaoAnthracite),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PaoAnthracite)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Atualizar Fila",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun QueueCounterBox(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp)),
        color = if (count > 0) PaoCream else PaoCreamLight,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (count > 0) PaoRed.copy(alpha = 0.4f) else PaoBorderLight
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (count > 0) PaoRed else PaoAnthraciteMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = PaoAnthracite
                )
            }
            Text(
                text = count.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (count > 0) PaoRed else PaoAnthraciteMuted
            )
        }
    }
}

@Composable
private fun ShiftQuickStatsCard(
    totalAttendances: Int,
    totalPeople: Int,
    newClients: Int,
    returningClients: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PaoBorderLight, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Resumo do Turno Atual",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PaoAnthracite
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatChip(label = "Atendimentos", value = totalAttendances.toString())
                StatChip(label = "Pessoas", value = totalPeople.toString())
                StatChip(label = "Novos", value = newClients.toString(), valueColor = StatusGreen)
                StatChip(label = "Recorrentes", value = returningClients.toString())
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    valueColor: Color = PaoAnthracite
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(text = label, fontSize = 11.sp, color = PaoAnthraciteMuted)
    }
}

@Composable
private fun LastAttendanceCard(
    item: AttendanceWithMembers,
    onCorrect: () -> Unit
) {
    val att = item.attendance
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Último Atendimento Registrado",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaoAnthracite
                    )
                    if (att.isPreferential) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = StatusAmber.copy(alpha = 0.18f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusAmber.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "★ PREFERENCIAL",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusAmber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (att.isEdited) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = StatusAmber.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Corrigido",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusAmber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = DateTimeUtils.formatTime(att.createdAt),
                    fontSize = 12.sp,
                    color = PaoAnthraciteMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${att.type.displayName} • ${att.totalMembers} pessoa(s) (${att.newMembers} novo, ${att.returningMembers} recorrente)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = PaoAnthracite
            )

            val newMembersWithOrigin = item.members.filter { it.classification.name == "NOVO" && !it.origin.isNullOrBlank() }
            if (newMembersWithOrigin.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                val originsText = newMembersWithOrigin.joinToString(", ") { "Int ${it.memberNumber}: ${it.origin}" }
                Text(
                    text = "Origens: $originsText",
                    fontSize = 11.sp,
                    color = PaoAnthraciteMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onCorrect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("btn_corrigir_ultimo"),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusAmber),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusAmber)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Corrigir Último Atendimento",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

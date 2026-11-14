package com.example.presentation.shift

import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.PaoComCarneApp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.util.DateTimeUtils
import com.example.presentation.components.PaoPrimaryButton
import com.example.presentation.components.PaoSecondaryButton
import com.example.presentation.components.SyncStatusPill
import com.example.presentation.reception.ReceptionViewModel
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

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun ShiftScreen(
    viewModel: ReceptionViewModel,
    onLogout: () -> Unit,
    onShiftClosed: () -> Unit = onLogout
) {
    val uiState by viewModel.uiState.collectAsState()
    val hoster by viewModel.currentHoster.collectAsState()
    val shift by viewModel.currentShift.collectAsState()
    val attendances by viewModel.shiftAttendances.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val deviceIdentityManager = remember {
        (context.applicationContext as PaoComCarneApp).deviceIdentityManager
    }
    var deviceDisplayName by remember { mutableStateOf(deviceIdentityManager.getDeviceDisplayName()) }
    var showEditDeviceDialog by remember { mutableStateOf(false) }

    val totalAtt = attendances.size
    val totalPeople = attendances.sumOf { it.attendance.totalMembers }
    val newCount = attendances.sumOf { it.attendance.newMembers }
    val returningCount = attendances.sumOf { it.attendance.returningMembers }
    val soloCount = attendances.count { it.attendance.type.name == "SOLO" }
    val duplaCount = attendances.count { it.attendance.type.name == "DUPLA" }
    val grupoCount = attendances.count { it.attendance.type.name == "GRUPO" }

    if (uiState.showCloseShiftDialog && shift != null) {
        CloseShiftDialog(
            shift = shift!!,
            totalAttendances = totalAtt,
            totalPeople = totalPeople,
            newClients = newCount,
            returningClients = returningCount,
            onDismiss = { viewModel.setShowCloseShift(false) },
            onConfirmClose = { viewModel.closeShift(onShiftClosed) }
        )
    }

    Scaffold(
        containerColor = PaoBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dados do Turno",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaoAnthracite
                    )
                    Text(
                        text = "Informações do hoster e encerramento",
                        fontSize = 12.sp,
                        color = PaoAnthraciteMuted
                    )
                }

                SyncStatusPill(
                    syncState = syncState,
                    pendingCount = pendingCount,
                    onClick = { viewModel.triggerSync() }
                )
            }

            HorizontalDivider(color = PaoBorderLight, modifier = Modifier.padding(bottom = 16.dp))

            // Hoster Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        color = PaoCream
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = PaoRed,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hoster?.name ?: "Hoster",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        Text(
                            text = "Hoster em atividade neste dispositivo",
                            fontSize = 12.sp,
                            color = PaoAnthraciteMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Device Identity Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PaoBorderLight, RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = PaoRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Dispositivo identificado nos logs",
                                fontSize = 11.sp,
                                color = PaoAnthraciteMuted
                            )
                            Text(
                                text = deviceDisplayName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                        }
                    }
                    IconButton(onClick = { showEditDeviceDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar nome do dispositivo",
                            tint = PaoRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (shift == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Nenhum Turno Ativo", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Inicie um turno na tela Recepção para começar os trabalhos.",
                            fontSize = 12.sp,
                            color = PaoAnthraciteMuted
                        )
                    }
                }
            } else {
                // Shift Details Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Situação do Turno Atual",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        DetailLine("Período", shift?.period?.displayName ?: "")
                        DetailLine("Data de Abertura", DateTimeUtils.formatDate(shift?.openedAt ?: 0))
                        DetailLine("Horário de Abertura", DateTimeUtils.formatTime(shift?.openedAt ?: 0))
                        DetailLine("Situação", "Aberto", StatusGreen)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = PaoBorderLight)

                        Text(
                            text = "Balanço do Turno:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        DetailLine("Atendimentos Registrados", totalAtt.toString())
                        DetailLine("Atendimentos Solo", soloCount.toString(), PaoRed)
                        DetailLine("Atendimentos Dupla", duplaCount.toString(), PaoAnthracite)
                        DetailLine("Atendimentos Grupo", grupoCount.toString(), StatusAmber)
                        DetailLine("Pessoas Atendidas", totalPeople.toString())
                        DetailLine("Novos Clientes", newCount.toString(), StatusGreen)
                        DetailLine("Clientes Recorrentes", returningCount.toString(), PaoAnthracite)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Encerrar Turno Button
                Button(
                    onClick = { viewModel.setShowCloseShift(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_encerrar_turno_action"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusRed,
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.LockClock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Encerrar Turno", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Logout action (trocar hoster)
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(bottom = 12.dp)
                    .testTag("btn_logout"),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PaoBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PaoAnthraciteMuted)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Trocar de Hoster / Sair", fontSize = 13.sp)
            }
        }
    }

    if (showEditDeviceDialog) {
        var draftName by remember(deviceDisplayName) { mutableStateOf(deviceIdentityManager.getFriendlyName().orEmpty()) }
        AlertDialog(
            onDismissRequest = { showEditDeviceDialog = false },
            containerColor = Color.White,
            title = { Text("Nome do Dispositivo", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Defina um apelido para identificar este aparelho nos logs do sistema (ex.: Recepção 1, Tablet Entrada).",
                        fontSize = 12.sp,
                        color = PaoAnthraciteMuted
                    )
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = { Text("Apelido do dispositivo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deviceIdentityManager.setFriendlyName(draftName)
                        deviceDisplayName = deviceIdentityManager.getDeviceDisplayName()
                        showEditDeviceDialog = false
                    }
                ) {
                    Text("Salvar", fontWeight = FontWeight.Bold, color = PaoRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDeviceDialog = false }) {
                    Text("Cancelar", color = PaoAnthraciteMuted)
                }
            }
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String, valueColor: Color = PaoAnthracite) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = PaoAnthraciteMuted)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

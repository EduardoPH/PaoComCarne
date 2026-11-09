package com.example.presentation.inspection

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AttendanceWithMembers
import com.example.domain.util.DateTimeUtils
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

@Composable
fun InspectionScreen(
    viewModel: ReceptionViewModel
) {
    val shift by viewModel.currentShift.collectAsState()
    val attendances by viewModel.shiftAttendances.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()

    val totalAtt = attendances.size
    val totalPeople = attendances.sumOf { it.attendance.totalMembers }
    val newCount = attendances.sumOf { it.attendance.newMembers }
    val returningCount = attendances.sumOf { it.attendance.returningMembers }

    Scaffold(
        containerColor = PaoBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
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
                        text = "Inspeção ao Vivo",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaoAnthracite
                    )
                    Text(
                        text = if (shift != null) "Acompanhamento exclusivo do Turno ${shift?.period?.displayName}" else "Nenhum turno aberto",
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

            HorizontalDivider(color = PaoBorderLight, modifier = Modifier.padding(bottom = 12.dp))

            if (shift == null) {
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
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = StatusAmber,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Sem Turno Ativo",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A inspeção ao vivo acompanha somente atendimentos de turnos abertos. Abra um turno na aba Recepção para acompanhar em tempo real.",
                            fontSize = 13.sp,
                            color = PaoAnthraciteMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                // Totalizadores ao Vivo
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Totalizadores do Turno Atual",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            InspectionStatBox(
                                label = "Atendimentos",
                                value = totalAtt.toString(),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            InspectionStatBox(
                                label = "Total Pessoas",
                                value = totalPeople.toString(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            InspectionStatBox(
                                label = "Clientes Novos",
                                value = newCount.toString(),
                                valueColor = StatusGreen,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            InspectionStatBox(
                                label = "Recorrentes",
                                value = returningCount.toString(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Histórico Cronológico (${attendances.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaoAnthracite
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (attendances.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum atendimento registrado ainda neste turno.",
                            fontSize = 13.sp,
                            color = PaoAnthraciteMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(attendances) { index, item ->
                            val isLast = index == attendances.size - 1
                            InspectionAttendanceCard(
                                item = item,
                                index = index + 1,
                                isLast = isLast,
                                onCorrect = { viewModel.setShowCorrection(true) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectionStatBox(
    label: String,
    value: String,
    valueColor: Color = PaoAnthracite,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp)),
        color = PaoCreamLight,
        border = androidx.compose.foundation.BorderStroke(1.dp, PaoBorderLight),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = PaoAnthraciteMuted
            )
        }
    }
}

@Composable
private fun InspectionAttendanceCard(
    item: AttendanceWithMembers,
    index: Int,
    isLast: Boolean,
    onCorrect: () -> Unit
) {
    val att = item.attendance
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PaoBorderLight, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color = PaoCream
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = index.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoRed
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = att.type.displayName,
                        fontSize = 14.sp,
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
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
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
                                text = "Editado",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusAmber,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = DateTimeUtils.formatTime(att.createdAt),
                    fontSize = 12.sp,
                    color = PaoAnthraciteMuted,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Total: ${att.totalMembers} pessoa(s) • ${att.newMembers} novo(s) • ${att.returningMembers} recorrente(s)",
                fontSize = 12.sp,
                color = PaoAnthracite
            )

            // Individual origins list for new members
            val newMembers = item.members.filter { it.classification.name == "NOVO" }
            if (newMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    newMembers.forEach { m ->
                        Text(
                            text = "• Integrante ${m.memberNumber}: ${m.origin ?: "Sem origem"}",
                            fontSize = 11.sp,
                            color = PaoAnthraciteMuted
                        )
                    }
                }
            }

            if (isLast) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onCorrect,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusAmber)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = StatusAmber,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Corrigir este último",
                            fontSize = 11.sp,
                            color = StatusAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

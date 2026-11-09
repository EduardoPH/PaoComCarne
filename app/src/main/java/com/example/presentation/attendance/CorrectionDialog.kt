package com.example.presentation.attendance

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.AttendanceMember
import com.example.domain.model.AttendanceType
import com.example.domain.model.AttendanceWithMembers
import com.example.domain.model.ClientClassification
import com.example.domain.model.ClientOrigins
import com.example.domain.model.Shift
import com.example.presentation.components.NumericStepper
import com.example.presentation.components.PaoPrimaryButton
import com.example.presentation.components.PaoSecondaryButton
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
fun CorrectionDialog(
    attendanceWithMembers: AttendanceWithMembers,
    shift: Shift,
    editorHosterName: String,
    onDismiss: () -> Unit,
    onConfirmCorrection: (
        newType: AttendanceType,
        newTotal: Int,
        newNewCount: Int,
        newReturningCount: Int,
        newIsPreferential: Boolean,
        newMembers: List<AttendanceMember>
    ) -> Unit
) {
    val initial = attendanceWithMembers.attendance
    var step by remember { mutableIntStateOf(1) } // 1: Edit, 2: Resumo e confirmação
    var currentType by remember { mutableStateOf(initial.type) }
    var isPreferential by remember { mutableStateOf(initial.isPreferential) }
    var groupMemberCount by remember {
        mutableIntStateOf(if (initial.type == AttendanceType.GRUPO) initial.totalMembers else 3)
    }

    val membersDraft = remember {
        val list = mutableStateListOf<MemberDraft>()
        attendanceWithMembers.members.forEach { m ->
            list.add(
                MemberDraft(
                    memberNumber = m.memberNumber,
                    classification = m.classification,
                    origin = m.origin
                )
            )
        }
        list
    }

    var isSubmitting by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun adjustMemberCount(count: Int) {
        val currentSize = membersDraft.size
        if (count > currentSize) {
            for (i in (currentSize + 1)..count) {
                membersDraft.add(MemberDraft(memberNumber = i))
            }
        } else if (count < currentSize) {
            while (membersDraft.size > count) {
                membersDraft.removeAt(membersDraft.size - 1)
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
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
                    .padding(top = 12.dp, bottom = 72.dp)
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 1) {
                        IconButton(
                            onClick = { step -= 1 },
                            enabled = !isSubmitting
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = PaoAnthracite
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onDismiss,
                            enabled = !isSubmitting
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = PaoAnthracite
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = StatusAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Corrigir Último Atendimento",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                        }
                        Text(
                            text = "Auditoria será registrada para este atendimento",
                            fontSize = 11.sp,
                            color = PaoAnthraciteMuted
                        )
                    }

                    Box(modifier = Modifier.size(48.dp))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = PaoBorderLight)

                if (validationError != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = StatusRed.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusRed.copy(alpha = 0.35f))
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = StatusRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = validationError ?: "", fontSize = 12.sp, color = StatusRed)
                        }
                    }
                }

                if (step == 1) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Text(
                                text = "Formato do Atendimento",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Preferential Toggle Card
                            PreferentialToggleCard(
                                isPreferential = isPreferential,
                                onToggle = { isPreferential = it }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Solo
                                TypeChip(
                                    title = "Solo",
                                    isSelected = currentType == AttendanceType.SOLO,
                                    onClick = {
                                        currentType = AttendanceType.SOLO
                                        adjustMemberCount(1)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                // Dupla
                                TypeChip(
                                    title = "Dupla",
                                    isSelected = currentType == AttendanceType.DUPLA,
                                    onClick = {
                                        currentType = AttendanceType.DUPLA
                                        adjustMemberCount(2)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                // Grupo
                                TypeChip(
                                    title = "Grupo",
                                    isSelected = currentType == AttendanceType.GRUPO,
                                    onClick = {
                                        currentType = AttendanceType.GRUPO
                                        adjustMemberCount(groupMemberCount)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (currentType == AttendanceType.GRUPO) {
                            item {
                                NumericStepper(
                                    value = groupMemberCount,
                                    onValueChange = {
                                        groupMemberCount = it
                                        adjustMemberCount(it)
                                    },
                                    minValue = 3,
                                    maxValue = 30,
                                    label = "Integrantes do Grupo"
                                )
                            }
                        }

                        item {
                            Text(
                                text = "Classificação dos Integrantes",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite,
                                modifier = Modifier.padding(top = 8.dp)
                            )

                            // Quick actions
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                QuickActionChip(
                                    text = "Marcar todos Novos",
                                    onClick = {
                                        for (i in 0 until membersDraft.size) {
                                            membersDraft[i] = membersDraft[i].copy(classification = ClientClassification.NOVO)
                                        }
                                    },
                                    testTag = "correction_quick_mark_all_new"
                                )
                                QuickActionChip(
                                    text = "Indicação p/ novos",
                                    onClick = {
                                        for (i in 0 until membersDraft.size) {
                                            if (membersDraft[i].classification == ClientClassification.NOVO) {
                                                membersDraft[i] = membersDraft[i].copy(origin = "Indicação")
                                            }
                                        }
                                    },
                                    testTag = "correction_quick_origin_indicacao"
                                )
                                QuickActionChip(
                                    text = "Todos Recorrentes",
                                    onClick = {
                                        for (i in 0 until membersDraft.size) {
                                            membersDraft[i] = membersDraft[i].copy(classification = ClientClassification.RECORRENTE, origin = null)
                                        }
                                    },
                                    testTag = "correction_quick_mark_all_rec"
                                )
                            }
                        }

                        itemsIndexed(membersDraft) { index, draft ->
                            MemberCardEditable(
                                draft = draft,
                                onClassificationChange = { newClass ->
                                    membersDraft[index] = draft.copy(
                                        classification = newClass,
                                        origin = if (newClass == ClientClassification.RECORRENTE) null else draft.origin
                                    )
                                },
                                onOriginChange = { newOrigin ->
                                    membersDraft[index] = draft.copy(origin = newOrigin)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PaoSecondaryButton(
                            text = "Cancelar",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            testTag = "correction_cancel_button"
                        )

                        PaoPrimaryButton(
                            text = "Avançar para Resumo da Correção",
                            onClick = {
                                var missingOrigin = false
                                for (m in membersDraft) {
                                    if (m.classification == ClientClassification.NOVO && m.origin.isNullOrBlank()) {
                                        missingOrigin = true
                                        break
                                    }
                                }
                                if (missingOrigin) {
                                    validationError = "Selecione a origem de todos os clientes novos."
                                } else {
                                    validationError = null
                                    step = 2
                                }
                            },
                            modifier = Modifier.weight(1.5f),
                            testTag = "correction_next_button"
                        )
                    }
                } else {
                    // Step 2: Resumo e confirmação da correção
                    val total = membersDraft.size
                    val newCount = membersDraft.count { it.classification == ClientClassification.NOVO }
                    val returningCount = membersDraft.count { it.classification == ClientClassification.RECORRENTE }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Confirmação da Correção",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        Text(
                            text = "O histórico anterior será preservado na auditoria interna.",
                            fontSize = 12.sp,
                            color = PaoAnthraciteMuted,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Card rolável no step 2 de correção
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, PaoBorder, RoundedCornerShape(14.dp)),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text(
                                                text = "Novos Valores:",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PaoRed
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(text = "Tipo: ${currentType.displayName}", fontSize = 13.sp)
                                            Text(
                                                text = "Atendimento Preferencial: ${if (isPreferential) "Sim (Preferencial)" else "Não"}",
                                                fontSize = 13.sp,
                                                fontWeight = if (isPreferential) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isPreferential) StatusAmber else PaoAnthracite
                                            )
                                            Text(text = "Total de Pessoas: $total", fontSize = 13.sp)
                                            Text(text = "Novos: $newCount | Recorrentes: $returningCount", fontSize = 13.sp)
                                            Text(text = "Hoster que corrigiu: $editorHosterName", fontSize = 13.sp)

                                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = PaoBorderLight)

                                            Text(
                                                text = "Valor Anterior Registrado:",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PaoAnthraciteMuted
                                            )
                                            Text(
                                                text = "${initial.type.displayName}${if (initial.isPreferential) " [PREFERENCIAL]" else ""} (${initial.totalMembers}p: ${initial.newMembers} novos, ${initial.returningMembers} rec)",
                                                fontSize = 12.sp,
                                                color = PaoAnthraciteMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PaoSecondaryButton(
                                text = "Voltar",
                                onClick = { step = 1 },
                                modifier = Modifier.weight(1f)
                            )

                            PaoPrimaryButton(
                                text = "Confirmar Correção",
                                onClick = {
                                    isSubmitting = true
                                    val domainMembers = membersDraft.map { draft ->
                                        AttendanceMember(
                                            id = "",
                                            attendanceId = initial.id,
                                            memberNumber = draft.memberNumber,
                                            classification = draft.classification,
                                            origin = if (draft.classification == ClientClassification.NOVO) draft.origin else null
                                        )
                                    }
                                    onConfirmCorrection(currentType, total, newCount, returningCount, isPreferential, domainMembers)
                                },
                                isLoading = isSubmitting,
                                modifier = Modifier.weight(1.4f),
                                testTag = "confirm_correction_button"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = if (isSelected) PaoRed else PaoCreamLight,
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else PaoAnthracite
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberCardEditable(
    draft: MemberDraft,
    onClassificationChange: (ClientClassification) -> Unit,
    onOriginChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PaoBorderLight, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Integrante ${draft.memberNumber}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PaoAnthracite
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isRecorrente = draft.classification == ClientClassification.RECORRENTE
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClassificationChange(ClientClassification.RECORRENTE) },
                    color = if (isRecorrente) PaoAnthracite else PaoCreamLight,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Recorrente",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRecorrente) Color.White else PaoAnthracite
                        )
                    }
                }

                val isNovo = draft.classification == ClientClassification.NOVO
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClassificationChange(ClientClassification.NOVO) },
                    color = if (isNovo) PaoRed else PaoCreamLight,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Novo Cliente",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isNovo) Color.White else PaoAnthracite
                        )
                    }
                }
            }

            if (draft.classification == ClientClassification.NOVO) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Origem:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PaoAnthracite
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ClientOrigins.ALL.forEach { origin ->
                        val isSelected = draft.origin == origin
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOriginChange(origin) },
                            color = if (isSelected) PaoRed.copy(alpha = 0.15f) else PaoCreamLight,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) PaoRed else PaoBorderLight
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = origin,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) PaoRed else PaoAnthracite,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

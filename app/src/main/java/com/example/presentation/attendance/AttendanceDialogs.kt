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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import java.util.UUID

data class MemberDraft(
    val memberNumber: Int,
    var classification: ClientClassification = ClientClassification.RECORRENTE,
    var origin: String? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewAttendanceDialog(
    shift: Shift,
    hosterName: String,
    onDismiss: () -> Unit,
    onConfirm: (
        type: AttendanceType,
        total: Int,
        newCount: Int,
        returningCount: Int,
        isPreferential: Boolean,
        members: List<AttendanceMember>
    ) -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // 1: Type selection, 2: Member classification, 3: Summary
    var selectedType by remember { mutableStateOf<AttendanceType?>(null) }
    var groupMemberCount by remember { mutableIntStateOf(3) }
    var isPreferential by remember { mutableStateOf(false) }
    val membersDraft = remember { mutableStateListOf<MemberDraft>() }
    var isSubmitting by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun initializeMembers(type: AttendanceType, count: Int) {
        membersDraft.clear()
        for (i in 1..count) {
            membersDraft.add(MemberDraft(memberNumber = i))
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
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 1) {
                        IconButton(
                            onClick = {
                                validationError = null
                                step -= 1
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.testTag("attendance_back_button")
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
                            enabled = !isSubmitting,
                            modifier = Modifier.testTag("attendance_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = PaoAnthracite
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when (step) {
                                1 -> "1. Formato"
                                2 -> "2. Integrantes"
                                else -> "3. Resumo & Confirmação"
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (s in 1..3) {
                                val isCurrentOrPassed = s <= step
                                Surface(
                                    modifier = Modifier
                                        .width(if (s == step) 28.dp else 14.dp)
                                        .height(4.dp),
                                    shape = RoundedCornerShape(2.dp),
                                    color = if (isCurrentOrPassed) PaoRed else PaoBorder
                                ) {}
                            }
                        }
                    }

                    Box(modifier = Modifier.size(48.dp)) // balance space
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = PaoBorderLight
                )

                if (validationError != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = StatusRed.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusRed.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = validationError ?: "",
                                fontSize = 13.sp,
                                color = StatusRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Step Content
                when (step) {
                    1 -> {
                        // Step 1: Select Type & Preferential
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Qual o formato do atendimento?",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )

                            // Preferential Switch Card (Novo: Marcar solo, dupla ou grupo como preferencial)
                            PreferentialToggleCard(
                                isPreferential = isPreferential,
                                onToggle = { isPreferential = it }
                            )

                            // Solo Card
                            AttendanceTypeCard(
                                title = "Solo",
                                subtitle = "1 pessoa",
                                icon = Icons.Default.Person,
                                isSelected = selectedType == AttendanceType.SOLO,
                                onClick = {
                                    selectedType = AttendanceType.SOLO
                                    initializeMembers(AttendanceType.SOLO, 1)
                                    step = 2
                                },
                                testTag = "type_solo_card"
                            )

                            // Dupla Card
                            AttendanceTypeCard(
                                title = "Dupla",
                                subtitle = "2 pessoas",
                                icon = Icons.Default.Group,
                                isSelected = selectedType == AttendanceType.DUPLA,
                                onClick = {
                                    selectedType = AttendanceType.DUPLA
                                    initializeMembers(AttendanceType.DUPLA, 2)
                                    step = 2
                                },
                                testTag = "type_dupla_card"
                            )

                            // Grupo Card
                            AttendanceTypeCard(
                                title = "Grupo",
                                subtitle = "3 ou mais pessoas",
                                icon = Icons.Default.Groups,
                                isSelected = selectedType == AttendanceType.GRUPO,
                                onClick = {
                                    selectedType = AttendanceType.GRUPO
                                },
                                testTag = "type_grupo_card"
                            )

                            if (selectedType == AttendanceType.GRUPO) {
                                Spacer(modifier = Modifier.height(4.dp))
                                NumericStepper(
                                    value = groupMemberCount,
                                    onValueChange = { groupMemberCount = it },
                                    minValue = 3,
                                    maxValue = 30,
                                    label = "Quantidade de integrantes do grupo",
                                    testTagPrefix = "group_members"
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                PaoPrimaryButton(
                                    text = "Continuar com $groupMemberCount pessoas",
                                    onClick = {
                                        initializeMembers(AttendanceType.GRUPO, groupMemberCount)
                                        step = 2
                                    },
                                    testTag = "continue_group_button"
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Spacer(modifier = Modifier.height(24.dp))
                            PaoSecondaryButton(
                                text = "Cancelar",
                                onClick = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding(),
                                testTag = "cancel_attendance_step1"
                            )
                        }
                    }

                    2 -> {
                        // Step 2: Individual classification of each member
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "Classificação individual dos integrantes",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                            Text(
                                text = "Defina se cada pessoa é nova ou recorrente e indique a origem para os novos.",
                                fontSize = 12.sp,
                                color = PaoAnthraciteMuted,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Atalhos operacionais rápidos (Agilidade para recepção)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                color = PaoCreamLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, PaoBorderLight),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Ações rápidas:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PaoAnthraciteMuted
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // 1. Marcar todos novos
                                        QuickActionChip(
                                            text = "Marcar todos Novos",
                                            onClick = {
                                                for (i in 0 until membersDraft.size) {
                                                    membersDraft[i] = membersDraft[i].copy(
                                                        classification = ClientClassification.NOVO
                                                    )
                                                }
                                            },
                                            testTag = "quick_mark_all_new"
                                        )

                                        // 2. Indicação para novos
                                        QuickActionChip(
                                            text = "Indicação p/ novos",
                                            onClick = {
                                                for (i in 0 until membersDraft.size) {
                                                    if (membersDraft[i].classification == ClientClassification.NOVO) {
                                                        membersDraft[i] = membersDraft[i].copy(origin = "Indicação")
                                                    }
                                                }
                                            },
                                            testTag = "quick_origin_indicacao_all_new"
                                        )

                                        // 3. Todos Recorrentes
                                        QuickActionChip(
                                            text = "Todos Recorrentes",
                                            onClick = {
                                                for (i in 0 until membersDraft.size) {
                                                    membersDraft[i] = membersDraft[i].copy(
                                                        classification = ClientClassification.RECORRENTE,
                                                        origin = null
                                                    )
                                                }
                                            },
                                            testTag = "quick_mark_all_recurrent"
                                        )
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                itemsIndexed(membersDraft) { index, draft ->
                                    MemberClassificationCard(
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

                            Spacer(modifier = Modifier.height(24.dp))

                            // Botões de ação inferiores garantindo visibilidade total
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PaoSecondaryButton(
                                    text = "Voltar",
                                    onClick = { step = 1 },
                                    modifier = Modifier.weight(1f),
                                    testTag = "step2_back_button"
                                )

                                PaoPrimaryButton(
                                    text = "Avançar para Resumo",
                                    onClick = {
                                        // Validate origins for all new members
                                        var hasIncompleteOrigin = false
                                        for (m in membersDraft) {
                                            if (m.classification == ClientClassification.NOVO && m.origin.isNullOrBlank()) {
                                                hasIncompleteOrigin = true
                                                break
                                            }
                                        }

                                        if (hasIncompleteOrigin) {
                                            validationError = "Selecione a origem de todos os clientes novos."
                                        } else {
                                            validationError = null
                                            step = 3
                                        }
                                    },
                                    modifier = Modifier.weight(1.4f),
                                    testTag = "advance_to_summary_button"
                                )
                            }
                        }
                    }

                    3 -> {
                        // Step 3: Resumo antes da confirmação
                        val type = selectedType ?: AttendanceType.SOLO
                        val total = membersDraft.size
                        val newCount = membersDraft.count { it.classification == ClientClassification.NOVO }
                        val returningCount = membersDraft.count { it.classification == ClientClassification.RECORRENTE }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "Resumo do Atendimento",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                            Text(
                                text = "Revise as informações antes de confirmar o registro.",
                                fontSize = 12.sp,
                                color = PaoAnthraciteMuted,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Card rolável caso a tela do dispositivo seja compacta
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
                                                .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                SummaryRow("Tipo de Atendimento", type.displayName)
                                                SummaryRow(
                                                    label = "Atendimento Preferencial",
                                                    value = if (isPreferential) "Sim (Preferencial)" else "Não",
                                                    valueColor = if (isPreferential) StatusAmber else PaoAnthracite
                                                )
                                                SummaryRow("Total de Pessoas", "$total pessoa(s)")
                                                SummaryRow("Clientes Novos", "$newCount", StatusGreen)
                                                SummaryRow("Clientes Recorrentes", "$returningCount", PaoAnthracite)
                                                SummaryRow("Hoster Responsável", hosterName)
                                                SummaryRow("Turno Atual", shift.period.displayName)

                                                if (newCount > 0) {
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(vertical = 10.dp),
                                                        color = PaoBorderLight
                                                    )
                                                    Text(
                                                        text = "Origem dos novos clientes:",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = PaoAnthracite
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    membersDraft.filter { it.classification == ClientClassification.NOVO }
                                                        .forEach { m ->
                                                            Text(
                                                                text = "• Integrante ${m.memberNumber}: ${m.origin}",
                                                                fontSize = 12.sp,
                                                                color = PaoAnthraciteMuted
                                                            )
                                                        }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Botões com layout fixo e garantido acima da barra de navegação
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PaoSecondaryButton(
                                    text = "Voltar e Corrigir",
                                    onClick = { step = 2 },
                                    modifier = Modifier.weight(1f),
                                    testTag = "summary_back_button"
                                )

                                PaoPrimaryButton(
                                    text = "Confirmar Atendimento",
                                    onClick = {
                                        isSubmitting = true
                                        val domainMembers = membersDraft.map { draft ->
                                            AttendanceMember(
                                                id = UUID.randomUUID().toString(),
                                                attendanceId = "",
                                                memberNumber = draft.memberNumber,
                                                classification = draft.classification,
                                                origin = if (draft.classification == ClientClassification.NOVO) draft.origin else null
                                            )
                                        }
                                        onConfirm(type, total, newCount, returningCount, isPreferential, domainMembers)
                                    },
                                    isLoading = isSubmitting,
                                    modifier = Modifier.weight(1.4f),
                                    testTag = "confirm_attendance_button"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreferentialToggleCard(
    isPreferential: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle(!isPreferential) }
            .testTag("toggle_preferential_card"),
        color = if (isPreferential) StatusAmber.copy(alpha = 0.12f) else PaoCreamLight,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPreferential) StatusAmber else PaoBorderLight
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = if (isPreferential) StatusAmber else PaoBorderLight
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "★",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPreferential) Color.White else PaoAnthraciteMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Atendimento Preferencial",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPreferential) StatusAmber else PaoAnthracite
                    )
                    Text(
                        text = "Idoso, gestante, PCD, autista ou mobilidade reduzida",
                        fontSize = 11.sp,
                        color = PaoAnthraciteMuted
                    )
                }
            }

            androidx.compose.material3.Switch(
                checked = isPreferential,
                onCheckedChange = onToggle,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = StatusAmber,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = PaoBorderLight
                ),
                modifier = Modifier.testTag("preferential_switch")
            )
        }
    }
}

@Composable
fun QuickActionChip(
    text: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .testTag(testTag),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, PaoRed.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = PaoRed,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color = PaoAnthracite
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = PaoAnthraciteMuted)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun AttendanceTypeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(testTag),
        color = if (isSelected) PaoCream else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) PaoRed else PaoBorder
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape),
                color = if (isSelected) PaoRed else PaoCream
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else PaoRed,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaoAnthracite
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = PaoAnthraciteMuted
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberClassificationCard(
    draft: MemberDraft,
    onClassificationChange: (ClientClassification) -> Unit,
    onOriginChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PaoBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Integrante ${draft.memberNumber}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PaoAnthracite
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Classification pills (Novo vs Recorrente)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Recorrente
                val isRecorrente = draft.classification == ClientClassification.RECORRENTE
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClassificationChange(ClientClassification.RECORRENTE) }
                        .testTag("member_${draft.memberNumber}_recorrente"),
                    color = if (isRecorrente) PaoAnthracite else PaoCreamLight,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Recorrente",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRecorrente) Color.White else PaoAnthracite
                        )
                    }
                }

                // Novo
                val isNovo = draft.classification == ClientClassification.NOVO
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClassificationChange(ClientClassification.NOVO) }
                        .testTag("member_${draft.memberNumber}_novo"),
                    color = if (isNovo) PaoRed else PaoCreamLight,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Novo Cliente",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isNovo) Color.White else PaoAnthracite
                        )
                    }
                }
            }

            // Origins selector (Only if Novo)
            if (draft.classification == ClientClassification.NOVO) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Origem do cliente:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PaoAnthracite
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ClientOrigins.ALL.forEach { origin ->
                        val isSelected = draft.origin == origin
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOriginChange(origin) }
                                .testTag("origin_${origin}_member_${draft.memberNumber}"),
                            color = if (isSelected) PaoRed.copy(alpha = 0.15f) else PaoCreamLight,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) PaoRed else PaoBorderLight
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = origin,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) PaoRed else PaoAnthracite,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

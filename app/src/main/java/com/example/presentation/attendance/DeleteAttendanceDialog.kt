package com.example.presentation.attendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AttendanceWithMembers
import com.example.domain.util.DateTimeUtils
import com.example.ui.theme.PaoAnthracite
import com.example.ui.theme.PaoAnthraciteMuted
import com.example.ui.theme.PaoBorderLight
import com.example.ui.theme.PaoCreamLight
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed

@Composable
fun DeleteAttendanceDialog(
    item: AttendanceWithMembers,
    onDismiss: () -> Unit,
    onConfirmDelete: (reason: String?) -> Unit
) {
    val att = item.attendance
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusRed,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = "Excluir Atendimento",
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
                    text = "Tem certeza de que deseja excluir este atendimento?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PaoAnthracite
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = PaoCreamLight,
                    border = BorderStroke(1.dp, PaoBorderLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DeleteInfoRow(
                            label = "Tipo",
                            value = att.type.displayName + if (att.isPreferential) " (Preferencial)" else ""
                        )
                        DeleteInfoRow(
                            label = "Total de pessoas",
                            value = "${att.totalMembers}"
                        )
                        DeleteInfoRow(
                            label = "Novos clientes",
                            value = "${att.newMembers}",
                            valueColor = StatusGreen
                        )
                        DeleteInfoRow(
                            label = "Recorrentes",
                            value = "${att.returningMembers}"
                        )
                        DeleteInfoRow(
                            label = "Hoster responsável",
                            value = att.hosterName
                        )
                        DeleteInfoRow(
                            label = "Data e horário",
                            value = DateTimeUtils.formatDateTime(att.createdAt)
                        )
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo da exclusão (opcional)", fontSize = 12.sp) },
                    placeholder = { Text("Ex.: cliente desistiu antes da acomodação", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_deletion_reason")
                )

                Text(
                    text = "O registro será removido dos indicadores e relatórios, permanecendo preservado no banco para auditoria.",
                    fontSize = 11.sp,
                    color = PaoAnthraciteMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmDelete(reason.trim().takeIf { it.isNotBlank() }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_confirmar_exclusao")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Confirmar Exclusão", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_cancelar_exclusao")
            ) {
                Text("Cancelar", fontSize = 13.sp, color = PaoAnthracite)
            }
        }
    )
}

@Composable
private fun DeleteInfoRow(
    label: String,
    value: String,
    valueColor: Color = PaoAnthracite
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$label:", fontSize = 12.sp, color = PaoAnthraciteMuted)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

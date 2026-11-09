package com.example.presentation.shift

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Shift
import com.example.presentation.components.PaoPrimaryButton
import com.example.presentation.components.PaoSecondaryButton
import com.example.ui.theme.PaoAnthracite
import com.example.ui.theme.PaoAnthraciteMuted
import com.example.ui.theme.PaoBorder
import com.example.ui.theme.PaoBorderLight
import com.example.ui.theme.PaoCream
import com.example.ui.theme.PaoRed
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed

@Composable
fun CloseShiftDialog(
    shift: Shift,
    totalAttendances: Int,
    totalPeople: Int,
    newClients: Int,
    returningClients: Int,
    onDismiss: () -> Unit,
    onConfirmClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = StatusRed.copy(alpha = 0.12f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockClock,
                        contentDescription = null,
                        tint = StatusRed,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Encerrar Turno?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaoAnthracite
                )
                Text(
                    text = "Turno ${shift.period.displayName} • ${shift.hosterName}",
                    fontSize = 13.sp,
                    color = PaoAnthraciteMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Após o encerramento, não será mais permitido registrar novos atendimentos nem editar registros deste turno.",
                    fontSize = 12.sp,
                    color = PaoAnthraciteMuted
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PaoBorder, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = PaoBorderLight.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Resultados do Turno:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        HorizontalDivider(color = PaoBorderLight)

                        MetricRow("Total de Atendimentos", totalAttendances.toString())
                        MetricRow("Total de Pessoas Atendidas", totalPeople.toString())
                        MetricRow("Clientes Novos", newClients.toString(), StatusGreen)
                        MetricRow("Clientes Recorrentes", returningClients.toString(), PaoAnthracite)
                    }
                }
            }
        },
        confirmButton = {
            PaoPrimaryButton(
                text = "Confirmar Encerramento",
                onClick = onConfirmClose,
                testTag = "confirm_close_shift_button"
            )
        },
        dismissButton = {
            PaoSecondaryButton(
                text = "Voltar",
                onClick = onDismiss,
                testTag = "cancel_close_shift_button"
            )
        }
    )
}

@Composable
private fun MetricRow(label: String, value: String, valueColor: Color = PaoAnthracite) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = PaoAnthraciteMuted)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

package com.example.presentation.queue

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
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.presentation.components.NumericStepper
import com.example.presentation.components.PaoPrimaryButton
import com.example.presentation.components.PaoSecondaryButton
import com.example.ui.theme.PaoAnthracite
import com.example.ui.theme.PaoAnthraciteMuted
import com.example.ui.theme.PaoCream
import com.example.ui.theme.PaoRed
import com.example.ui.theme.StatusRed

@Composable
fun UpdateQueueDialog(
    initialDuos: Int,
    initialGroups: Int,
    onDismiss: () -> Unit,
    onSave: (duos: Int, groups: Int) -> Unit
) {
    var duos by remember { mutableIntStateOf(initialDuos) }
    var groups by remember { mutableIntStateOf(initialGroups) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = PaoCream
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = PaoRed,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Atualizar Fila de Espera",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaoAnthracite
                )
                Text(
                    text = "Controle manual de duplas e grupos aguardando mesa",
                    fontSize = 12.sp,
                    color = PaoAnthraciteMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                NumericStepper(
                    value = duos,
                    onValueChange = { duos = it },
                    minValue = 0,
                    maxValue = 50,
                    label = "Duplas aguardando",
                    testTagPrefix = "queue_duos"
                )

                NumericStepper(
                    value = groups,
                    onValueChange = { groups = it },
                    minValue = 0,
                    maxValue = 50,
                    label = "Grupos aguardando",
                    testTagPrefix = "queue_groups"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            duos = 0
                            groups = 0
                        },
                        modifier = Modifier.testTag("reset_queue_button")
                    ) {
                        Text(
                            text = "Zerar Fila de Espera",
                            color = StatusRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            PaoPrimaryButton(
                text = "Salvar Atualização",
                onClick = { onSave(duos, groups) },
                testTag = "save_queue_button"
            )
        },
        dismissButton = {
            PaoSecondaryButton(
                text = "Cancelar",
                onClick = onDismiss,
                testTag = "cancel_queue_button"
            )
        }
    )
}

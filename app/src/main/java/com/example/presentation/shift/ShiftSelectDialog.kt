package com.example.presentation.shift

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.domain.model.ShiftPeriod
import com.example.presentation.components.PaoPrimaryButton
import com.example.presentation.components.PaoSecondaryButton
import com.example.ui.theme.PaoAnthracite
import com.example.ui.theme.PaoAnthraciteMuted
import com.example.ui.theme.PaoBorder
import com.example.ui.theme.PaoBorderLight
import com.example.ui.theme.PaoCream
import com.example.ui.theme.PaoCreamLight
import com.example.ui.theme.PaoRed

@Composable
fun ShiftSelectDialog(
    hosterName: String,
    onSelectPeriod: (ShiftPeriod) -> Unit,
    onCancel: () -> Unit
) {
    var selectedPeriod by remember { mutableStateOf(ShiftPeriod.MANHA) }

    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = PaoCream
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = PaoRed,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Abertura de Turno",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaoAnthracite
                )
                Text(
                    text = "Hoster: $hosterName",
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
                    text = "Selecione o período do turno que deseja iniciar:",
                    fontSize = 13.sp,
                    color = PaoAnthraciteMuted
                )

                // Manhã Option
                ShiftPeriodOptionCard(
                    period = ShiftPeriod.MANHA,
                    title = "Manhã",
                    subtitle = "Almoço e primeiras horas",
                    icon = Icons.Default.Brightness5,
                    isSelected = selectedPeriod == ShiftPeriod.MANHA,
                    onClick = { selectedPeriod = ShiftPeriod.MANHA },
                    testTag = "period_manha_option"
                )

                // Noite Option
                ShiftPeriodOptionCard(
                    period = ShiftPeriod.NOITE,
                    title = "Noite",
                    subtitle = "Jantar e movimento noturno",
                    icon = Icons.Default.NightsStay,
                    isSelected = selectedPeriod == ShiftPeriod.NOITE,
                    onClick = { selectedPeriod = ShiftPeriod.NOITE },
                    testTag = "period_noite_option"
                )
            }
        },
        confirmButton = {
            PaoPrimaryButton(
                text = "Iniciar Turno",
                onClick = { onSelectPeriod(selectedPeriod) },
                testTag = "confirm_shift_start_button"
            )
        },
        dismissButton = {
            PaoSecondaryButton(
                text = "Trocar Hoster",
                onClick = onCancel,
                testTag = "cancel_shift_button"
            )
        }
    )
}

@Composable
private fun ShiftPeriodOptionCard(
    period: ShiftPeriod,
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
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag(testTag),
        color = if (isSelected) PaoCream else PaoCreamLight,
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) PaoRed else PaoBorderLight
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) PaoRed else PaoAnthraciteMuted,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = PaoAnthracite
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = PaoAnthraciteMuted
                )
            }
        }
    }
}

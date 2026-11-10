package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.domain.model.SyncState
import com.example.ui.theme.PaoAnthracite
import com.example.ui.theme.PaoAnthraciteMuted
import com.example.ui.theme.PaoBorder
import com.example.ui.theme.PaoBorderLight
import com.example.ui.theme.PaoCream
import com.example.ui.theme.PaoCreamLight
import com.example.ui.theme.PaoRed
import com.example.ui.theme.PaoRedDark
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed

val LocalSyncLogsOpener = androidx.compose.runtime.compositionLocalOf<() -> Unit> { {} }

@Composable
fun SyncStatusPill(
    syncState: SyncState,
    pendingCount: Int,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val opener = LocalSyncLogsOpener.current
    val effectiveClick: () -> Unit = {
        opener.invoke()
        onClick?.invoke()
    }

    val (dotColor, icon, text) = when (syncState) {
        SyncState.SINCRONIZADO -> Triple(StatusGreen, Icons.Default.CloudDone, "Sincronizado")
        SyncState.OFFLINE -> Triple(StatusAmber, Icons.Default.CloudOff, "Modo Offline")
        SyncState.PENDENTE -> Triple(StatusAmber, Icons.Default.CloudQueue, "Pendente ($pendingCount)")
        SyncState.SINCRONIZANDO -> Triple(StatusBlue, Icons.Default.CloudSync, "Sincronizando...")
        SyncState.ERRO -> Triple(StatusRed, Icons.Default.Warning, "Erro de Sinc.")
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { effectiveClick() }
            .testTag("sync_status_pill"),
        color = dotColor.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, dotColor.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                imageVector = icon,
                contentDescription = text,
                tint = dotColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = PaoAnthracite,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun PaoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    testTag: String = "primary_button"
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PaoRed,
            contentColor = Color.White,
            disabledContainerColor = PaoBorder
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }
    }
}

@Composable
fun PaoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "secondary_button"
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(48.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, PaoAnthracite),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PaoAnthracite
        )
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun NumericStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int = 0,
    maxValue: Int = 99,
    label: String,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "stepper"
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, PaoBorderLight, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = PaoCreamLight),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PaoAnthracite
                )
                Text(
                    text = if (value == 0) "Nenhum no momento" else "$value selecionado(s)",
                    fontSize = 12.sp,
                    color = PaoAnthraciteMuted
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(enabled = value > minValue) { onValueChange(value - 1) }
                        .testTag("${testTagPrefix}_decrement"),
                    color = if (value > minValue) Color.White else PaoBorderLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PaoBorder),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Diminuir",
                            tint = if (value > minValue) PaoAnthracite else PaoAnthraciteMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .width(44.dp)
                        .height(42.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PaoBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = value.toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(enabled = value < maxValue) { onValueChange(value + 1) }
                        .testTag("${testTagPrefix}_increment"),
                    color = if (value < maxValue) PaoRed else PaoBorderLight,
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Aumentar",
                            tint = if (value < maxValue) Color.White else PaoAnthraciteMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

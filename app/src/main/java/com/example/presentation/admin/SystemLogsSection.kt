package com.example.presentation.admin

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.LogResult
import com.example.domain.model.SystemLog
import com.example.domain.util.DateTimeUtils
import com.example.ui.theme.PaoAnthracite
import com.example.ui.theme.PaoAnthraciteMuted
import com.example.ui.theme.PaoBorder
import com.example.ui.theme.PaoBorderLight
import com.example.ui.theme.PaoCreamLight
import com.example.ui.theme.PaoRed
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import java.util.Calendar

data class SystemLogsFilterState(
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val selectedUser: String? = null,
    val selectedAction: String? = null,
    val selectedDevice: String? = null,
    val selectedResult: LogResult? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemLogsFilterCard(
    filter: SystemLogsFilterState,
    availableUsers: List<String>,
    availableActions: List<String>,
    availableDevices: List<String>,
    onStartDateChange: (Long?) -> Unit,
    onEndDateChange: (Long?) -> Unit,
    onUserChange: (String?) -> Unit,
    onActionChange: (String?) -> Unit,
    onDeviceChange: (String?) -> Unit,
    onResultChange: (LogResult?) -> Unit,
    onClearFilters: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = PaoRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Filtros de Auditoria e Logs",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaoAnthracite
                    )
                }
                TextButton(onClick = onClearFilters) {
                    Text("Limpar", fontSize = 11.sp, color = PaoRed, fontWeight = FontWeight.Bold)
                }
            }

            // Date range row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = filter.startMillis ?: System.currentTimeMillis()
                            }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val sel = Calendar.getInstance().apply {
                                        set(y, m, d, 0, 0, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    onStartDateChange(sel.timeInMillis)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                    color = PaoCreamLight,
                    border = BorderStroke(1.dp, PaoBorder),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PaoRed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Data inicial", fontSize = 10.sp, color = PaoAnthraciteMuted)
                            Text(
                                text = filter.startMillis?.let { DateTimeUtils.formatDate(it) } ?: "Todas",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = filter.endMillis ?: System.currentTimeMillis()
                            }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val sel = Calendar.getInstance().apply {
                                        set(y, m, d, 23, 59, 59)
                                        set(Calendar.MILLISECOND, 999)
                                    }
                                    onEndDateChange(sel.timeInMillis)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                    color = PaoCreamLight,
                    border = BorderStroke(1.dp, PaoBorder),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PaoRed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Data final", fontSize = 10.sp, color = PaoAnthraciteMuted)
                            Text(
                                text = filter.endMillis?.let { DateTimeUtils.formatDate(it) } ?: "Todas",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                        }
                    }
                }
            }

            // Resultado chips
            Text("Resultado", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PaoAnthraciteMuted)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LogChip("Todos", filter.selectedResult == null) { onResultChange(null) }
                LogChip("Sucesso", filter.selectedResult == LogResult.SUCCESS) { onResultChange(LogResult.SUCCESS) }
                LogChip("Falha", filter.selectedResult == LogResult.FAILURE) { onResultChange(LogResult.FAILURE) }
            }

            // Usuário chips
            if (availableUsers.isNotEmpty()) {
                Text("Usuário", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PaoAnthraciteMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LogChip("Todos", filter.selectedUser == null) { onUserChange(null) }
                    availableUsers.forEach { user ->
                        LogChip(user, filter.selectedUser == user) { onUserChange(user) }
                    }
                }
            }

            // Dispositivo chips
            if (availableDevices.isNotEmpty()) {
                Text("Dispositivo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PaoAnthraciteMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LogChip("Todos", filter.selectedDevice == null) { onDeviceChange(null) }
                    availableDevices.forEach { dev ->
                        LogChip(dev, filter.selectedDevice == dev) { onDeviceChange(dev) }
                    }
                }
            }

            // Tipo de ação chips
            if (availableActions.isNotEmpty()) {
                Text("Tipo de ação", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PaoAnthraciteMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LogChip("Todas", filter.selectedAction == null) { onActionChange(null) }
                    availableActions.forEach { action ->
                        LogChip(formatActionLabel(action), filter.selectedAction == action) {
                            onActionChange(action)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SystemLogItemCard(log: SystemLog) {
    val isSuccess = log.result == LogResult.SUCCESS
    val badgeColor = if (isSuccess) StatusGreen else StatusRed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PaoBorderLight, RoundedCornerShape(12.dp))
            .testTag("system_log_item_${log.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${formatActionLabel(log.action)} • ${log.result.label}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }

                Text(
                    text = DateTimeUtils.formatDateTime(log.createdAt),
                    fontSize = 11.sp,
                    color = PaoAnthraciteMuted
                )
            }

            Text(
                text = log.description,
                fontSize = 12.sp,
                color = PaoAnthracite,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = PaoAnthraciteMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = log.userName ?: "Sistema",
                        fontSize = 11.sp,
                        color = PaoAnthraciteMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = PaoAnthraciteMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = log.deviceName,
                        fontSize = 11.sp,
                        color = PaoAnthraciteMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun LogChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (isSelected) PaoRed else PaoCreamLight,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else PaoAnthracite,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

fun formatActionLabel(action: String): String {
    return action.lowercase()
        .split("_")
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
}

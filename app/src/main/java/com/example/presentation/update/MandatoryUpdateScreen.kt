package com.example.presentation.update

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.update.UpdateStatus
import com.example.data.update.UpdateUiState
import com.example.presentation.components.PaoPrimaryButton
import com.example.presentation.components.motionDuration
import com.example.presentation.components.rememberReducedMotion
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

@Composable
fun MandatoryUpdateScreen(
    state: UpdateUiState,
    isOnline: Boolean,
    onStartUpdate: () -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
    onRecheckVersion: () -> Unit
) {
    // Unbypassable: do not allow Android back button to dismiss mandatory update
    BackHandler(enabled = true) { }

    val policy = state.policy
    val reducedMotion = rememberReducedMotion()
    val animatedProgress by animateFloatAsState(
        targetValue = state.downloadProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = motionDuration(reducedMotion, 180)),
        label = "apk_download_progress"
    )
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = PaoBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                shape = RoundedCornerShape(18.dp),
                color = PaoRed
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_pao_com_carne),
                    contentDescription = "Pão com Carne",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = StatusRed.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = StatusRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "VERSÃO INCOMPATÍVEL BLOQUEADA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Atualização Obrigatória",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = PaoAnthracite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Esta versão do aplicativo Pão com Carne foi descontinuada para evitar inconsistências nos registros. Atualize agora para continuar operando.",
                fontSize = 13.sp,
                color = PaoAnthraciteMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Version comparison card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Versão instalada", fontSize = 12.sp, color = PaoAnthraciteMuted)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusRed.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "v${state.installedVersionName} (cód. ${state.installedVersionCode})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusRed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Versão necessária", fontSize = 12.sp, color = PaoAnthraciteMuted)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusGreen.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "v${policy?.versionName ?: "—"} (mín. cód. ${policy?.minSupportedCode ?: "—"})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (!policy?.releaseReason.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = PaoCreamLight,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, PaoBorderLight)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Motivo da atualização",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PaoAnthracite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = policy!!.releaseReason,
                                    fontSize = 12.sp,
                                    color = PaoAnthraciteMuted
                                )
                            }
                        }
                    }

                    if (!policy?.changelog.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, PaoBorderLight)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Novidades da versão",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PaoAnthracite
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = policy!!.changelog,
                                    fontSize = 12.sp,
                                    color = PaoAnthracite
                                )
                            }
                        }
                    }

                    Text(
                        text = "Seus registros locais pendentes e fila de sincronização serão preservados durante a atualização.",
                        fontSize = 11.sp,
                        color = StatusGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Offline alert
            if (!isOnline) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = StatusAmber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = StatusAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sem internet no momento. Conecte-se para baixar a atualização obrigatória e validar a versão.",
                            fontSize = 12.sp,
                            color = PaoAnthracite,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Progress block
            if (state.status == UpdateStatus.DOWNLOADING || state.status == UpdateStatus.INSTALLING) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PaoBorder, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.statusMessage ?: "Baixando atualização...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoRed
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = PaoRed,
                            trackColor = PaoBorderLight
                        )
                        if (state.totalBytes > 0L) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val mbDown = String.format("%.1f", state.downloadedBytes / (1024f * 1024f))
                            val mbTotal = String.format("%.1f", state.totalBytes / (1024f * 1024f))
                            Text(
                                text = "$mbDown MB de $mbTotal MB",
                                fontSize = 11.sp,
                                color = PaoAnthraciteMuted
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Permission needed
            if (state.status == UpdateStatus.NEEDS_INSTALL_PERMISSION) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StatusAmber, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = PaoCreamLight),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = StatusAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Permissão de Instalação Necessária",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Autorize \"Instalar apps desconhecidos\" para o Pão com Carne para concluir a instalação do APK.",
                            fontSize = 12.sp,
                            color = PaoAnthraciteMuted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onOpenInstallPermissionSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = PaoAnthracite),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Autorizar nas Configurações", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Error banner
            if (!state.errorMessage.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = StatusRed.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.errorMessage,
                            fontSize = 12.sp,
                            color = StatusRed,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            PaoPrimaryButton(
                text = when (state.status) {
                    UpdateStatus.DOWNLOADING -> "Baixando APK..."
                    UpdateStatus.INSTALLING -> "Instalando atualização..."
                    else -> "Atualizar Aplicativo Agora"
                },
                onClick = onStartUpdate,
                enabled = isOnline &&
                    policy != null &&
                    policy.apkPath.isNotBlank() &&
                    state.status != UpdateStatus.DOWNLOADING &&
                    state.status != UpdateStatus.INSTALLING,
                isLoading = state.status == UpdateStatus.DOWNLOADING || state.status == UpdateStatus.INSTALLING,
                testTag = "btn_mandatory_update"
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onRecheckVersion,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PaoBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_recheck_version")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = PaoAnthracite,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verificar Versão Novamente", fontSize = 13.sp, color = PaoAnthracite)
            }
        }
    }
}

@Composable
fun OptionalUpdateBanner(
    state: UpdateUiState,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val policy = state.policy ?: return
    if (state.isBlocked || state.status != UpdateStatus.OPTIONAL_AVAILABLE || state.isOptionalBannerDismissed) {
        return
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = PaoCream,
        border = BorderStroke(1.dp, PaoRed.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PaoRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = PaoRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Nova versão disponível (v${policy.versionName})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaoAnthracite
                    )
                    Text(
                        text = policy.changelog.ifBlank { "Melhorias de estabilidade e desempenho." },
                        fontSize = 11.sp,
                        color = PaoAnthraciteMuted,
                        maxLines = 1
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) {
                    Text("Depois", fontSize = 11.sp, color = PaoAnthraciteMuted)
                }
                Button(
                    onClick = onUpdateClick,
                    colors = ButtonDefaults.buttonColors(containerColor = PaoRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Atualizar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

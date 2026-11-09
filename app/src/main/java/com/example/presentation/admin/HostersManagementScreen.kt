package com.example.presentation.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.AuthRepository
import com.example.data.sync.SyncManager
import com.example.domain.model.Hoster
import com.example.presentation.components.PaoPrimaryButton
import com.example.presentation.components.SyncStatusPill
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
import kotlinx.coroutines.launch

@Composable
fun HostersManagementScreen(
    authRepository: AuthRepository,
    syncManager: SyncManager,
    onLogout: () -> Unit
) {
    val hosters by authRepository.getActiveHostersFlow().collectAsState(initial = emptyList())
    val syncState by syncManager.syncState.collectAsState()
    val pendingCount by syncManager.pendingCount.collectAsState()
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var hosterToEdit by remember { mutableStateOf<Hoster?>(null) }
    var hosterToDelete by remember { mutableStateOf<Hoster?>(null) }
    var hosterToResetPin by remember { mutableStateOf<Hoster?>(null) }

    var actionMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    // Android back button: prompt logout confirmation
    androidx.activity.compose.BackHandler {
        showLogoutConfirmDialog = true
    }

    Scaffold(
        containerColor = PaoBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = PaoRed,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("fab_add_hoster")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Hoster")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = PaoRed
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Administrador",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        Text(
                            text = "Gestão de Hosters",
                            fontSize = 12.sp,
                            color = PaoAnthraciteMuted
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SyncStatusPill(
                        syncState = syncState,
                        pendingCount = pendingCount,
                        onClick = { syncManager.triggerSync() }
                    )

                    // Botão destacado "Sair" com texto e ícone
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showLogoutConfirmDialog = true }
                            .testTag("admin_logout_button"),
                        color = PaoRed.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PaoRed.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Sair",
                                tint = PaoRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sair",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoRed
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = PaoBorderLight, modifier = Modifier.padding(bottom = 12.dp))

            if (actionMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = StatusGreen.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusGreen.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = actionMessage ?: "",
                        fontSize = 12.sp,
                        color = PaoAnthracite,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (errorMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = StatusRed.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusRed.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = errorMessage ?: "",
                        fontSize = 12.sp,
                        color = StatusRed,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PaoBorderLight, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = PaoRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Todas as inclusões, edições e exclusões são salvas localmente e sincronizadas no Supabase em tempo real.",
                        fontSize = 12.sp,
                        color = PaoAnthraciteMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Hosters Cadastrados (${hosters.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PaoAnthracite
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(hosters) { hoster ->
                    HosterItemCard(
                        hoster = hoster,
                        onEdit = { hosterToEdit = hoster },
                        onResetPin = { hosterToResetPin = hoster },
                        onDelete = { hosterToDelete = hoster }
                    )
                }
            }
        }
    }

    // Dialog Create Hoster
    if (showCreateDialog) {
        CreateHosterDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, pin ->
                showCreateDialog = false
                scope.launch {
                    val res = authRepository.createHoster(name, pin)
                    res.fold(
                        onSuccess = {
                            actionMessage = "Hoster \"${it.name}\" cadastrado com sucesso!"
                            errorMessage = null
                        },
                        onFailure = {
                            errorMessage = it.message ?: "Erro ao cadastrar hoster."
                        }
                    )
                }
            }
        )
    }

    // Dialog Edit Hoster Name
    if (hosterToEdit != null) {
        EditHosterDialog(
            hoster = hosterToEdit!!,
            onDismiss = { hosterToEdit = null },
            onConfirm = { newName ->
                val target = hosterToEdit!!
                hosterToEdit = null
                scope.launch {
                    val res = authRepository.updateHosterName(target.id, newName)
                    res.fold(
                        onSuccess = {
                            actionMessage = "Nome atualizado para \"$newName\"!"
                            errorMessage = null
                        },
                        onFailure = {
                            errorMessage = it.message ?: "Erro ao editar nome."
                        }
                    )
                }
            }
        )
    }

    // Dialog Reset Pin
    if (hosterToResetPin != null) {
        ResetPinDialog(
            hoster = hosterToResetPin!!,
            onDismiss = { hosterToResetPin = null },
            onConfirm = { newPin ->
                val target = hosterToResetPin!!
                hosterToResetPin = null
                scope.launch {
                    val res = authRepository.updateHosterPin(target.id, newPin)
                    res.fold(
                        onSuccess = {
                            actionMessage = "Senha de \"${target.name}\" alterada com sucesso!"
                            errorMessage = null
                        },
                        onFailure = {
                            errorMessage = it.message ?: "Erro ao alterar senha."
                        }
                    )
                }
            }
        )
    }

    // Dialog Delete Confirm
    if (hosterToDelete != null) {
        val target = hosterToDelete!!
        AlertDialog(
            onDismissRequest = { hosterToDelete = null },
            title = { Text("Excluir Hoster", fontWeight = FontWeight.Bold) },
            text = { Text("Tem certeza que deseja remover ${target.name}? Esta operação sincronizará a remoção no Supabase.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        hosterToDelete = null
                        scope.launch {
                            val res = authRepository.deleteHoster(target.id)
                            res.fold(
                                onSuccess = {
                                    actionMessage = "Hoster removido com sucesso."
                                    errorMessage = null
                                },
                                onFailure = {
                                    errorMessage = it.message ?: "Erro ao remover hoster."
                                }
                            )
                        }
                    }
                ) {
                    Text("Excluir", color = StatusRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { hosterToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog Confirm Logout
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("Sair do Painel Admin", fontWeight = FontWeight.Bold) },
            text = { Text("Deseja realmente sair da sessão do administrador e voltar à tela inicial de login?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmDialog = false
                        onLogout()
                    }
                ) {
                    Text("Sair", color = StatusRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun HosterItemCard(
    hoster: Hoster,
    onEdit: () -> Unit,
    onResetPin: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PaoBorderLight, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                    color = if (hoster.isAdmin) PaoRed.copy(alpha = 0.15f) else PaoCream
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (hoster.isAdmin) Icons.Default.Shield else Icons.Default.Person,
                            contentDescription = null,
                            tint = PaoRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = hoster.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        if (hoster.isAdmin) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = PaoRed
                            ) {
                                Text(
                                    text = "ADMIN",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (hoster.isActivated) "Ativo • Senha cadastrada" else "Pendente primeiro acesso",
                        fontSize = 11.sp,
                        color = if (hoster.isActivated) StatusGreen else StatusAmber
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onResetPin) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Alterar senha",
                        tint = PaoAnthraciteMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar nome",
                        tint = PaoAnthraciteMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (!hoster.isAdmin) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Excluir",
                            tint = StatusRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreateHosterDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, pin: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Hoster", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.imePadding()) {
                Text(
                    text = "Adicione um membro para a equipe de recepção:",
                    fontSize = 12.sp,
                    color = PaoAnthraciteMuted
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nome completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("Senha inicial (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = error ?: "", color = StatusRed, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        error = "O nome é obrigatório."
                    } else {
                        onConfirm(name, pin.ifBlank { null })
                    }
                }
            ) {
                Text("Cadastrar", fontWeight = FontWeight.Bold, color = PaoRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun EditHosterDialog(
    hoster: Hoster,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var name by remember { mutableStateOf(hoster.name) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Nome do Hoster", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.imePadding()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nome completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = error ?: "", color = StatusRed, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        error = "O nome não pode ficar vazio."
                    } else {
                        onConfirm(name)
                    }
                }
            ) {
                Text("Salvar", fontWeight = FontWeight.Bold, color = PaoRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun ResetPinDialog(
    hoster: Hoster,
    onDismiss: () -> Unit,
    onConfirm: (newPin: String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Definir Senha de ${hoster.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.imePadding()) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it; error = null },
                    label = { Text("Nova senha (mínimo 4 caracteres)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = error ?: "", color = StatusRed, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pin.length < 4) {
                        error = "A senha deve ter pelo menos 4 caracteres."
                    } else {
                        onConfirm(pin)
                    }
                }
            ) {
                Text("Salvar Senha", fontWeight = FontWeight.Bold, color = PaoRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

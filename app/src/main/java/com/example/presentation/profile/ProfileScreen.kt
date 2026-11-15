package com.example.presentation.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.presentation.components.SyncStatusPill
import com.example.presentation.reception.ReceptionViewModel
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
fun ProfileScreen(
    viewModel: ReceptionViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val hoster by viewModel.currentHoster.collectAsState()
    val shift by viewModel.currentShift.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    // Name form state
    var nameInput by remember(hoster?.id, hoster?.name) { mutableStateOf(hoster?.name.orEmpty()) }
    var isSavingName by remember { mutableStateOf(false) }
    var nameSuccessMessage by remember { mutableStateOf<String?>(null) }
    var nameErrorMessage by remember { mutableStateOf<String?>(null) }

    // Password form state
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showCurrentPin by remember { mutableStateOf(false) }
    var showNewPin by remember { mutableStateOf(false) }
    var showConfirmPin by remember { mutableStateOf(false) }
    var isSavingPassword by remember { mutableStateOf(false) }
    var passwordSuccessMessage by remember { mutableStateOf<String?>(null) }
    var passwordErrorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler {
        onBack()
    }

    Scaffold(
        containerColor = PaoBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, PaoBorderLight)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("btn_profile_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = PaoAnthracite
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Meu Perfil",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        Text(
                            text = "Editar nome, senha ou sair",
                            fontSize = 12.sp,
                            color = PaoAnthraciteMuted
                        )
                    }
                }

                SyncStatusPill(
                    syncState = syncState,
                    pendingCount = pendingCount,
                    onClick = { viewModel.triggerSync() }
                )
            }

            HorizontalDivider(color = PaoBorderLight, modifier = Modifier.padding(bottom = 16.dp))

            // Hoster Identity Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = PaoRed
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_pao_com_carne),
                            contentDescription = "Pão com Carne",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(5.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hoster?.name ?: "Hoster",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaoAnthracite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Hoster da Recepção",
                            fontSize = 12.sp,
                            color = PaoAnthraciteMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (shift != null) StatusGreen.copy(alpha = 0.12f) else StatusAmber.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (shift != null) {
                                    "Turno ${shift?.period?.displayName} em andamento"
                                } else {
                                    "Nenhum turno em andamento"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (shift != null) StatusGreen else StatusAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Change Name
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape),
                            color = PaoCream
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = PaoRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Alterar Nome",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                            Text(
                                text = "Como seu nome aparece na equipe e relatórios",
                                fontSize = 11.sp,
                                color = PaoAnthraciteMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            nameErrorMessage = null
                            nameSuccessMessage = null
                        },
                        label = { Text("Nome do Hoster") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = PaoAnthraciteMuted
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PaoRed,
                            unfocusedBorderColor = PaoBorder,
                            focusedLabelColor = PaoRed
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_profile_name")
                    )

                    ProfileFeedbackBanner(
                        successMessage = nameSuccessMessage,
                        errorMessage = nameErrorMessage
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val trimmed = nameInput.trim()
                            val currentName = hoster?.name.orEmpty().trim()
                            when {
                                trimmed.isBlank() -> {
                                    nameErrorMessage = "Informe um nome válido."
                                    nameSuccessMessage = null
                                }
                                trimmed == currentName -> {
                                    nameErrorMessage = "O novo nome precisa ser diferente do atual."
                                    nameSuccessMessage = null
                                }
                                else -> {
                                    isSavingName = true
                                    nameErrorMessage = null
                                    nameSuccessMessage = null
                                    scope.launch {
                                        val res = viewModel.updateOwnName(trimmed)
                                        isSavingName = false
                                        res.fold(
                                            onSuccess = {
                                                nameSuccessMessage = "Nome atualizado para \"$trimmed\"!"
                                            },
                                            onFailure = { err ->
                                                nameErrorMessage = err.message ?: "Erro ao atualizar nome."
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        enabled = !isSavingName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_save_profile_name"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PaoRed,
                            contentColor = Color.White
                        )
                    ) {
                        if (isSavingName) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Salvar Nome", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Change Password
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PaoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape),
                            color = PaoCream
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = PaoRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Alterar Senha",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaoAnthracite
                            )
                            Text(
                                text = "Confirme sua senha atual para definir uma nova",
                                fontSize = 11.sp,
                                color = PaoAnthraciteMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    ProfilePasswordField(
                        value = currentPin,
                        onValueChange = {
                            currentPin = it
                            passwordErrorMessage = null
                            passwordSuccessMessage = null
                        },
                        label = "Senha atual",
                        visible = showCurrentPin,
                        onToggleVisibility = { showCurrentPin = !showCurrentPin },
                        imeAction = ImeAction.Next,
                        testTag = "input_current_password"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ProfilePasswordField(
                        value = newPin,
                        onValueChange = {
                            newPin = it
                            passwordErrorMessage = null
                            passwordSuccessMessage = null
                        },
                        label = "Nova senha (mín. 4 caracteres)",
                        visible = showNewPin,
                        onToggleVisibility = { showNewPin = !showNewPin },
                        imeAction = ImeAction.Next,
                        testTag = "input_new_password"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ProfilePasswordField(
                        value = confirmPin,
                        onValueChange = {
                            confirmPin = it
                            passwordErrorMessage = null
                            passwordSuccessMessage = null
                        },
                        label = "Confirmar nova senha",
                        visible = showConfirmPin,
                        onToggleVisibility = { showConfirmPin = !showConfirmPin },
                        imeAction = ImeAction.Done,
                        testTag = "input_confirm_password"
                    )

                    ProfileFeedbackBanner(
                        successMessage = passwordSuccessMessage,
                        errorMessage = passwordErrorMessage
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            when {
                                currentPin.isBlank() -> {
                                    passwordErrorMessage = "Informe a sua senha atual."
                                    passwordSuccessMessage = null
                                }
                                newPin.length < 4 -> {
                                    passwordErrorMessage = "A nova senha deve ter no mínimo 4 caracteres."
                                    passwordSuccessMessage = null
                                }
                                newPin != confirmPin -> {
                                    passwordErrorMessage = "As novas senhas não coincidem."
                                    passwordSuccessMessage = null
                                }
                                else -> {
                                    isSavingPassword = true
                                    passwordErrorMessage = null
                                    passwordSuccessMessage = null
                                    scope.launch {
                                        val res = viewModel.changeOwnPassword(currentPin, newPin)
                                        isSavingPassword = false
                                        res.fold(
                                            onSuccess = {
                                                currentPin = ""
                                                newPin = ""
                                                confirmPin = ""
                                                passwordSuccessMessage = "Senha alterada com sucesso!"
                                            },
                                            onFailure = { err ->
                                                passwordErrorMessage = err.message ?: "Erro ao alterar senha."
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        enabled = !isSavingPassword,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_save_profile_password"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PaoAnthracite,
                            contentColor = Color.White
                        )
                    ) {
                        if (isSavingPassword) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Atualizar Senha", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Switch Hoster / Logout
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PaoRed.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = PaoCreamLight),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sessão neste Dispositivo",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaoAnthracite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Troque de hoster ou encerre o acesso atual para retornar à tela inicial de login.",
                        fontSize = 12.sp,
                        color = PaoAnthraciteMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_logout"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, PaoRed),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = PaoRed
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Trocar de Hoster / Sair",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    imeAction: ImeAction,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Ocultar senha" else "Mostrar senha",
                    tint = PaoAnthraciteMuted
                )
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PaoRed,
            unfocusedBorderColor = PaoBorder,
            focusedLabelColor = PaoRed
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

@Composable
private fun ProfileFeedbackBanner(
    successMessage: String?,
    errorMessage: String?
) {
    val message = successMessage ?: errorMessage ?: return
    val isSuccess = successMessage != null
    val accent = if (isSuccess) StatusGreen else StatusRed

    Spacer(modifier = Modifier.height(10.dp))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        color = accent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSuccess) PaoAnthracite else StatusRed
            )
        }
    }
}

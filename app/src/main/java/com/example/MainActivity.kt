package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.remote.SupabaseConfig
import com.example.data.update.UpdateUiState
import com.example.presentation.admin.AdminScreen
import com.example.presentation.admin.AdminViewModel
import com.example.presentation.components.motionDuration
import com.example.presentation.components.rememberReducedMotion
import com.example.presentation.inspection.InspectionScreen
import com.example.presentation.login.LoginScreen
import com.example.presentation.login.LoginViewModel
import com.example.presentation.profile.ProfileScreen
import com.example.presentation.reception.ReceptionScreen
import com.example.presentation.reception.ReceptionViewModel
import com.example.presentation.shift.ShiftScreen
import com.example.presentation.update.MandatoryUpdateScreen
import com.example.presentation.update.OptionalUpdateBanner
import com.example.ui.theme.PaoAnthraciteMuted
import com.example.ui.theme.PaoBackground
import com.example.ui.theme.PaoBorderLight
import com.example.ui.theme.PaoComCarneTheme
import com.example.ui.theme.PaoCream
import com.example.ui.theme.PaoRed
import kotlinx.coroutines.launch

enum class AppTab(val label: String, val icon: ImageVector, val tag: String) {
    RECEPCAO("Recepção", Icons.Default.Storefront, "tab_recepcao"),
    INSPECAO("Inspeção", Icons.Default.Visibility, "tab_inspecao"),
    ADMINISTRACAO("Administração", Icons.Default.Assessment, "tab_admin"),
    TURNO("Turno", Icons.Default.Schedule, "tab_turno")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        val app = application as PaoComCarneApp

        setContent {
            PaoComCarneTheme {
                val authRepo = app.authRepository
                val receptionRepo = app.receptionRepository
                val exportRepo = app.exportRepository
                val syncMgr = app.syncManager
                val systemLogRepo = app.systemLogRepository
                val updateMgr = app.appUpdateManager

                val loginViewModel = remember { LoginViewModel(authRepo, syncMgr) }
                val receptionViewModel = remember { ReceptionViewModel(authRepo, receptionRepo, syncMgr) }
                val adminViewModel = remember { AdminViewModel(receptionRepo, exportRepo, syncMgr, systemLogRepo) }

                val currentHoster by authRepo.currentHoster.collectAsState()
                val updateState by updateMgr.uiState.collectAsState()
                var showSyncLogsDialog by remember { mutableStateOf(false) }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, currentHoster?.id) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            updateMgr.checkVersionPolicy(
                                isOnline = syncMgr.isOnline,
                                userId = currentHoster?.id,
                                userName = currentHoster?.name
                            )
                            updateMgr.resumePendingInstallIfPermitted(
                                userId = currentHoster?.id,
                                userName = currentHoster?.name
                            )
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                androidx.compose.runtime.CompositionLocalProvider(
                    com.example.presentation.components.LocalSyncLogsOpener provides { showSyncLogsDialog = true }
                ) {
                    if (updateState.isBlocked) {
                        MandatoryUpdateScreen(
                            state = updateState,
                            isOnline = syncMgr.isOnline,
                            onStartUpdate = {
                                updateMgr.startDownloadAndInstall(
                                    userId = currentHoster?.id,
                                    userName = currentHoster?.name
                                )
                            },
                            onOpenInstallPermissionSettings = {
                                updateMgr.openUnknownAppSourcesSettings()
                            },
                            onRecheckVersion = {
                                updateMgr.checkVersionPolicy(
                                    isOnline = syncMgr.isOnline,
                                    userId = currentHoster?.id,
                                    userName = currentHoster?.name
                                )
                            }
                        )
                    } else if (currentHoster == null) {
                        LoginScreen(
                            viewModel = loginViewModel,
                            onLoginSuccess = { }
                        )
                    } else if (currentHoster?.isAdmin == true) {
                        // Admin user has exclusive access to Hosters Management & Logs
                        com.example.presentation.admin.HostersManagementScreen(
                            authRepository = authRepo,
                            receptionRepository = receptionRepo,
                            syncManager = syncMgr,
                            onLogout = { authRepo.logout() }
                        )
                    } else {
                        MainAppScaffold(
                            receptionViewModel = receptionViewModel,
                            adminViewModel = adminViewModel,
                            actorId = currentHoster?.id,
                            actorName = currentHoster?.name,
                            updateState = updateState,
                            onStartOptionalUpdate = {
                                updateMgr.startDownloadAndInstall(
                                    userId = currentHoster?.id,
                                    userName = currentHoster?.name
                                )
                            },
                            onDismissOptionalUpdate = { updateMgr.dismissOptionalBanner() },
                            onLogout = { authRepo.logout() }
                        )
                    }

                    if (showSyncLogsDialog && !updateState.isBlocked) {
                        com.example.presentation.components.SyncLogsDialog(
                            syncManager = syncMgr,
                            onDismiss = { showSyncLogsDialog = false }
                        )
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
fun MainAppScaffold(
    receptionViewModel: ReceptionViewModel,
    adminViewModel: AdminViewModel,
    actorId: String?,
    actorName: String?,
    updateState: UpdateUiState,
    onStartOptionalUpdate: () -> Unit,
    onDismissOptionalUpdate: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(AppTab.RECEPCAO) }
    var showProfileScreen by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()

    val handleShiftClosed: () -> Unit = {
        selectedTab = AppTab.ADMINISTRACAO
        adminViewModel.refreshRemoteData()
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "Turno encerrado com sucesso! Confira os resultados na tela de Administração.",
                duration = SnackbarDuration.Long
            )
        }
    }

    BackHandler(enabled = showProfileScreen || selectedTab != AppTab.RECEPCAO) {
        if (showProfileScreen) {
            showProfileScreen = false
        } else {
            selectedTab = AppTab.RECEPCAO
        }
    }

    if (showProfileScreen) {
        ProfileScreen(
            viewModel = receptionViewModel,
            onBack = { showProfileScreen = false },
            onLogout = onLogout
        )
        return
    }

    Scaffold(
        containerColor = PaoBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, PaoBorderLight, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .testTag("persistent_bottom_nav")
            ) {
                AppTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PaoRed,
                            selectedTextColor = PaoRed,
                            indicatorColor = PaoCream,
                            unselectedIconColor = PaoAnthraciteMuted,
                            unselectedTextColor = PaoAnthraciteMuted
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OptionalUpdateBanner(
                state = updateState,
                onUpdateClick = onStartOptionalUpdate,
                onDismiss = onDismissOptionalUpdate
            )

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val dur = motionDuration(reducedMotion, 200)
                        fadeIn(tween(dur)) togetherWith fadeOut(tween(dur))
                    },
                    label = "tab_transition"
                ) { tab ->
                    when (tab) {
                        AppTab.RECEPCAO -> ReceptionScreen(
                            viewModel = receptionViewModel,
                            onNavigateToInspection = { selectedTab = AppTab.INSPECAO },
                            onNavigateToAdmin = { selectedTab = AppTab.ADMINISTRACAO },
                            onNavigateToShift = { selectedTab = AppTab.TURNO },
                            onNavigateToProfile = { showProfileScreen = true },
                            onShiftClosed = handleShiftClosed
                        )
                        AppTab.INSPECAO -> InspectionScreen(
                            viewModel = receptionViewModel
                        )
                        AppTab.ADMINISTRACAO -> AdminScreen(
                            viewModel = adminViewModel,
                            actorId = actorId,
                            actorName = actorName
                        )
                        AppTab.TURNO -> ShiftScreen(
                            viewModel = receptionViewModel,
                            onShiftClosed = handleShiftClosed,
                            onNavigateToProfile = { showProfileScreen = true }
                        )
                    }
                }
            }
        }
    }

    if (showConfigDialog) {
        SupabaseSettingsDialog(onDismiss = { showConfigDialog = false })
    }
}

@Composable
fun SupabaseSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(SupabaseConfig.getUrl(context)) }
    var key by remember { mutableStateOf(SupabaseConfig.getKey(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Configuração do Supabase", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Altere a URL e a Chave Anon do projeto Supabase para sincronização em nuvem.",
                    fontSize = 12.sp,
                    color = PaoAnthraciteMuted
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Supabase URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Supabase Anon Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                SupabaseConfig.saveConfig(context, url, key)
                onDismiss()
            }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

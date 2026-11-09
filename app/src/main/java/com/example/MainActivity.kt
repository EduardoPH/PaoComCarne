package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.remote.SupabaseConfig
import com.example.presentation.admin.AdminScreen
import com.example.presentation.admin.AdminViewModel
import com.example.presentation.components.PaoPrimaryButton
import com.example.presentation.components.PaoSecondaryButton
import com.example.presentation.inspection.InspectionScreen
import com.example.presentation.login.LoginScreen
import com.example.presentation.login.LoginViewModel
import com.example.presentation.reception.ReceptionScreen
import com.example.presentation.reception.ReceptionViewModel
import com.example.presentation.shift.ShiftScreen
import com.example.ui.theme.PaoAnthracite
import com.example.ui.theme.PaoAnthraciteMuted
import com.example.ui.theme.PaoBackground
import com.example.ui.theme.PaoBorder
import com.example.ui.theme.PaoBorderLight
import com.example.ui.theme.PaoComCarneTheme
import com.example.ui.theme.PaoCream
import com.example.ui.theme.PaoRed

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

                val loginViewModel = remember { LoginViewModel(authRepo, syncMgr) }
                val receptionViewModel = remember { ReceptionViewModel(authRepo, receptionRepo, syncMgr) }
                val adminViewModel = remember { AdminViewModel(receptionRepo, exportRepo, syncMgr) }

                val currentHoster by authRepo.currentHoster.collectAsState()

                if (currentHoster == null) {
                    LoginScreen(
                        viewModel = loginViewModel,
                        onLoginSuccess = { }
                    )
                } else if (currentHoster?.isAdmin == true) {
                    // Admin user has exclusive access to the Hosters Management Screen
                    com.example.presentation.admin.HostersManagementScreen(
                        authRepository = authRepo,
                        syncManager = syncMgr,
                        onLogout = { authRepo.logout() }
                    )
                } else {
                    MainAppScaffold(
                        receptionViewModel = receptionViewModel,
                        adminViewModel = adminViewModel,
                        onLogout = { authRepo.logout() }
                    )
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
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(AppTab.RECEPCAO) }
    var showConfigDialog by remember { mutableStateOf(false) }

    // Android back handler: pop to Reception tab before exiting
    BackHandler(enabled = selectedTab != AppTab.RECEPCAO) {
        selectedTab = AppTab.RECEPCAO
    }

    Scaffold(
        containerColor = PaoBackground,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = selectedTab, label = "tab_crossfade") { tab ->
                when (tab) {
                    AppTab.RECEPCAO -> ReceptionScreen(
                        viewModel = receptionViewModel,
                        onNavigateToInspection = { selectedTab = AppTab.INSPECAO },
                        onNavigateToAdmin = { selectedTab = AppTab.ADMINISTRACAO },
                        onNavigateToShift = { selectedTab = AppTab.TURNO },
                        onShiftClosed = onLogout
                    )
                    AppTab.INSPECAO -> InspectionScreen(
                        viewModel = receptionViewModel
                    )
                    AppTab.ADMINISTRACAO -> AdminScreen(
                        viewModel = adminViewModel
                    )
                    AppTab.TURNO -> ShiftScreen(
                        viewModel = receptionViewModel,
                        onLogout = onLogout,
                        onShiftClosed = onLogout
                    )
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

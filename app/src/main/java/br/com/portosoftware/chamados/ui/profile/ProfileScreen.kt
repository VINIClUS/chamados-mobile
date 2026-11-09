package br.com.portosoftware.chamados.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.portosoftware.chamados.core.data.AuthRepository
import br.com.portosoftware.chamados.core.data.SessionState
import br.com.portosoftware.chamados.core.data.SettingsRepository
import br.com.portosoftware.chamados.core.model.Role
import br.com.portosoftware.chamados.core.model.RolePolicy
import br.com.portosoftware.chamados.core.model.Secretary
import br.com.portosoftware.chamados.core.model.ThemeMode
import br.com.portosoftware.chamados.core.model.User
import br.com.portosoftware.chamados.ui.common.AvatarButton
import br.com.portosoftware.chamados.ui.common.Banner
import br.com.portosoftware.chamados.ui.common.SectionHeader
import br.com.portosoftware.chamados.ui.preview.PreviewData
import br.com.portosoftware.chamados.ui.theme.ChamadosTheme
import br.com.portosoftware.chamados.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: User? = null,
    val secretary: Secretary? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        val state: StateFlow<ProfileUiState> =
            combine(auth.session, settings.themeMode) { session, theme ->
                val active = session as? SessionState.Active
                ProfileUiState(active?.user, active?.secretary, theme)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

        fun setTheme(mode: ThemeMode) {
            viewModelScope.launch { settings.setThemeMode(mode) }
        }

        fun switchSecretary(secretary: Secretary) {
            viewModelScope.launch { auth.selectSecretary(secretary) }
        }

        fun logout() {
            viewModelScope.launch { auth.logout() }
        }
    }

val Role.label: String
    get() =
        when (this) {
            Role.ADMIN -> "Administrador geral"
            Role.TECHNICAL_ADMIN -> "Administrador técnico"
            Role.TECHNICAL -> "Técnico de TI"
            Role.VIEWER -> "Somente relatórios"
            Role.SYSTEM_EXTERNAL -> "Sistema Externo"
            Role.UNKNOWN -> "Perfil não reconhecido"
        }

@Composable
fun ProfileRoute(
    onBack: (() -> Unit)?,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(state, onBack, viewModel::setTheme, viewModel::switchSecretary, viewModel::logout)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onBack: (() -> Unit)?,
    onTheme: (ThemeMode) -> Unit,
    onSwitchSecretary: (Secretary) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val user = state.user
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") } } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (user != null) {
                if (RolePolicy(user.role).hasNoAppContent) {
                    Banner(
                        if (user.role == Role.UNKNOWN) {
                            "Esta versão do app não reconhece o seu perfil de acesso. Atualize o app ou fale com o administrador."
                        } else {
                            "Seu perfil de acesso é só para relatórios, que ficam na versão web. Este app não tem telas para ele."
                        },
                        icon = Icons.Outlined.Info,
                    )
                }
                ListItem(
                    leadingContent = { AvatarButton(user.name, null) },
                    headlineContent = { Text(user.name, style = MaterialTheme.typography.titleLarge) },
                    supportingContent = { Text(user.role.label) },
                )
                ListItem(leadingContent = {
                    Icon(Icons.Outlined.Email, null)
                }, headlineContent = { Text(user.email) }, overlineContent = { Text("E-mail") })
                ListItem(leadingContent = {
                    Icon(Icons.Outlined.Badge, null)
                }, headlineContent = { Text(user.role.label) }, overlineContent = { Text("Papel") })
                HorizontalDivider()
                SectionHeader("Secretaria ativa")
                user.secretaries.forEach { secretary ->
                    val current = secretary.id == state.secretary?.id
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.Business, null) },
                        headlineContent = { Text(secretary.name) },
                        trailingContent = { if (current) Icon(Icons.Outlined.Check, "Ativa") },
                        modifier = Modifier.clickable(enabled = !current) { onSwitchSecretary(secretary) },
                    )
                }
                HorizontalDivider()
            }
            SectionHeader("Tema")
            val modes = listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Escuro")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen)) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = state.themeMode == mode,
                        onClick = { onTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) { Text(label) }
                }
            }
            HorizontalDivider(Modifier.padding(top = Spacing.xl))
            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text("Sair", color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { confirmLogout = true },
            )
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sair do app?") },
            text = { Text("Você vai precisar entrar de novo para ver os chamados.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text("Sair") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } },
        )
    }
}

@PreviewLightDark
@Composable
private fun ProfilePreview() =
    ChamadosTheme {
        ProfileScreen(ProfileUiState(PreviewData.user, PreviewData.secretary), null, {}, {}, {})
    }

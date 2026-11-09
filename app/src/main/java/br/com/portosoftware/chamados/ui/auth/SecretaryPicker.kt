package br.com.portosoftware.chamados.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.portosoftware.chamados.core.data.AuthRepository
import br.com.portosoftware.chamados.core.data.SessionState
import br.com.portosoftware.chamados.core.data.SettingsRepository
import br.com.portosoftware.chamados.core.model.RolePolicy
import br.com.portosoftware.chamados.core.model.Secretary
import br.com.portosoftware.chamados.core.model.TopLevel
import br.com.portosoftware.chamados.ui.common.firstName
import br.com.portosoftware.chamados.ui.preview.PreviewData
import br.com.portosoftware.chamados.ui.theme.ChamadosTheme
import br.com.portosoftware.chamados.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SecretaryPickerUiState(
    val userName: String = "",
    val secretaries: List<Secretary> = emptyList(),
    val lastUsedId: String? = null,
    /** Onde a secretaria pode ser trocada depois: o VIEWER não tem o Início. */
    val switchHint: String = "no Início ou no Perfil",
) {
    /** O nome só chega com o usuário: antes disso a lista vazia ainda não diz nada. */
    val hasNoSecretaries: Boolean get() = userName.isNotEmpty() && secretaries.isEmpty()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SecretaryPickerViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        settings: SettingsRepository,
    ) : ViewModel() {
        val state: StateFlow<SecretaryPickerUiState> =
            auth.session
                .flatMapLatest { session ->
                    val user = (session as? SessionState.ChoosingSecretary)?.user
                    val last = user?.let { settings.lastSecretaryId(it.id) } ?: flowOf(null)
                    val hasHome = user?.let { TopLevel.HOME in RolePolicy(it.role).destinations } ?: true
                    last.map {
                        SecretaryPickerUiState(
                            userName = user?.name.orEmpty(),
                            secretaries = user?.secretaries.orEmpty(),
                            lastUsedId = it,
                            switchHint = if (hasHome) "no Início ou no Perfil" else "no Perfil",
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecretaryPickerUiState())

        fun select(secretary: Secretary) {
            viewModelScope.launch { auth.selectSecretary(secretary) }
        }

        fun logout() {
            viewModelScope.launch { auth.logout() }
        }
    }

@Composable
fun SecretaryPickerRoute(viewModel: SecretaryPickerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SecretaryPickerScreen(state, onConfirm = viewModel::select, onLogout = viewModel::logout)
}

@Composable
fun SecretaryPickerScreen(
    state: SecretaryPickerUiState,
    onConfirm: (Secretary) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedId by rememberSaveable(state.lastUsedId, state.secretaries) {
        mutableStateOf(state.secretaries.firstOrNull { it.id == state.lastUsedId }?.id ?: state.secretaries.firstOrNull()?.id)
    }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                TextButton(onClick = onLogout) { Text("Sair") }
                Button(
                    onClick = { state.secretaries.firstOrNull { it.id == selectedId }?.let(onConfirm) },
                    enabled = selectedId != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Continuar") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.screen)) {
            Text(
                "Olá, ${state.userName.firstName()}",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = Spacing.xxl),
            )
            Text(
                if (state.hasNoSecretaries) {
                    "Nenhuma equipe vinculada. Solicite o vínculo ao administrador."
                } else {
                    "Escolha a secretaria em que vai trabalhar. Você pode trocar depois ${state.switchHint}."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xl),
            )
            LazyColumn(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(state.secretaries, key = { it.id }) { secretary ->
                    val selected = secretary.id == selectedId
                    OutlinedCard(
                        colors =
                            CardDefaults.outlinedCardColors(
                                containerColor =
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            ),
                        modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) { selectedId = secretary.id },
                    ) {
                        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, onClick = null)
                            Column(Modifier.padding(start = Spacing.md)) {
                                Text(secretary.name, style = MaterialTheme.typography.titleMedium)
                                if (secretary.id == state.lastUsedId) {
                                    Text(
                                        "Última usada",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SecretaryPickerPreview() =
    ChamadosTheme {
        SecretaryPickerScreen(
            SecretaryPickerUiState(PreviewData.user.name, PreviewData.user.secretaries, PreviewData.secretary.id),
            onConfirm = {},
            onLogout = {},
        )
    }

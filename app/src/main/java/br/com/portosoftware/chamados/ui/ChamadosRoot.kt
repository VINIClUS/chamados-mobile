package br.com.portosoftware.chamados.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import br.com.portosoftware.chamados.core.data.SessionState
import br.com.portosoftware.chamados.ui.auth.LoginRoute
import br.com.portosoftware.chamados.ui.auth.SecretaryPickerRoute
import br.com.portosoftware.chamados.ui.navigation.MainScaffold
import br.com.portosoftware.chamados.ui.theme.ChamadosTheme

/**
 * Raiz do app. Quem decide entre Login, Seleção de secretaria e App é o
 * [SessionState]; as telas de autenticação nunca navegam explicitamente.
 */
@Composable
fun ChamadosRoot(
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val lastActive by viewModel.lastActive.collectAsStateWithLifecycle()

    ChamadosTheme(themeMode) {
        Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
            when (val s = session) {
                SessionState.Restoring -> {
                    Splash()
                }

                SessionState.SignedOut -> {
                    LoginRoute()
                }

                is SessionState.ChoosingSecretary -> {
                    SecretaryPickerRoute()
                }

                // Mesmo ramo para Active e Expired: a sessão que expira continua montada por trás do
                // diálogo, sem recriar a navegação nem os ViewModels.
                is SessionState.Active, SessionState.Expired -> {
                    val active = s as? SessionState.Active ?: lastActive
                    if (active != null) SessionScaffold(active, viewModel.sessionStoreOwner(active)) else LoginRoute()
                    if (s == SessionState.Expired) SessionExpiredDialog(onConfirm = viewModel::acknowledgeExpired)
                }
            }
        }
    }
}

/**
 * Trocar de secretaria recria a navegação inteira: caches e telas recarregam, espelhando o header
 * X-Active-Secretary-Id. Os ViewModels das telas ficam no store da sessão, que o [AppViewModel]
 * limpa quando ela termina.
 */
@Composable
private fun SessionScaffold(
    active: SessionState.Active,
    storeOwner: ViewModelStoreOwner,
) {
    key(active.user.id, active.secretary.id) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) { MainScaffold(active.user) }
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun SessionExpiredDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Sessão encerrada") },
        text = { Text("Por segurança, sua sessão terminou. Entre novamente para continuar.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Entrar de novo") } },
    )
}

@Preview
@Composable
private fun SessionExpiredPreview() = ChamadosTheme { SessionExpiredDialog {} }

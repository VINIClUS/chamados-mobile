package br.com.portosoftware.chamados.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.portosoftware.chamados.ui.theme.ChamadosTheme
import br.com.portosoftware.chamados.ui.theme.Spacing

@Composable
fun LoginRoute(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onForgotPasswordClick = viewModel::openForgot,
        onForgotEmailChange = viewModel::onForgotEmailChange,
        onSendResetLink = viewModel::sendForgot,
        onDismissForgotPassword = viewModel::closeForgot,
        onMessageConsume = viewModel::messageShown,
    )
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onForgotEmailChange: (String) -> Unit,
    onSendResetLink: () -> Unit,
    onDismissForgotPassword: () -> Unit,
    onMessageConsume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val currentOnMessageConsume by rememberUpdatedState(onMessageConsume)
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            currentOnMessageConsume()
        }
    }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Icon(
                    Icons.Outlined.SupportAgent,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("Chamados TI", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Entre com sua conta da prefeitura.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = { Text("E-mail") },
                    singleLine = true,
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text("Senha") },
                    singleLine = true,
                    enabled = !state.loading,
                    isError = state.error != null,
                    supportingText = state.error?.let { { Text(it) } },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) "Ocultar senha" else "Mostrar senha",
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = onSubmit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Entrar")
                    }
                }
                TextButton(
                    onClick = onForgotPasswordClick,
                    enabled = !state.loading,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Esqueci a senha")
                }
            }
        }
    }

    if (state.forgotOpen) {
        AlertDialog(
            onDismissRequest = onDismissForgotPassword,
            title = { Text("Redefinir senha") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("Enviaremos um link para o seu e-mail.")
                    OutlinedTextField(
                        value = state.forgotEmail,
                        onValueChange = onForgotEmailChange,
                        label = { Text("E-mail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onSendResetLink, enabled = !state.forgotSending && state.forgotEmail.isNotBlank()) {
                    Text(if (state.forgotSending) "Enviando…" else "Enviar link")
                }
            },
            dismissButton = { TextButton(onClick = onDismissForgotPassword) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun LoginPreview(state: LoginUiState) =
    ChamadosTheme {
        LoginScreen(state, {}, {}, {}, {}, {}, {}, {}, {})
    }

@PreviewLightDark
@Composable
private fun LoginDefaultPreview() = LoginPreview(LoginUiState())

@Preview(name = "Erro de credencial")
@Composable
private fun LoginErrorPreview() = LoginPreview(LoginUiState("tecnico@chamados.test", "1234", error = "E-mail ou senha inválidos."))

@Preview(name = "Carregando")
@Composable
private fun LoginLoadingPreview() = LoginPreview(LoginUiState("tecnico@chamados.test", "demo", loading = true))

@Preview(name = "Esqueci a senha")
@Composable
private fun LoginForgotPreview() = LoginPreview(LoginUiState(forgotOpen = true, forgotEmail = "tecnico@chamados.test"))

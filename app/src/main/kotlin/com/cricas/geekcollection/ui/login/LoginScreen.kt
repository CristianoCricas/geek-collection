package com.cricas.geekcollection.ui.login

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.colorResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cricas.geekcollection.R
import com.cricas.geekcollection.di.AppContainer

@Composable
fun LoginScreen(
    container: AppContainer,
    onDone: () -> Unit,
) {
    val viewModel: LoginViewModel = viewModel { LoginViewModel(container.syncManager, container.settings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val missingEmail = stringResource(R.string.login_missing_email)
    val missingPassword = stringResource(R.string.login_missing_password)
    val resetSent = stringResource(R.string.login_reset_sent)
    val connected = stringResource(R.string.login_connected)
    val created = stringResource(R.string.login_created)
    val configSaved = stringResource(R.string.login_config_saved)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            // The launcher icon is adaptive (not loadable by painterResource); draw its layers by hand.
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(colorResource(R.color.ic_launcher_background), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.login_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(
                            when (state.mode) {
                                LoginMode.SIGN_IN -> R.string.login_title
                                LoginMode.SIGN_UP -> R.string.login_signup_title
                                LoginMode.RESET -> R.string.login_reset_title
                            }
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = viewModel::setEmail,
                        label = { Text(stringResource(R.string.sync_email)) },
                        singleLine = true,
                        enabled = state.configured && !state.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.mode != LoginMode.RESET) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = viewModel::setPassword,
                            label = { Text(stringResource(R.string.sync_password)) },
                            singleLine = true,
                            enabled = state.configured && !state.busy,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            stringResource(R.string.login_reset_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    state.info?.let {
                        Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.submit(missingEmail, missingPassword, resetSent) {
                                Toast.makeText(context, if (state.mode == LoginMode.SIGN_UP) created else connected, Toast.LENGTH_SHORT).show()
                                onDone()
                            }
                        },
                        enabled = state.configured && !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                stringResource(
                                    when (state.mode) {
                                        LoginMode.SIGN_IN -> R.string.sync_sign_in
                                        LoginMode.SIGN_UP -> R.string.sync_sign_up
                                        LoginMode.RESET -> R.string.login_send_reset
                                    }
                                )
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (state.mode != LoginMode.SIGN_IN) {
                            TextButton(onClick = { viewModel.setMode(LoginMode.SIGN_IN) }) { Text(stringResource(R.string.login_have_account)) }
                        } else {
                            TextButton(onClick = { viewModel.setMode(LoginMode.SIGN_UP) }) { Text(stringResource(R.string.sync_sign_up)) }
                        }
                        if (state.mode != LoginMode.RESET) {
                            TextButton(onClick = { viewModel.setMode(LoginMode.RESET) }) { Text(stringResource(R.string.login_forgot)) }
                        }
                    }
                }
            }

            if (!state.configured) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.login_not_configured),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    TextButton(onClick = viewModel::toggleConfig, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.login_config_title) + if (state.configured) " ✓" else "")
                    }
                    if (state.showConfig) {
                        Text(
                            stringResource(R.string.login_config_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.projectId,
                            onValueChange = viewModel::setProjectId,
                            label = { Text(stringResource(R.string.sync_project_id)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.apiKey,
                            onValueChange = viewModel::setApiKey,
                            label = { Text(stringResource(R.string.sync_api_key)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            viewModel.saveConfig { ok ->
                                if (ok) Toast.makeText(context, configSaved, Toast.LENGTH_SHORT).show()
                            }
                        }) { Text(stringResource(R.string.action_save)) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { viewModel.skip(onDone) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.login_skip))
            }
            Text(
                stringResource(R.string.login_skip_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

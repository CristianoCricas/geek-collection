package com.cricas.geekcollection.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cricas.geekcollection.R
import com.cricas.geekcollection.di.AppContainer

private val wikipediaLanguages = listOf("pt" to "Português", "en" to "English", "es" to "Español")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val initial = remember { container.settings.current }
    var rawgKey by remember { mutableStateOf(initial.rawgApiKey) }
    var wikiLang by remember { mutableStateOf(initial.wikipediaLanguage) }
    var fbProject by remember { mutableStateOf(initial.firebaseProjectId) }
    var fbKey by remember { mutableStateOf(initial.firebaseApiKey) }
    var autoSync by remember { mutableStateOf(initial.autoSync) }
    val syncManager = container.syncManager
    val syncState by syncManager.state.collectAsStateWithLifecycle()
    var signedIn by remember { mutableStateOf(syncManager.firebase.isSignedIn) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun saveAll() {
        container.settings.update(
            initial.copy(rawgApiKey = rawgKey, wikipediaLanguage = wikiLang, firebaseProjectId = fbProject, firebaseApiKey = fbKey, autoSync = autoSync)
        )
    }

    fun credential(signUp: Boolean) {
        saveAll()
        if (fbProject.isBlank() || fbKey.isBlank()) { authError = context.getString(R.string.sync_fill_config); return }
        if (email.isBlank() || password.isBlank()) { authError = context.getString(R.string.field_title_required).replace("um título", "e-mail e senha"); return }
        authBusy = true
        authError = null
        scope.launch {
            val result = runCatching { if (signUp) syncManager.firebase.signUp(email.trim(), password) else syncManager.firebase.signIn(email.trim(), password) }
            authBusy = false
            result.onSuccess {
                signedIn = true
                password = ""
                syncManager.syncNow()
            }.onFailure { authError = it.message ?: it::class.java.simpleName }
        }
    }
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_rawg_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_rawg_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rawgKey,
                        onValueChange = { rawgKey = it },
                        placeholder = { Text(stringResource(R.string.settings_rawg_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_wikipedia_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_wikipedia_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        wikipediaLanguages.forEach { (code, name) ->
                            FilterChip(selected = wikiLang == code, onClick = { wikiLang = code }, label = { Text(name) })
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("☁️ " + stringResource(R.string.sync_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.sync_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.sync_setup), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = fbProject,
                        onValueChange = { fbProject = it },
                        label = { Text(stringResource(R.string.sync_project_id)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fbKey,
                        onValueChange = { fbKey = it },
                        label = { Text(stringResource(R.string.sync_api_key)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.sync_auto), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.sync_auto_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoSync, onCheckedChange = { autoSync = it })
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    if (signedIn) {
                        val session = syncManager.firebase.auth
                        Text(stringResource(R.string.sync_signed_in_as, session?.email ?: session?.uid ?: ""), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (syncState.lastSyncAt > 0L) stringResource(R.string.sync_last, java.text.DateFormat.getDateTimeInstance().format(java.util.Date(syncState.lastSyncAt)))
                            else stringResource(R.string.sync_never),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (syncState.running) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(syncState.status, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        syncState.lastError?.let { Text(stringResource(R.string.sync_failed, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { saveAll(); syncManager.syncNow() }, enabled = !syncState.running) { Text(stringResource(R.string.sync_now)) }
                            OutlinedButton(onClick = { syncManager.signOut(); signedIn = false }) { Text(stringResource(R.string.sync_sign_out)) }
                        }
                    } else {
                        val enabled = fbProject.isNotBlank() && fbKey.isNotBlank() && !authBusy
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(stringResource(R.string.sync_email)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.sync_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { credential(signUp = false) }, enabled = enabled) { Text(stringResource(R.string.sync_sign_in)) }
                            OutlinedButton(onClick = { credential(signUp = true) }, enabled = enabled) { Text(stringResource(R.string.sync_sign_up)) }
                        }
                        Text(
                            stringResource(if (fbProject.isNotBlank() && fbKey.isNotBlank()) R.string.sync_same_credentials else R.string.sync_fill_config),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        authError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            Button(
                onClick = {
                    saveAll()
                    Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_sources_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_sources_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_about_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_about_body, versionName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

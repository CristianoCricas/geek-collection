package com.cricas.geekcollection.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cricas.geekcollection.data.settings.SettingsRepository
import com.cricas.geekcollection.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginMode { SIGN_IN, SIGN_UP, RESET }

data class LoginUiState(
    val mode: LoginMode = LoginMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val configured: Boolean = false,
    val projectId: String = "",
    val apiKey: String = "",
    val showConfig: Boolean = false,
)

class LoginViewModel(
    private val syncManager: SyncManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LoginUiState(
            configured = syncManager.firebase.isConfigured,
            projectId = settings.current.firebaseProjectId,
            apiKey = settings.current.firebaseApiKey,
            showConfig = !syncManager.firebase.isConfigured,
        )
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun setMode(mode: LoginMode) = _state.update { it.copy(mode = mode, error = null, info = null) }
    fun setEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun setPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun setProjectId(v: String) = _state.update { it.copy(projectId = v) }
    fun setApiKey(v: String) = _state.update { it.copy(apiKey = v) }
    fun toggleConfig() = _state.update { it.copy(showConfig = !it.showConfig) }

    fun saveConfig(onSaved: (configured: Boolean) -> Unit) {
        val s = _state.value
        settings.update(settings.current.copy(firebaseProjectId = s.projectId.trim(), firebaseApiKey = s.apiKey.trim()))
        val configured = syncManager.firebase.isConfigured
        _state.update { it.copy(configured = configured, showConfig = !configured, error = null) }
        onSaved(configured)
    }

    /** Signs in, signs up or sends the reset e-mail depending on the mode. */
    fun submit(
        missingEmail: String,
        missingPassword: String,
        resetSent: String,
        onSignedIn: () -> Unit,
    ) {
        val s = _state.value
        val email = s.email.trim()
        if (email.isBlank()) { _state.update { it.copy(error = missingEmail) }; return }
        if (s.mode != LoginMode.RESET && s.password.isBlank()) { _state.update { it.copy(error = missingPassword) }; return }
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            val result = runCatching {
                when (s.mode) {
                    LoginMode.RESET -> syncManager.firebase.sendPasswordReset(email)
                    LoginMode.SIGN_UP -> syncManager.firebase.signUp(email, s.password)
                    LoginMode.SIGN_IN -> syncManager.firebase.signIn(email, s.password)
                }
            }
            result.onSuccess {
                if (s.mode == LoginMode.RESET) {
                    _state.update { it.copy(busy = false, mode = LoginMode.SIGN_IN, info = resetSent) }
                } else {
                    settings.setSkipLogin(false)
                    _state.update { it.copy(busy = false, password = "") }
                    syncManager.syncNow()
                    onSignedIn()
                }
            }.onFailure { e ->
                _state.update { it.copy(busy = false, error = e.message ?: e::class.java.simpleName) }
            }
        }
    }

    fun skip(onDone: () -> Unit) {
        settings.setSkipLogin(true)
        onDone()
    }
}

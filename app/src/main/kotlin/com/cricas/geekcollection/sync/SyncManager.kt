package com.cricas.geekcollection.sync

import com.cricas.geekcollection.core.sync.FirebaseClient
import com.cricas.geekcollection.core.sync.SyncEngine
import com.cricas.geekcollection.core.sync.SyncSummary
import com.cricas.geekcollection.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncState(
    val running: Boolean = false,
    val status: String = "",
    val lastSummary: SyncSummary? = null,
    val lastError: String? = null,
    val lastSyncAt: Long = 0L,
)

/**
 * App-level coordinator: runs the [SyncEngine], exposes its state to the UI,
 * debounces background syncs after local edits and syncs on app start.
 */
class SyncManager(
    val firebase: FirebaseClient,
    private val engine: SyncEngine,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _state = MutableStateFlow(SyncState(lastSyncAt = settings.lastSyncAt))
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private var scheduled: Job? = null
    private var current: Job? = null

    val canSync: Boolean get() = firebase.isConfigured && firebase.isSignedIn

    /** Runs a sync now unless one is already running. */
    fun syncNow(onDone: ((Result<SyncSummary>) -> Unit)? = null) {
        if (!canSync) { onDone?.invoke(Result.failure(IllegalStateException("Configure e faça login para sincronizar"))); return }
        if (current?.isActive == true) return
        current = scope.launch {
            _state.update { it.copy(running = true, status = "Sincronizando…", lastError = null) }
            val result = runCatching { engine.sync { msg -> _state.update { it.copy(status = msg) } } }
            result.onSuccess { summary ->
                settings.lastSyncAt = System.currentTimeMillis()
                _state.update { it.copy(running = false, status = "", lastSummary = summary, lastSyncAt = settings.lastSyncAt) }
            }.onFailure { e ->
                _state.update { it.copy(running = false, status = "", lastError = e.message ?: e::class.java.simpleName) }
            }
            onDone?.invoke(result)
        }
    }

    /** Debounced sync after a local change (only when auto sync is on). */
    fun scheduleAfterChange(delayMs: Long = 4_000) {
        if (!settings.current.autoSync || !canSync) return
        scheduled?.cancel()
        scheduled = scope.launch {
            delay(delayMs)
            syncNow()
        }
    }

    /** Called when the app comes to the foreground. */
    fun autoSync() {
        if (settings.current.autoSync && canSync) syncNow()
    }

    fun signOut() {
        firebase.signOut()
        settings.syncCursor = null
        _state.update { SyncState() }
    }
}

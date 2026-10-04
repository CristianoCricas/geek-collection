package com.cricas.geekcollection.ui.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cricas.geekcollection.core.lookup.LookupOutcome
import com.cricas.geekcollection.core.lookup.LookupResult
import com.cricas.geekcollection.core.lookup.LookupService
import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.core.recognition.RecognitionResult
import com.cricas.geekcollection.data.ImageStore
import com.cricas.geekcollection.di.DraftHolder
import com.cricas.geekcollection.recognition.MlKitImageRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Recognizing : ScanUiState
    data class Searching(val recognition: RecognitionResult, val imagePath: String?) : ScanUiState
    data class Results(val recognition: RecognitionResult, val outcome: LookupOutcome, val imagePath: String?) : ScanUiState
    data class Error(val message: String, val imagePath: String?) : ScanUiState
}

class ScanViewModel(
    private val recognizer: MlKitImageRecognizer,
    private val lookupService: LookupService,
    private val imageStore: ImageStore,
    private val draftHolder: DraftHolder,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val _categoryHint = MutableStateFlow<ItemCategory?>(null)
    val categoryHint: StateFlow<ItemCategory?> = _categoryHint.asStateFlow()

    var pendingCaptureUri: Uri? = null

    fun setCategoryHint(category: ItemCategory?) {
        _categoryHint.value = category
    }

    fun onPhotoCaptured(success: Boolean) {
        val uri = pendingCaptureUri ?: return
        pendingCaptureUri = null
        if (success) analyze(uri)
    }

    fun analyze(uri: Uri?) {
        uri ?: return
        _state.value = ScanUiState.Recognizing
        viewModelScope.launch {
            val imagePath = runCatching { imageStore.persist(uri) }.getOrNull()
            val recognition = try {
                recognizer.recognize(uri)
            } catch (e: Exception) {
                _state.value = ScanUiState.Error(e.message ?: e::class.java.simpleName, imagePath)
                return@launch
            }
            if (recognition.isEmpty) {
                _state.value = ScanUiState.Results(
                    recognition,
                    LookupOutcome(null, null, null, null, emptyList()),
                    imagePath,
                )
                return@launch
            }
            _state.value = ScanUiState.Searching(recognition, imagePath)
            val outcome = runCatching { lookupService.lookup(recognition, _categoryHint.value) }
                .getOrElse { LookupOutcome(_categoryHint.value, null, null, null, emptyList(), listOf(it.message ?: "erro")) }
            _state.value = ScanUiState.Results(recognition, outcome, imagePath)
        }
    }

    fun retrySearch() {
        val current = _state.value
        val (recognition, imagePath) = when (current) {
            is ScanUiState.Results -> current.recognition to current.imagePath
            is ScanUiState.Searching -> current.recognition to current.imagePath
            else -> return
        }
        _state.value = ScanUiState.Searching(recognition, imagePath)
        viewModelScope.launch {
            val outcome = runCatching { lookupService.lookup(recognition, _categoryHint.value) }
                .getOrElse { LookupOutcome(_categoryHint.value, null, null, null, emptyList(), listOf(it.message ?: "erro")) }
            _state.value = ScanUiState.Results(recognition, outcome, imagePath)
        }
    }

    /** Base item built only from what was recognized, used as fallback for the draft. */
    private fun recognizedItem(results: ScanUiState.Results): CollectionItem {
        val category = results.outcome.suggestedCategory ?: _categoryHint.value ?: ItemCategory.OTHER
        return CollectionItem(
            title = results.outcome.titleGuess.orEmpty(),
            category = category,
            platform = results.outcome.suggestedPlatform,
            barcode = results.outcome.barcode,
            localImagePath = results.imagePath,
            completionPercent = if (category.supportsCompletion) 0 else null,
        )
    }

    fun pick(result: LookupResult) {
        val current = _state.value as? ScanUiState.Results ?: return
        draftHolder.put(result, recognizedItem(current))
    }

    fun proceedManually() {
        val current = _state.value as? ScanUiState.Results ?: return
        draftHolder.put(recognizedItem(current))
    }

    fun reset() {
        val path = when (val s = _state.value) {
            is ScanUiState.Results -> s.imagePath
            is ScanUiState.Error -> s.imagePath
            is ScanUiState.Searching -> s.imagePath
            else -> null
        }
        path?.let { viewModelScope.launch { imageStore.delete(it) } }
        _state.value = ScanUiState.Idle
    }
}

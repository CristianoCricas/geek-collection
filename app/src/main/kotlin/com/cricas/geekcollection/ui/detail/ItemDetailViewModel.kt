package com.cricas.geekcollection.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ProgressStatus
import com.cricas.geekcollection.data.ItemRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Loaded(val item: CollectionItem) : DetailUiState
}

class ItemDetailViewModel(private val repository: ItemRepository, private val itemId: Long) : ViewModel() {

    val uiState: StateFlow<DetailUiState> = repository.observeItem(itemId)
        .map { item -> if (item == null) DetailUiState.NotFound else DetailUiState.Loaded(item) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    fun setCompletion(percent: Int) {
        viewModelScope.launch { repository.setCompletion(itemId, percent) }
    }

    fun setStatus(item: CollectionItem, status: ProgressStatus) {
        viewModelScope.launch { repository.setStatus(item, status) }
    }

    fun setPlatinum(item: CollectionItem, platinum: Boolean) {
        viewModelScope.launch { repository.setPlatinum(item, platinum) }
    }

    fun setBacklog(item: CollectionItem, backlog: Boolean) {
        viewModelScope.launch { repository.setBacklog(item, backlog) }
    }

    fun markCompleted(item: CollectionItem) {
        viewModelScope.launch { repository.markCompleted(item) }
    }

    fun toggleFavorite(current: Boolean) {
        viewModelScope.launch { repository.setFavorite(itemId, !current) }
    }

    fun delete(item: CollectionItem, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.delete(item)
            onDone()
        }
    }
}

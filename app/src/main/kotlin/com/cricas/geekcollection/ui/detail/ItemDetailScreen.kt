package com.cricas.geekcollection.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cricas.geekcollection.R
import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.di.AppContainer
import com.cricas.geekcollection.ui.components.CompletionBar
import com.cricas.geekcollection.ui.components.ItemCover
import com.cricas.geekcollection.ui.components.creatorLabelRes
import com.cricas.geekcollection.ui.components.icon
import com.cricas.geekcollection.ui.components.label
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    container: AppContainer,
    itemId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val viewModel: ItemDetailViewModel = viewModel(key = "detail_$itemId") {
        ItemDetailViewModel(container.repository, itemId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state as? DetailUiState.Loaded)?.item?.category?.label() ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    (state as? DetailUiState.Loaded)?.item?.let { item ->
                        IconButton(onClick = { viewModel.toggleFavorite(item.favorite) }) {
                            Icon(
                                imageVector = if (item.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (item.favorite) R.string.action_unfavorite else R.string.action_favorite
                                ),
                                tint = if (item.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state is DetailUiState.Loaded) {
                FloatingActionButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                }
            }
        },
    ) { padding ->
        when (val s = state) {
            DetailUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            DetailUiState.NotFound -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.detail_not_found))
            }
            is DetailUiState.Loaded -> DetailContent(
                item = s.item,
                modifier = Modifier.padding(padding),
                onCompletionChange = viewModel::setCompletion,
            )
        }
    }

    if (confirmDelete) {
        val item = (state as? DetailUiState.Loaded)?.item
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.edit_delete_confirm_title)) },
            text = { Text(stringResource(R.string.edit_delete_confirm_body, item?.title ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (item != null) viewModel.delete(item, onBack)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun DetailContent(
    item: CollectionItem,
    modifier: Modifier = Modifier,
    onCompletionChange: (Int) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 96.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            ItemCover(item = item, modifier = Modifier.size(width = 120.dp, height = 160.dp), cornerRadius = 16)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(item.category.label()) },
                    leadingIcon = { Icon(item.category.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                item.platform?.let { InfoLine(stringResource(R.string.field_platform), it) }
                item.releaseYear?.let { InfoLine(stringResource(R.string.field_release_year), it.toString()) }
            }
        }

        if (item.category.supportsCompletion) {
            Spacer(Modifier.height(20.dp))
            CompletionSection(percent = item.completionOrZero, onCompletionChange = onCompletionChange)
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        item.creator?.let { InfoLine(stringResource(item.category.creatorLabelRes()), it) }
        item.publisher?.let { InfoLine(stringResource(R.string.field_publisher), it) }
        item.barcode?.let { InfoLine(stringResource(R.string.field_barcode), it) }

        item.description?.let {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.field_description), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        item.notes?.let {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.field_notes), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.field_added_on, DateFormat.getDateInstance().format(Date(item.createdAt))),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        item.externalSource?.let {
            Text(
                stringResource(R.string.field_source, it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompletionSection(percent: Int, onCompletionChange: (Int) -> Unit) {
    var sliderValue by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    LaunchedEffect(percent) { sliderValue = percent.toFloat() }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.detail_completion_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            CompletionBar(percent = sliderValue.roundToInt())
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onCompletionChange(sliderValue.roundToInt()) },
                valueRange = 0f..100f,
                steps = 19,
            )
            if (percent < 100) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onCompletionChange(100) }) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.detail_mark_completed))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

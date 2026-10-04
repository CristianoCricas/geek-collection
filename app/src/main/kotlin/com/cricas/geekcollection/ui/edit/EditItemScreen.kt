package com.cricas.geekcollection.ui.edit

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cricas.geekcollection.R
import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.core.model.Platforms
import com.cricas.geekcollection.di.AppContainer
import com.cricas.geekcollection.ui.components.CompletionBar
import com.cricas.geekcollection.ui.components.ItemCover
import com.cricas.geekcollection.ui.components.LookupResultsSheet
import com.cricas.geekcollection.ui.components.creatorLabelRes
import com.cricas.geekcollection.ui.components.icon
import com.cricas.geekcollection.ui.components.label
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemScreen(
    container: AppContainer,
    itemId: Long?,
    fromDraft: Boolean,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val viewModel: EditItemViewModel = viewModel(key = "edit_${itemId ?: "new"}") {
        EditItemViewModel(
            repository = container.repository,
            imageStore = container.imageStore,
            lookupService = container.lookupService,
            itemId = itemId,
            draft = if (fromDraft) container.draftHolder.consume() else null,
        )
    }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onPhotoCaptured(success)
    }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onPhotoPicked(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (itemId == null) R.string.edit_title_new else R.string.edit_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.save { id ->
                                Toast.makeText(context, R.string.edit_saved, Toast.LENGTH_SHORT).show()
                                if (itemId == null) onSaved(id) else onBack()
                            }
                        },
                        enabled = !form.saving && !form.loading,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        if (form.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Picture
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemCover(item = form.toItem(), modifier = Modifier.size(width = 96.dp, height = 128.dp))
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val uri = container.imageStore.newCaptureUri()
                        viewModel.pendingCaptureUri = uri
                        try {
                            takePicture.launch(uri)
                        } catch (e: ActivityNotFoundException) {
                            viewModel.pendingCaptureUri = null
                            Toast.makeText(context, R.string.scan_camera_unavailable, Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.edit_take_photo))
                    }
                    OutlinedButton(onClick = {
                        pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.edit_pick_photo))
                    }
                    if (form.localImagePath != null) {
                        TextButton(onClick = viewModel::removePhoto) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.edit_remove_photo))
                        }
                    }
                }
            }

            // Title + online search
            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> viewModel.update { copy(title = v) } },
                label = { Text(stringResource(R.string.field_title)) },
                isError = form.titleError,
                supportingText = if (form.titleError) {
                    { Text(stringResource(R.string.field_title_required)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::searchOnline,
                modifier = Modifier.fillMaxWidth(),
                enabled = search !is OnlineSearchState.Loading,
            ) {
                Icon(Icons.Filled.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.edit_search_online))
            }
            Text(
                stringResource(R.string.edit_search_online_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Category
            Text(stringResource(R.string.field_category), style = MaterialTheme.typography.titleSmall)
            CategoryPicker(selected = form.category, onSelect = viewModel::setCategory)

            // Platform (games, consoles, accessories)
            if (form.category.supportsPlatform) {
                PlatformField(
                    value = form.platform,
                    custom = form.customPlatform,
                    onValueChange = { v, custom -> viewModel.update { copy(platform = v, customPlatform = custom) } },
                )
            }

            // Completion (board games, games, books, comics)
            if (form.category.supportsCompletion) {
                Column {
                    Text(stringResource(R.string.field_completion), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    CompletionBar(percent = form.completionPercent)
                    Slider(
                        value = form.completionPercent.toFloat(),
                        onValueChange = { v -> viewModel.update { copy(completionPercent = v.roundToInt()) } },
                        valueRange = 0f..100f,
                        steps = 19,
                    )
                }
            }

            OutlinedTextField(
                value = form.creator,
                onValueChange = { v -> viewModel.update { copy(creator = v) } },
                label = { Text(stringResource(form.category.creatorLabelRes())) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.publisher,
                onValueChange = { v -> viewModel.update { copy(publisher = v) } },
                label = { Text(stringResource(R.string.field_publisher)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.releaseYear,
                    onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) viewModel.update { copy(releaseYear = v) } },
                    label = { Text(stringResource(R.string.field_release_year)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.barcode,
                    onValueChange = { v -> viewModel.update { copy(barcode = v) } },
                    label = { Text(stringResource(R.string.field_barcode)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1.4f),
                )
            }
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.update { copy(description = v) } },
                label = { Text(stringResource(R.string.field_description)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> viewModel.update { copy(notes = v) } },
                label = { Text(stringResource(R.string.field_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (val s = search) {
        OnlineSearchState.Hidden -> Unit
        else -> LookupResultsSheet(
            state = s,
            onDismiss = viewModel::dismissSearch,
            onPick = viewModel::applyResult,
            onRetry = viewModel::searchOnline,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(selected: ItemCategory, onSelect: (ItemCategory) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ItemCategory.entries.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category.label(), maxLines = 1) },
                leadingIcon = { Icon(category.icon(), contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlatformField(
    value: String,
    custom: Boolean,
    onValueChange: (String, Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val customLabel = stringResource(R.string.field_platform_custom)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = if (custom) customLabel else value,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.field_platform)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                var lastBrand: String? = null
                Platforms.all.forEach { platform ->
                    if (platform.brand != lastBrand) {
                        lastBrand = platform.brand
                        Text(
                            platform.brand,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(platform.name) },
                        onClick = { onValueChange(platform.name, false); expanded = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text(customLabel) },
                    onClick = { onValueChange(if (custom) value else "", true); expanded = false },
                )
            }
        }
        if (custom) {
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it, true) },
                placeholder = { Text(stringResource(R.string.field_platform_custom_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

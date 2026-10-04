package com.cricas.geekcollection.ui.scan

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cricas.geekcollection.R
import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.core.recognition.RecognitionResult
import com.cricas.geekcollection.di.AppContainer
import com.cricas.geekcollection.ui.components.ItemCover
import com.cricas.geekcollection.ui.components.LookupResultRow
import com.cricas.geekcollection.ui.components.icon
import com.cricas.geekcollection.ui.components.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onProceedToEdit: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: ScanViewModel = viewModel {
        ScanViewModel(container.recognizer, container.lookupService, container.imageStore, container.draftHolder)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categoryHint by viewModel.categoryHint.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onPhotoCaptured(success)
    }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.analyze(uri)
    }

    val launchCamera = {
        val uri = container.imageStore.newCaptureUri()
        viewModel.pendingCaptureUri = uri
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            viewModel.pendingCaptureUri = null
            Toast.makeText(context, R.string.scan_camera_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
    val launchGallery = {
        pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                ScanUiState.Idle -> IdleContent(
                    categoryHint = categoryHint,
                    onCategoryHint = viewModel::setCategoryHint,
                    rawgConfigured = container.rawgProvider.isConfigured,
                    onTakePhoto = launchCamera,
                    onPickPhoto = launchGallery,
                    onOpenSettings = onOpenSettings,
                )
                ScanUiState.Recognizing -> Progress(stringResource(R.string.scan_recognizing))
                is ScanUiState.Searching -> Progress(stringResource(R.string.scan_searching))
                is ScanUiState.Error -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.scan_error, s.message), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::reset) { Text(stringResource(R.string.action_retry)) }
                }
                is ScanUiState.Results -> ResultsContent(
                    state = s,
                    categoryHint = categoryHint,
                    onPick = { viewModel.pick(it); onProceedToEdit() },
                    onManual = { viewModel.proceedManually(); onProceedToEdit() },
                    onRetry = viewModel::retrySearch,
                    onNewPhoto = viewModel::reset,
                )
            }
        }
    }
}

@Composable
private fun Progress(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message)
    }
}

@Composable
private fun IdleContent(
    categoryHint: ItemCategory?,
    onCategoryHint: (ItemCategory?) -> Unit,
    rawgConfigured: Boolean,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(
            Icons.Filled.DocumentScanner,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.scan_intro_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.scan_intro_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        Text(
            stringResource(R.string.scan_category_hint),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
            item {
                FilterChip(
                    selected = categoryHint == null,
                    onClick = { onCategoryHint(null) },
                    label = { Text(stringResource(R.string.scan_category_auto)) },
                )
            }
            items(ItemCategory.entries) { category ->
                FilterChip(
                    selected = categoryHint == category,
                    onClick = { onCategoryHint(if (categoryHint == category) null else category) },
                    label = { Text(category.label()) },
                    leadingIcon = { Icon(category.icon(), contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.scan_take_photo))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPickPhoto, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.scan_pick_photo))
        }

        if (!rawgConfigured && (categoryHint == null || categoryHint == ItemCategory.VIDEO_GAME)) {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.scan_rawg_hint), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ResultsContent(
    state: ScanUiState.Results,
    categoryHint: ItemCategory?,
    onPick: (com.cricas.geekcollection.core.lookup.LookupResult) -> Unit,
    onManual: () -> Unit,
    onRetry: () -> Unit,
    onNewPhoto: () -> Unit,
) {
    val outcome = state.outcome
    val previewItem = CollectionItem(
        title = outcome.titleGuess ?: "",
        category = outcome.suggestedCategory ?: categoryHint ?: ItemCategory.OTHER,
        localImagePath = state.imagePath,
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp)) {
                    ItemCover(item = previewItem, modifier = Modifier.size(width = 80.dp, height = 106.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.scan_recognized_title), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        RecognizedSummary(state.recognition, outcome.barcode, outcome.suggestedPlatform, outcome.suggestedCategory)
                    }
                }
            }
        }

        if (state.recognition.isEmpty) {
            item {
                Text(stringResource(R.string.scan_nothing_recognized), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (outcome.candidates.isEmpty()) {
            item {
                Text(stringResource(R.string.scan_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            item { Text(stringResource(R.string.scan_results_title), style = MaterialTheme.typography.titleMedium) }
            items(outcome.candidates) { result ->
                LookupResultRow(result = result, onClick = { onPick(result) })
            }
        }

        if (outcome.errors.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.scan_provider_errors, outcome.errors.joinToString("; ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_skip))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f), enabled = !state.recognition.isEmpty) {
                        Text(stringResource(R.string.action_retry))
                    }
                    OutlinedButton(onClick = onNewPhoto, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scan_take_photo))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognizedSummary(
    recognition: RecognitionResult,
    barcode: String?,
    platform: String?,
    category: ItemCategory?,
) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    barcode?.let { Text(stringResource(R.string.scan_recognized_barcode, it), style = style, color = color) }
    category?.let { Text(stringResource(R.string.scan_recognized_category, it.label()), style = style, color = color) }
    platform?.let { Text(stringResource(R.string.scan_recognized_platform, it), style = style, color = color) }
    if (recognition.textLines.isNotEmpty()) {
        Text(
            stringResource(R.string.scan_recognized_text, recognition.textLines.take(4).joinToString(" / ")),
            style = style,
            color = color,
            maxLines = 3,
        )
    }
    if (recognition.labels.isNotEmpty()) {
        Text(
            stringResource(R.string.scan_recognized_labels, recognition.labels.take(4).joinToString(", ") { it.text }),
            style = style,
            color = color,
            maxLines = 2,
        )
    }
}

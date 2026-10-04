package com.cricas.geekcollection.core.lookup

import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.core.recognition.BarcodeClassifier
import com.cricas.geekcollection.core.recognition.BarcodeKind
import com.cricas.geekcollection.core.recognition.CategorySuggester
import com.cricas.geekcollection.core.recognition.PlatformDetector
import com.cricas.geekcollection.core.recognition.RecognitionResult
import com.cricas.geekcollection.core.recognition.TitleGuesser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Turns what the camera recognized into concrete candidates by querying the
 * online databases that make sense for the (suggested) category.
 */
class LookupService(private val providers: List<LookupProvider>) {

    suspend fun lookup(recognition: RecognitionResult, categoryHint: ItemCategory? = null): LookupOutcome {
        val category = categoryHint ?: CategorySuggester.suggest(recognition)
        val platform = PlatformDetector.detect(recognition.textLines)?.name
        val titles = TitleGuesser.rank(recognition.textLines)
        val barcode = recognition.barcodes.firstOrNull { BarcodeClassifier.classify(it) != BarcodeKind.UNKNOWN }
            ?.let { BarcodeClassifier.normalize(it) }

        val errors = mutableListOf<String>()
        val candidates = mutableListOf<LookupResult>()

        if (barcode != null) {
            val (found, failed) = runProviders(barcodeProvidersFor(barcode)) { it.searchByBarcode(barcode) }
            candidates += found
            errors += failed
        }

        // Search by the most promising title(s) until something comes back.
        for (title in titles.take(2)) {
            if (candidates.size >= MIN_RESULTS) break
            val (found, failed) = runProviders(titleProvidersFor(category)) { it.searchByTitle(title, 5) }
            candidates += found
            errors += failed
        }

        return LookupOutcome(
            suggestedCategory = category,
            suggestedPlatform = platform,
            titleGuess = titles.firstOrNull(),
            barcode = barcode,
            candidates = candidates.distinctBy { "${it.source}:${it.externalId ?: it.title.lowercase()}" }
                .map { it.applyingHints(category, platform) },
            errors = errors.distinct(),
        )
    }

    /** Manual search typed by the user on the edit form. */
    suspend fun searchByTitle(title: String, category: ItemCategory?, platform: String? = null): LookupOutcome {
        val (found, failed) = runProviders(titleProvidersFor(category)) { it.searchByTitle(title, 8) }
        return LookupOutcome(
            suggestedCategory = category,
            suggestedPlatform = platform,
            titleGuess = title,
            barcode = null,
            candidates = found.distinctBy { "${it.source}:${it.externalId ?: it.title.lowercase()}" }
                .map { it.applyingHints(category, platform) },
            errors = failed,
        )
    }

    suspend fun searchByBarcode(barcode: String, category: ItemCategory?): LookupOutcome {
        val normalized = BarcodeClassifier.normalize(barcode)
        val (found, failed) = runProviders(barcodeProvidersFor(normalized)) { it.searchByBarcode(normalized) }
        return LookupOutcome(
            suggestedCategory = category ?: found.firstNotNullOfOrNull { it.category },
            suggestedPlatform = null,
            titleGuess = null,
            barcode = normalized,
            candidates = found.map { it.applyingHints(category, null) },
            errors = failed,
        )
    }

    private fun barcodeProvidersFor(barcode: String): List<LookupProvider> =
        when (BarcodeClassifier.classify(barcode)) {
            BarcodeKind.ISBN -> providers.filter { it.id == "openlibrary" || it.id == "upcitemdb" }
            BarcodeKind.EAN_UPC -> providers.filter { it.id == "upcitemdb" }
            BarcodeKind.UNKNOWN -> emptyList()
        }

    internal fun titleProvidersFor(category: ItemCategory?): List<LookupProvider> {
        val specific = providers.filter { p -> category != null && category in p.categories }
        val generic = providers.filter { it.categories.isEmpty() && it.id != "upcitemdb" }
        return when {
            category == null -> providers.filter { it.id != "upcitemdb" }
            specific.isNotEmpty() -> specific + generic
            else -> generic
        }
    }

    private suspend fun runProviders(
        selected: List<LookupProvider>,
        block: suspend (LookupProvider) -> List<LookupResult>,
    ): Pair<List<LookupResult>, List<String>> = coroutineScope {
        val jobs = selected.map { provider ->
            async { provider.id to runCatching { block(provider) } }
        }
        val found = mutableListOf<LookupResult>()
        val failed = mutableListOf<String>()
        for (job in jobs) {
            val (providerId, result) = job.await()
            result.onSuccess { found += it }.onFailure { failed += "$providerId: ${it.message ?: it::class.simpleName}" }
        }
        found to failed
    }

    private fun LookupResult.applyingHints(category: ItemCategory?, platform: String?): LookupResult = copy(
        category = this.category ?: category,
        platform = this.platform ?: platform?.takeIf { (this.category ?: category)?.supportsPlatform == true },
    )

    private companion object {
        const val MIN_RESULTS = 3
    }
}

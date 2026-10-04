package com.cricas.geekcollection.core.lookup

import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ItemCategory

/** A candidate found in an online database that can pre-fill a new item. */
data class LookupResult(
    val title: String,
    val category: ItemCategory?,
    val source: String,
    val externalId: String? = null,
    val platform: String? = null,
    val description: String? = null,
    val creator: String? = null,
    val publisher: String? = null,
    val releaseYear: Int? = null,
    val coverUrl: String? = null,
    val barcode: String? = null,
) {
    fun toItem(fallbackCategory: ItemCategory = ItemCategory.OTHER): CollectionItem = CollectionItem(
        title = title,
        category = category ?: fallbackCategory,
        platform = platform,
        description = description,
        creator = creator,
        publisher = publisher,
        releaseYear = releaseYear,
        barcode = barcode,
        coverUrl = coverUrl,
        externalSource = source,
        externalId = externalId,
    ).normalized()
}

/** Result of the whole recognition + lookup pipeline. */
data class LookupOutcome(
    val suggestedCategory: ItemCategory?,
    val suggestedPlatform: String?,
    val titleGuess: String?,
    val barcode: String?,
    val candidates: List<LookupResult>,
    /** Providers that failed, so the UI can tell the user why results may be missing. */
    val errors: List<String> = emptyList(),
)

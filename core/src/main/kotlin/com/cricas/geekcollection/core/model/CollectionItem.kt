package com.cricas.geekcollection.core.model

/**
 * An item stored in the geek library.
 */
data class CollectionItem(
    val id: Long = 0L,
    val title: String,
    val category: ItemCategory,
    /** Gaming platform, only meaningful when [ItemCategory.supportsPlatform]. */
    val platform: String? = null,
    /** 0..100, only meaningful when [ItemCategory.supportsCompletion]. */
    val completionPercent: Int? = null,
    val description: String? = null,
    /** Author, developer, designer or manufacturer depending on the category. */
    val creator: String? = null,
    val publisher: String? = null,
    val releaseYear: Int? = null,
    val barcode: String? = null,
    /** Remote cover URL found online. */
    val coverUrl: String? = null,
    /** Local picture taken or picked by the user. */
    val localImagePath: String? = null,
    val notes: String? = null,
    val favorite: Boolean = false,
    /** Which online source filled the data (e.g. "openlibrary", "bgg"). */
    val externalSource: String? = null,
    val externalId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val completionOrZero: Int get() = completionPercent?.coerceIn(0, 100) ?: 0

    /** Removes values that do not make sense for the chosen category. */
    fun normalized(): CollectionItem = copy(
        title = title.trim(),
        platform = if (category.supportsPlatform) platform?.trim()?.takeIf { it.isNotEmpty() } else null,
        completionPercent = if (category.supportsCompletion) completionPercent?.coerceIn(0, 100) else null,
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        creator = creator?.trim()?.takeIf { it.isNotEmpty() },
        publisher = publisher?.trim()?.takeIf { it.isNotEmpty() },
        barcode = barcode?.trim()?.takeIf { it.isNotEmpty() },
        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
    )
}

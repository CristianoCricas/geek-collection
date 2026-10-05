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
    /** Only meaningful when [ItemCategory.supportsCompletion]. */
    val progressStatus: ProgressStatus = ProgressStatus.IN_PROGRESS,
    /** All trophies/achievements earned; implies 100% and [ProgressStatus.FINISHED]. Video games only. */
    val platinum: Boolean = false,
    /** Not started yet. Games (video and board) only. */
    val backlog: Boolean = false,
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
    /** Stable id shared across devices (cloud sync). */
    val syncId: String = newSyncId(),
    /** True when the item changed locally since the last successful push. */
    val dirty: Boolean = true,
    /** Tombstone date: the item was deleted and the deletion still has to reach the cloud. */
    val deletedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null

    val completionOrZero: Int get() = completionPercent?.coerceIn(0, 100) ?: 0

    /** Removes values that do not make sense for the chosen category. */
    fun normalized(): CollectionItem = copy(
        title = title.trim(),
        platform = if (category.supportsPlatform) platform?.trim()?.takeIf { it.isNotEmpty() } else null,
        completionPercent = when {
            !category.supportsCompletion -> null
            category.supportsPlatinum && platinum -> 100
            else -> (completionPercent ?: 0).coerceIn(0, 100)
        },
        progressStatus = when {
            !category.supportsCompletion -> ProgressStatus.IN_PROGRESS
            category.supportsPlatinum && platinum -> ProgressStatus.FINISHED
            else -> progressStatus
        },
        platinum = category.supportsPlatinum && platinum,
        backlog = category.supportsBacklog && backlog,
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        creator = creator?.trim()?.takeIf { it.isNotEmpty() },
        publisher = publisher?.trim()?.takeIf { it.isNotEmpty() },
        barcode = barcode?.trim()?.takeIf { it.isNotEmpty() },
        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
    )
}

fun newSyncId(): String = java.util.UUID.randomUUID().toString().replace("-", "")

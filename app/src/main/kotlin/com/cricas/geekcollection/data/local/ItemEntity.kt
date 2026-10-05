package com.cricas.geekcollection.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.core.model.ProgressStatus

@Entity(
    tableName = "items",
    indices = [Index("category"), Index("title"), Index("updatedAt")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val category: String,
    val platform: String? = null,
    val completionPercent: Int? = null,
    @ColumnInfo(defaultValue = "IN_PROGRESS") val progressStatus: String = ProgressStatus.IN_PROGRESS.name,
    @ColumnInfo(defaultValue = "0") val platinum: Boolean = false,
    @ColumnInfo(defaultValue = "0") val backlog: Boolean = false,
    val description: String? = null,
    val creator: String? = null,
    val publisher: String? = null,
    val releaseYear: Int? = null,
    val barcode: String? = null,
    val coverUrl: String? = null,
    val localImagePath: String? = null,
    val notes: String? = null,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    val externalSource: String? = null,
    val externalId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toDomain(): CollectionItem = CollectionItem(
        id = id,
        title = title,
        category = ItemCategory.fromName(category),
        platform = platform,
        completionPercent = completionPercent,
        progressStatus = ProgressStatus.fromName(progressStatus),
        platinum = platinum,
        backlog = backlog,
        description = description,
        creator = creator,
        publisher = publisher,
        releaseYear = releaseYear,
        barcode = barcode,
        coverUrl = coverUrl,
        localImagePath = localImagePath,
        notes = notes,
        favorite = favorite,
        externalSource = externalSource,
        externalId = externalId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(item: CollectionItem): ItemEntity = ItemEntity(
            id = item.id,
            title = item.title,
            category = item.category.name,
            platform = item.platform,
            completionPercent = item.completionPercent,
            progressStatus = item.progressStatus.name,
            platinum = item.platinum,
            backlog = item.backlog,
            description = item.description,
            creator = item.creator,
            publisher = item.publisher,
            releaseYear = item.releaseYear,
            barcode = item.barcode,
            coverUrl = item.coverUrl,
            localImagePath = item.localImagePath,
            notes = item.notes,
            favorite = item.favorite,
            externalSource = item.externalSource,
            externalId = item.externalId,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
        )
    }
}

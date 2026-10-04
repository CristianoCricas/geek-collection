package com.cricas.geekcollection.core.lookup

import com.cricas.geekcollection.core.model.ItemCategory

interface LookupProvider {
    val id: String

    /** Categories this provider knows about. Empty means "anything". */
    val categories: Set<ItemCategory>

    suspend fun searchByTitle(title: String, limit: Int = 5): List<LookupResult>

    /** Barcode lookup, when the provider supports it. */
    suspend fun searchByBarcode(barcode: String): List<LookupResult> = emptyList()
}

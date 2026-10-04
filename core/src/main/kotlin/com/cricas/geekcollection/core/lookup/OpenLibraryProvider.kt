package com.cricas.geekcollection.core.lookup

import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.core.recognition.BarcodeClassifier

/** Books, via the free Open Library API (no key required). */
class OpenLibraryProvider(private val http: HttpFetcher) : LookupProvider {

    override val id: String = "openlibrary"
    override val categories: Set<ItemCategory> = setOf(ItemCategory.BOOK, ItemCategory.COMIC)

    override suspend fun searchByTitle(title: String, limit: Int): List<LookupResult> {
        val url = "https://openlibrary.org/search.json?title=${title.urlEncode()}&limit=$limit" +
            "&fields=key,title,author_name,first_publish_year,cover_i,publisher,isbn"
        val json = parseJson(http.get(url))
        return json["docs"].arr.orEmpty().mapNotNull { doc ->
            val name = doc["title"].str ?: return@mapNotNull null
            val coverId = doc["cover_i"].int
            LookupResult(
                title = name,
                category = ItemCategory.BOOK,
                source = id,
                externalId = doc["key"].str,
                creator = doc["author_name"].arr?.mapNotNull { it.str }?.joinToString(", ")?.takeIf { it.isNotBlank() },
                publisher = doc["publisher"][0].str,
                releaseYear = doc["first_publish_year"].int,
                coverUrl = coverId?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" },
                barcode = doc["isbn"].arr?.mapNotNull { it.str }?.firstOrNull { it.length == 13 },
            )
        }
    }

    override suspend fun searchByBarcode(barcode: String): List<LookupResult> {
        val isbn = BarcodeClassifier.toIsbn13(barcode) ?: return emptyList()
        val url = "https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data"
        val json = parseJson(http.get(url))
        val book = json["ISBN:$isbn"].obj ?: return emptyList()
        val name = book["title"].str ?: return emptyList()
        val subtitle = book["subtitle"].str
        return listOf(
            LookupResult(
                title = if (subtitle != null) "$name: $subtitle" else name,
                category = ItemCategory.BOOK,
                source = id,
                externalId = book["key"].str,
                creator = book["authors"].arr?.mapNotNull { it["name"].str }?.joinToString(", ")?.takeIf { it.isNotBlank() },
                publisher = book["publishers"][0]["name"].str,
                releaseYear = book["publish_date"].str.extractYear(),
                coverUrl = book["cover"]["large"].str ?: book["cover"]["medium"].str,
                description = book["notes"].str ?: book["excerpts"][0]["text"].str,
                barcode = isbn,
            )
        )
    }
}

package com.cricas.geekcollection.core.lookup

import com.cricas.geekcollection.core.model.ItemCategory

/**
 * Generic product barcode lookup using the free (rate limited) UPCitemdb
 * trial endpoint. Good for games, consoles, accessories and toys.
 */
class UpcItemDbProvider(private val http: HttpFetcher) : LookupProvider {

    override val id: String = "upcitemdb"
    override val categories: Set<ItemCategory> = emptySet()

    override suspend fun searchByTitle(title: String, limit: Int): List<LookupResult> = emptyList()

    override suspend fun searchByBarcode(barcode: String): List<LookupResult> {
        val digits = barcode.filter { it.isDigit() }
        if (digits.length !in 8..14) return emptyList()
        val body = http.get(
            "https://api.upcitemdb.com/prod/trial/lookup?upc=$digits",
            mapOf("Accept" to "application/json"),
        )
        return parse(body, digits)
    }

    internal fun parse(body: String, barcode: String): List<LookupResult> {
        val json = parseJson(body)
        return json["items"].arr.orEmpty().mapNotNull { item ->
            val name = item["title"].str ?: return@mapNotNull null
            val categoryPath = item["category"].str?.lowercase().orEmpty()
            LookupResult(
                title = name,
                category = guessCategory(categoryPath, name),
                source = id,
                externalId = item["ean"].str ?: item["upc"].str,
                description = item["description"].str,
                creator = item["brand"].str,
                coverUrl = item["images"][0].str,
                barcode = barcode,
            )
        }
    }

    internal fun guessCategory(categoryPath: String, title: String): ItemCategory? {
        val text = "$categoryPath ${title.lowercase()}"
        return when {
            "board game" in text || "tabletop" in text || "jogo de tabuleiro" in text -> ItemCategory.BOARD_GAME
            "book" in text || "media > books" in text -> ItemCategory.BOOK
            "comic" in text || "manga" in text -> ItemCategory.COMIC
            "action figure" in text || "figure" in text || "toys > dolls" in text -> ItemCategory.ACTION_FIGURE
            "console" in text -> ItemCategory.CONSOLE
            "controller" in text || "accessor" in text || "headset" in text || "cable" in text -> ItemCategory.ACCESSORY
            "video game" in text || "games" in text || "playstation" in text || "xbox" in text || "nintendo" in text -> ItemCategory.VIDEO_GAME
            "collectible" in text || "toys" in text -> ItemCategory.COLLECTIBLE
            else -> null
        }
    }
}

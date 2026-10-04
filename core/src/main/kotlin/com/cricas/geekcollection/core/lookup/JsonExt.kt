package com.cricas.geekcollection.core.lookup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseJson(text: String): JsonElement = lenientJson.parseToJsonElement(text)

internal val JsonElement?.obj: JsonObject? get() = this as? JsonObject
internal val JsonElement?.arr: JsonArray? get() = this as? JsonArray
internal val JsonElement?.str: String?
    get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
internal val JsonElement?.int: Int?
    get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull

internal operator fun JsonElement?.get(key: String): JsonElement? = this.obj?.get(key)
internal operator fun JsonElement?.get(index: Int): JsonElement? = this.arr?.getOrNull(index)

/** Extracts a four digit year from strings such as "2017-03-03", "March 2017" or "2017". */
internal fun String?.extractYear(): Int? =
    this?.let { Regex("(1[89]\\d{2}|20\\d{2})").find(it)?.value?.toIntOrNull() }

/** Strips HTML tags and collapses whitespace in descriptions. */
internal fun String.stripHtml(): String =
    replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&amp;", "&")
        .replace("&nbsp;", " ")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

package com.cricas.geekcollection.core.sync

import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.core.model.ProgressStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A document as stored in Firestore, decoded. */
data class RemoteItem(
    val item: CollectionItem,
    /** Base64 JPEG data URL carried along with the item, if any. */
    val thumb: String?,
    /** Server timestamp (RFC 3339) used as the incremental pull cursor. */
    val serverUpdatedAt: String?,
)

/**
 * Converts items to/from the Firestore REST "typed value" JSON representation.
 * Field names are shared with the PWA so both clients read the same documents.
 */
object FirestoreCodec {

    fun value(v: Any?): JsonElement = when (v) {
        null -> buildJsonObject { put("nullValue", JsonNull) }
        is Boolean -> buildJsonObject { put("booleanValue", v) }
        is Int -> buildJsonObject { put("integerValue", v.toString()) }
        is Long -> buildJsonObject { put("integerValue", v.toString()) }
        is Double -> buildJsonObject { put("doubleValue", v) }
        is Float -> buildJsonObject { put("doubleValue", v.toDouble()) }
        else -> buildJsonObject { put("stringValue", v.toString()) }
    }

    fun decode(v: JsonElement?): Any? {
        val o = v as? JsonObject ?: return null
        o["stringValue"]?.let { return it.jsonPrimitive.contentOrNull }
        o["integerValue"]?.let { return it.jsonPrimitive.contentOrNull?.toLongOrNull() }
        o["doubleValue"]?.let { return it.jsonPrimitive.doubleOrNull }
        o["booleanValue"]?.let { return it.jsonPrimitive.booleanOrNull }
        o["timestampValue"]?.let { return it.jsonPrimitive.contentOrNull }
        o["arrayValue"]?.let { arr -> return (arr.jsonObject["values"] as? JsonArray)?.map { decode(it) } ?: emptyList<Any?>() }
        o["mapValue"]?.let { m -> return (m.jsonObject["fields"] as? JsonObject)?.mapValues { decode(it.value) } ?: emptyMap<String, Any?>() }
        return null
    }

    /** Firestore `fields` object for an item (plus optional thumbnail). */
    fun toFields(item: CollectionItem, thumb: String?): JsonObject = buildJsonObject {
        put("syncId", value(item.syncId))
        put("title", value(item.title))
        put("category", value(item.category.name))
        put("platform", value(item.platform))
        put("completionPercent", value(item.completionPercent))
        put("progressStatus", value(item.progressStatus.name))
        put("platinum", value(item.platinum))
        put("backlog", value(item.backlog))
        put("description", value(item.description))
        put("creator", value(item.creator))
        put("publisher", value(item.publisher))
        put("releaseYear", value(item.releaseYear))
        put("barcode", value(item.barcode))
        put("coverUrl", value(item.coverUrl))
        put("notes", value(item.notes))
        put("favorite", value(item.favorite))
        put("sourceUrl", value(null))
        put("sourceName", value(item.externalSource))
        put("externalId", value(item.externalId))
        put("createdAt", value(item.createdAt))
        put("updatedAt", value(item.updatedAt))
        put("deletedAt", value(item.deletedAt))
        put("deleted", value(item.deletedAt != null))
        put("thumb", value(thumb))
        put("client", value("android"))
    }

    /** Decodes a `document` object ({name, fields, updateTime}) into a [RemoteItem]. */
    fun fromDocument(document: JsonObject): RemoteItem? {
        val fields = document["fields"] as? JsonObject ?: return null
        fun str(k: String) = decode(fields[k]) as? String
        fun long(k: String) = decode(fields[k]) as? Long
        fun bool(k: String) = (decode(fields[k]) as? Boolean) ?: false
        val syncId = str("syncId") ?: return null
        val deleted = bool("deleted")
        val updatedAt = long("updatedAt") ?: 0L
        val item = CollectionItem(
            title = str("title") ?: "",
            category = ItemCategory.fromName(str("category")),
            platform = str("platform"),
            completionPercent = long("completionPercent")?.toInt(),
            progressStatus = ProgressStatus.fromName(str("progressStatus")),
            platinum = bool("platinum"),
            backlog = bool("backlog"),
            description = str("description"),
            creator = str("creator"),
            publisher = str("publisher"),
            releaseYear = long("releaseYear")?.toInt(),
            barcode = str("barcode"),
            coverUrl = str("coverUrl"),
            notes = str("notes"),
            favorite = bool("favorite"),
            externalSource = str("sourceName"),
            externalId = str("externalId"),
            createdAt = long("createdAt") ?: updatedAt,
            updatedAt = updatedAt,
            syncId = syncId,
            dirty = false,
            deletedAt = if (deleted) (long("deletedAt") ?: updatedAt) else null,
        )
        return RemoteItem(
            item = item,
            thumb = str("thumb"),
            serverUpdatedAt = (decode(fields["serverUpdatedAt"]) as? String)
                ?: (document["updateTime"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    /** Parses the array returned by `:runQuery`. */
    fun parseQueryResponse(body: JsonElement): List<RemoteItem> =
        (body as? JsonArray).orEmpty().mapNotNull { row ->
            (row as? JsonObject)?.get("document")?.let { fromDocument(it.jsonObject) }
        }

    /** Body of a `:commit` request upserting the given entries with a server timestamp. */
    fun commitBody(entries: List<Pair<CollectionItem, String?>>, docNameOf: (String) -> String): JsonObject = buildJsonObject {
        put("writes", JsonArray(entries.map { (item, thumb) ->
            buildJsonObject {
                put("update", buildJsonObject {
                    put("name", docNameOf(item.syncId))
                    put("fields", toFields(item, thumb))
                })
                put("updateTransforms", JsonArray(listOf(buildJsonObject {
                    put("fieldPath", "serverUpdatedAt")
                    put("setToServerValue", "REQUEST_TIME")
                })))
            }
        }))
    }

    /** Body of a `:runQuery` request for documents changed after [cursor]. */
    fun queryBody(cursor: String?, limit: Int): JsonObject = buildJsonObject {
        put("structuredQuery", buildJsonObject {
            put("from", JsonArray(listOf(buildJsonObject { put("collectionId", "items") })))
            if (cursor != null) {
                put("where", buildJsonObject {
                    put("fieldFilter", buildJsonObject {
                        put("field", buildJsonObject { put("fieldPath", "serverUpdatedAt") })
                        put("op", "GREATER_THAN")
                        put("value", buildJsonObject { put("timestampValue", cursor) })
                    })
                })
            }
            put("orderBy", JsonArray(listOf(buildJsonObject {
                put("field", buildJsonObject { put("fieldPath", "serverUpdatedAt") })
                put("direction", "ASCENDING")
            })))
            put("limit", limit)
        })
    }
}

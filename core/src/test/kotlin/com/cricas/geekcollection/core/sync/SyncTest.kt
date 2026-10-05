package com.cricas.geekcollection.core.sync

import com.cricas.geekcollection.core.lookup.HttpException
import com.cricas.geekcollection.core.lookup.HttpFetcher
import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ItemCategory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryAuth(var value: FirebaseAuth? = null) : AuthStore {
    override fun load() = value
    override fun save(auth: FirebaseAuth?) { value = auth }
}

/** Fake Firebase backend: auth endpoints + an in-memory document store. */
private class FakeBackend : HttpFetcher {
    val calls = mutableListOf<Triple<String, String, Map<String, String>>>()
    val stored = linkedMapOf<String, JsonObject>()
    var serverStamp = "2026-02-02T00:00:00Z"
    override suspend fun get(url: String, headers: Map<String, String>): String = error("no GET expected")
    override suspend fun post(url: String, body: String, contentType: String, headers: Map<String, String>, method: String): String {
        calls += Triple(url, body, headers)
        return when {
            url.contains("accounts:signInWithPassword") -> """{"localId":"uid1","email":"a@b.c","idToken":"tok","refreshToken":"ref","expiresIn":"3600"}"""
            url.contains("accounts:signUp") -> throw HttpException(400, "EMAIL_EXISTS")
            url.contains("accounts:sendOobCode") -> if (body.contains("nobody@")) throw HttpException(400, "EMAIL_NOT_FOUND") else "{}"
            url.contains("securetoken") -> """{"id_token":"tok2","refresh_token":"ref","user_id":"uid1","expires_in":"3600"}"""
            url.endsWith(":commit") -> {
                val writes = Json.parseToJsonElement(body).jsonObject["writes"]!!.jsonArray
                writes.forEach { w ->
                    val update = w.jsonObject["update"]!!.jsonObject
                    val name = update["name"]!!.jsonPrimitive.content
                    val fields = buildJsonObject {
                        update["fields"]!!.jsonObject.forEach { (k, v) -> put(k, v) }
                        put("serverUpdatedAt", buildJsonObject { put("timestampValue", serverStamp) })
                    }
                    stored[name] = buildJsonObject { put("name", name); put("fields", fields) }
                }
                """{"writeResults":[]}"""
            }
            url.endsWith(":runQuery") -> JsonArray(stored.values.map { doc -> buildJsonObject { put("document", doc) } }).toString()
            else -> error("unexpected $url")
        }
    }
}

private class MemoryStore(initial: List<CollectionItem>) : SyncStore {
    val items = initial.associateBy { it.syncId }.toMutableMap()
    val thumbs = mutableMapOf<String, String?>()
    var cursor: String? = null
    override suspend fun dirtyItems() = items.values.filter { it.dirty }
    override suspend fun bySyncId(syncId: String) = items[syncId]
    override suspend fun applyRemote(item: CollectionItem, thumb: String?) { items[item.syncId] = item.copy(dirty = false); thumbs[item.syncId] = thumb }
    override suspend fun removeLocal(syncId: String) { items.remove(syncId) }
    override suspend fun markClean(syncIds: List<String>) { syncIds.forEach { id -> items[id]?.let { items[id] = it.copy(dirty = false) } } }
    override suspend fun thumbFor(item: CollectionItem) = if (item.localImagePath != null) "thumb-${item.syncId}" else null
    override suspend fun loadCursor() = cursor
    override suspend fun saveCursor(cursor: String) { this.cursor = cursor }
}

class SyncTest {

    private fun client(backend: FakeBackend, auth: MemoryAuth, now: Long = 1_000_000L) = FirebaseClient(
        backend, { FirebaseConfig("proj", "key") }, auth, clock = { now },
    )

    @Test
    fun `codec round trips an item`() {
        val item = CollectionItem(title = "Zelda", category = ItemCategory.VIDEO_GAME, platform = "Nintendo Switch", completionPercent = 40, syncId = "abc", createdAt = 1000, updatedAt = 2000, externalSource = "google")
        val fields = FirestoreCodec.toFields(item, "data:image/jpeg;base64,xx")
        assertEquals("Zelda", fields["title"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content)
        assertEquals("40", fields["completionPercent"]!!.jsonObject["integerValue"]!!.jsonPrimitive.content)
        assertEquals("false", fields["deleted"]!!.jsonObject["booleanValue"]!!.jsonPrimitive.content)
        val doc = buildJsonObject {
            put("name", "x")
            put("fields", buildJsonObject {
                fields.forEach { (k, v) -> put(k, v) }
                put("serverUpdatedAt", buildJsonObject { put("timestampValue", "2026-01-01T00:00:00Z") })
            })
        }
        val remote = FirestoreCodec.fromDocument(doc)!!
        assertEquals("Zelda", remote.item.title)
        assertEquals("Nintendo Switch", remote.item.platform)
        assertEquals(40, remote.item.completionPercent)
        assertEquals(2000L, remote.item.updatedAt)
        assertEquals("google", remote.item.externalSource)
        assertFalse(remote.item.dirty)
        assertNull(remote.item.deletedAt)
        assertEquals("data:image/jpeg;base64,xx", remote.thumb)
        assertEquals("2026-01-01T00:00:00Z", remote.serverUpdatedAt)

        val dead = FirestoreCodec.fromDocument(buildJsonObject {
            put("fields", FirestoreCodec.toFields(item.copy(deletedAt = 5L), null))
        })!!
        assertEquals(5L, dead.item.deletedAt)
        assertTrue(dead.item.isDeleted)
    }

    @Test
    fun `query body filters on the cursor only when present`() {
        assertNull(FirestoreCodec.queryBody(null, 10)["structuredQuery"]!!.jsonObject["where"])
        val where = FirestoreCodec.queryBody("2026-01-01T00:00:00Z", 10)["structuredQuery"]!!.jsonObject["where"]!!.jsonObject
        assertEquals("2026-01-01T00:00:00Z", where["fieldFilter"]!!.jsonObject["value"]!!.jsonObject["timestampValue"]!!.jsonPrimitive.content)
    }

    @Test
    fun `sign in, refresh and friendly errors`() = runTest {
        val backend = FakeBackend()
        val auth = MemoryAuth()
        val client = client(backend, auth)
        assertFalse(client.isSignedIn)
        val session = client.signIn("a@b.c", "secret")
        assertEquals("uid1", session.uid)
        assertTrue(client.isSignedIn)
        auth.value = session.copy(expiresAt = 0L)
        assertEquals("tok2", client.ensureToken())
        assertTrue(backend.calls[1].second.contains("grant_type=refresh_token&refresh_token=ref"))
        val error = runCatching { client.signUp("a@b.c", "secret") }.exceptionOrNull()
        assertEquals("Este e-mail já está cadastrado. Faça login.", error?.message)
    }

    @Test
    fun `engine pulls before pushing and resolves conflicts by updatedAt`() = runTest {
        val backend = FakeBackend()
        val auth = MemoryAuth()
        val client = client(backend, auth)
        client.signIn("a@b.c", "secret")
        fun remoteDoc(item: CollectionItem, stamp: String) = buildJsonObject {
            put("name", client.docName("uid1", item.syncId))
            put("fields", buildJsonObject {
                FirestoreCodec.toFields(item, null).forEach { (k, v) -> put(k, v) }
                put("serverUpdatedAt", buildJsonObject { put("timestampValue", stamp) })
            })
        }
        backend.stored["r1"] = remoteDoc(CollectionItem(title = "Remote newer", category = ItemCategory.BOOK, syncId = "r1", updatedAt = 5000, createdAt = 1), "2026-01-05T00:00:00Z")
        backend.stored["r2"] = remoteDoc(CollectionItem(title = "Remote older", category = ItemCategory.BOOK, syncId = "r2", updatedAt = 10, createdAt = 1), "2026-01-03T00:00:00Z")
        backend.stored["r3"] = remoteDoc(CollectionItem(title = "Gone", category = ItemCategory.BOOK, syncId = "r3", updatedAt = 9000, createdAt = 1, deletedAt = 9000), "2026-01-04T00:00:00Z")

        val store = MemoryStore(listOf(
            CollectionItem(title = "Local dirty", category = ItemCategory.VIDEO_GAME, syncId = "l1", updatedAt = 100, dirty = true, localImagePath = "/img"),
            CollectionItem(title = "Local clean", category = ItemCategory.VIDEO_GAME, syncId = "l2", updatedAt = 100, dirty = false),
            CollectionItem(title = "Local stale", category = ItemCategory.BOOK, syncId = "r1", updatedAt = 1000, dirty = true),
            CollectionItem(title = "Local newer dirty", category = ItemCategory.BOOK, syncId = "r2", updatedAt = 20, dirty = true),
            CollectionItem(title = "To be removed", category = ItemCategory.BOOK, syncId = "r3", updatedAt = 1, dirty = false),
        ))
        val statuses = mutableListOf<String>()
        val summary = SyncEngine(client, store).sync { statuses += it }

        assertEquals("Remote newer", store.items["r1"]!!.title)
        assertEquals("Local newer dirty", store.items["r2"]!!.title)
        assertFalse(store.items.containsKey("r3"))
        assertEquals(1, summary.removed)
        assertEquals(1, summary.pulled)
        assertEquals(2, summary.pushed)
        val commit = backend.calls.first { it.first.endsWith(":commit") }
        val names = Json.parseToJsonElement(commit.second).jsonObject["writes"]!!.jsonArray.map {
            it.jsonObject["update"]!!.jsonObject["name"]!!.jsonPrimitive.content.substringAfterLast('/')
        }
        assertEquals(listOf("l1", "r2"), names.sorted())
        assertEquals("Bearer tok", commit.third["Authorization"])
        assertTrue(commit.second.contains("\"setToServerValue\":\"REQUEST_TIME\""))
        assertTrue(commit.second.contains("thumb-l1"))
        assertFalse(store.items["l1"]!!.dirty)
        assertEquals("2026-01-05T00:00:00Z", store.cursor)
        assertTrue(statuses.any { it.startsWith("Enviando") })
    }

    @Test
    fun `password reset posts to sendOobCode and translates errors`() = runTest {
        val backend = FakeBackend()
        val client = client(backend, MemoryAuth())
        client.sendPasswordReset("a@b.c")
        val call = backend.calls.last()
        assertTrue(call.first.contains("accounts:sendOobCode?key=key"))
        assertEquals("""{"requestType":"PASSWORD_RESET","email":"a@b.c"}""", call.second)
        val error = runCatching { client.sendPasswordReset("nobody@x.y") }.exceptionOrNull()
        assertEquals("E-mail não encontrado. Crie uma conta.", error?.message)
    }

    @Test
    fun `engine requires a session`() = runTest {
        val client = client(FakeBackend(), MemoryAuth())
        val error = runCatching { SyncEngine(client, MemoryStore(emptyList())).sync() }.exceptionOrNull()
        assertTrue(error is SyncAuthException)
    }
}

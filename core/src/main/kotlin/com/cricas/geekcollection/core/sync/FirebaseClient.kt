package com.cricas.geekcollection.core.sync

import com.cricas.geekcollection.core.lookup.HttpException
import com.cricas.geekcollection.core.lookup.HttpFetcher
import com.cricas.geekcollection.core.model.CollectionItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/** Firebase Auth session persisted by the app. */
data class FirebaseAuth(
    val uid: String,
    val email: String?,
    val idToken: String,
    val refreshToken: String,
    /** Epoch millis after which [idToken] must be refreshed. */
    val expiresAt: Long,
)

data class FirebaseConfig(val projectId: String, val apiKey: String) {
    val isConfigured: Boolean get() = projectId.isNotBlank() && apiKey.isNotBlank()
}

/** Where the app keeps the session (SharedPreferences on Android). */
interface AuthStore {
    fun load(): FirebaseAuth?
    fun save(auth: FirebaseAuth?)
}

class SyncAuthException(message: String) : RuntimeException(message)

/**
 * Firebase Authentication (email/password) and Cloud Firestore via REST.
 * Mirrors web/js/sync/firebase.js so both clients share one data layout.
 */
class FirebaseClient(
    private val http: HttpFetcher,
    private val configProvider: () -> FirebaseConfig,
    private val authStore: AuthStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val config: FirebaseConfig get() = configProvider()
    val isConfigured: Boolean get() = config.isConfigured
    val auth: FirebaseAuth? get() = authStore.load()
    val isSignedIn: Boolean get() = auth?.refreshToken?.isNotBlank() == true

    suspend fun signUp(email: String, password: String): FirebaseAuth = credential("signUp", email, password)
    suspend fun signIn(email: String, password: String): FirebaseAuth = credential("signInWithPassword", email, password)

    fun signOut() = authStore.save(null)

    private suspend fun credential(method: String, email: String, password: String): FirebaseAuth {
        val cfg = config
        if (!cfg.isConfigured) throw SyncAuthException("Configure o projeto Firebase primeiro")
        val body = """{"email":${quote(email)},"password":${quote(password)},"returnSecureToken":true}"""
        val response = try {
            http.post("$AUTH:$method?key=${enc(cfg.apiKey)}", body)
        } catch (e: HttpException) {
            throw SyncAuthException(authErrorMessage(e.message))
        }
        val o = json.parseToJsonElement(response).jsonObject
        val session = FirebaseAuth(
            uid = o.str("localId") ?: throw SyncAuthException("Resposta inválida do Firebase"),
            email = o.str("email") ?: email,
            idToken = o.str("idToken").orEmpty(),
            refreshToken = o.str("refreshToken").orEmpty(),
            expiresAt = clock() + ((o.str("expiresIn")?.toLongOrNull() ?: 3600L) - 60) * 1000,
        )
        authStore.save(session)
        return session
    }

    /** Returns a valid ID token, refreshing it when expired. */
    suspend fun ensureToken(): String {
        val a = auth ?: throw SyncAuthException("Faça login para sincronizar")
        if (a.refreshToken.isBlank()) throw SyncAuthException("Faça login para sincronizar")
        if (a.idToken.isNotBlank() && clock() < a.expiresAt) return a.idToken
        val response = try {
            http.post(
                "$TOKEN?key=${enc(config.apiKey)}",
                "grant_type=refresh_token&refresh_token=${enc(a.refreshToken)}",
                contentType = "application/x-www-form-urlencoded",
            )
        } catch (e: HttpException) {
            if (e.status == 400 || e.status == 401) {
                signOut()
                throw SyncAuthException("Sessão expirada. Faça login novamente.")
            }
            throw e
        }
        val o = json.parseToJsonElement(response).jsonObject
        val refreshed = a.copy(
            uid = o.str("user_id") ?: a.uid,
            idToken = o.str("id_token") ?: a.idToken,
            refreshToken = o.str("refresh_token") ?: a.refreshToken,
            expiresAt = clock() + ((o.str("expires_in")?.toLongOrNull() ?: 3600L) - 60) * 1000,
        )
        authStore.save(refreshed)
        return refreshed.idToken
    }

    private val documentsRoot: String get() = "projects/${config.projectId}/databases/(default)/documents"
    private val baseUrl: String get() = "https://firestore.googleapis.com/v1/$documentsRoot"

    fun docName(uid: String, syncId: String) = "$documentsRoot/users/$uid/items/$syncId"

    /** Upserts the entries in one commit; the server stamps `serverUpdatedAt`. */
    suspend fun commitItems(entries: List<Pair<CollectionItem, String?>>) {
        if (entries.isEmpty()) return
        val token = ensureToken()
        val uid = auth!!.uid
        val body = FirestoreCodec.commitBody(entries) { docName(uid, it) }
        http.post("$baseUrl:commit", body.toString(), headers = mapOf("Authorization" to "Bearer $token"))
    }

    /** Documents changed on the server after [cursor], oldest first. */
    suspend fun changedSince(cursor: String?, limit: Int = 300): List<RemoteItem> {
        val token = ensureToken()
        val uid = auth!!.uid
        val body = FirestoreCodec.queryBody(cursor, limit)
        val response = http.post("$baseUrl/users/$uid:runQuery", body.toString(), headers = mapOf("Authorization" to "Bearer $token"))
        return FirestoreCodec.parseQueryResponse(json.parseToJsonElement(response))
    }

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull

    companion object {
        private const val AUTH = "https://identitytoolkit.googleapis.com/v1/accounts"
        private const val TOKEN = "https://securetoken.googleapis.com/v1/token"

        private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
        private fun quote(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        fun authErrorMessage(raw: String?): String {
            val code = raw.orEmpty().trim().split(" ", ":").firstOrNull().orEmpty()
            return when (code) {
                "EMAIL_EXISTS" -> "Este e-mail já está cadastrado. Faça login."
                "EMAIL_NOT_FOUND" -> "E-mail não encontrado. Crie uma conta."
                "INVALID_PASSWORD" -> "Senha incorreta."
                "INVALID_LOGIN_CREDENTIALS" -> "E-mail ou senha incorretos."
                "WEAK_PASSWORD" -> "A senha precisa ter pelo menos 6 caracteres."
                "INVALID_EMAIL" -> "E-mail inválido."
                "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Muitas tentativas. Tente mais tarde."
                "CONFIGURATION_NOT_FOUND", "OPERATION_NOT_ALLOWED" -> "Ative o login por e-mail/senha no Firebase Authentication."
                "API_KEY_INVALID" -> "Chave da API do Firebase inválida."
                else -> raw?.takeIf { it.isNotBlank() } ?: "Falha na autenticação"
            }
        }
    }
}

package com.cricas.geekcollection.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.cricas.geekcollection.core.sync.AuthStore
import com.cricas.geekcollection.core.sync.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val rawgApiKey: String = "",
    val wikipediaLanguage: String = "pt",
    val firebaseProjectId: String = "",
    val firebaseApiKey: String = "",
    val autoSync: Boolean = true,
)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("geek_collection_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun update(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_RAWG, settings.rawgApiKey.trim())
            .putString(KEY_WIKI_LANG, settings.wikipediaLanguage.trim().ifEmpty { "pt" })
            .putString(KEY_FB_PROJECT, settings.firebaseProjectId.trim())
            .putString(KEY_FB_KEY, settings.firebaseApiKey.trim())
            .putBoolean(KEY_AUTO_SYNC, settings.autoSync)
            .apply()
        _settings.value = load()
    }

    private fun load() = AppSettings(
        rawgApiKey = prefs.getString(KEY_RAWG, "") ?: "",
        wikipediaLanguage = prefs.getString(KEY_WIKI_LANG, "pt") ?: "pt",
        firebaseProjectId = prefs.getString(KEY_FB_PROJECT, "") ?: "",
        firebaseApiKey = prefs.getString(KEY_FB_KEY, "") ?: "",
        autoSync = prefs.getBoolean(KEY_AUTO_SYNC, true),
    )

    // ---- sync state (cursor + last run) ----
    var syncCursor: String?
        get() = prefs.getString(KEY_SYNC_CURSOR, null)
        set(value) { prefs.edit().putString(KEY_SYNC_CURSOR, value).apply() }

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_SYNC, value).apply() }

    /** Firebase session persisted in the same preferences file. */
    val authStore: AuthStore = object : AuthStore {
        override fun load(): FirebaseAuth? {
            val uid = prefs.getString(KEY_AUTH_UID, null) ?: return null
            val refresh = prefs.getString(KEY_AUTH_REFRESH, null) ?: return null
            return FirebaseAuth(
                uid = uid,
                email = prefs.getString(KEY_AUTH_EMAIL, null),
                idToken = prefs.getString(KEY_AUTH_ID_TOKEN, "") ?: "",
                refreshToken = refresh,
                expiresAt = prefs.getLong(KEY_AUTH_EXPIRES, 0L),
            )
        }

        override fun save(auth: FirebaseAuth?) {
            val editor = prefs.edit()
            if (auth == null) {
                editor.remove(KEY_AUTH_UID).remove(KEY_AUTH_EMAIL).remove(KEY_AUTH_ID_TOKEN).remove(KEY_AUTH_REFRESH).remove(KEY_AUTH_EXPIRES)
            } else {
                editor.putString(KEY_AUTH_UID, auth.uid)
                    .putString(KEY_AUTH_EMAIL, auth.email)
                    .putString(KEY_AUTH_ID_TOKEN, auth.idToken)
                    .putString(KEY_AUTH_REFRESH, auth.refreshToken)
                    .putLong(KEY_AUTH_EXPIRES, auth.expiresAt)
            }
            editor.apply()
        }
    }

    private companion object {
        const val KEY_RAWG = "rawg_api_key"
        const val KEY_WIKI_LANG = "wikipedia_language"
        const val KEY_FB_PROJECT = "firebase_project_id"
        const val KEY_FB_KEY = "firebase_api_key"
        const val KEY_AUTO_SYNC = "auto_sync"
        const val KEY_SYNC_CURSOR = "sync_cursor"
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_AUTH_UID = "auth_uid"
        const val KEY_AUTH_EMAIL = "auth_email"
        const val KEY_AUTH_ID_TOKEN = "auth_id_token"
        const val KEY_AUTH_REFRESH = "auth_refresh_token"
        const val KEY_AUTH_EXPIRES = "auth_expires_at"
    }
}

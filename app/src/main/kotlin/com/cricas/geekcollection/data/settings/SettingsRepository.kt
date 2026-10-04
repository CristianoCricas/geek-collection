package com.cricas.geekcollection.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val rawgApiKey: String = "",
    val wikipediaLanguage: String = "pt",
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
            .apply()
        _settings.value = load()
    }

    private fun load() = AppSettings(
        rawgApiKey = prefs.getString(KEY_RAWG, "") ?: "",
        wikipediaLanguage = prefs.getString(KEY_WIKI_LANG, "pt") ?: "pt",
    )

    private companion object {
        const val KEY_RAWG = "rawg_api_key"
        const val KEY_WIKI_LANG = "wikipedia_language"
    }
}

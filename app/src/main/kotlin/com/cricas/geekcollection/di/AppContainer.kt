package com.cricas.geekcollection.di

import android.content.Context
import com.cricas.geekcollection.core.lookup.BoardGameGeekProvider
import com.cricas.geekcollection.core.lookup.LookupResult
import com.cricas.geekcollection.core.lookup.LookupService
import com.cricas.geekcollection.core.lookup.OpenLibraryProvider
import com.cricas.geekcollection.core.lookup.RawgProvider
import com.cricas.geekcollection.core.lookup.UpcItemDbProvider
import com.cricas.geekcollection.core.lookup.WikipediaProvider
import com.cricas.geekcollection.core.model.CollectionItem
import com.cricas.geekcollection.core.model.ItemCategory
import com.cricas.geekcollection.data.ImageStore
import com.cricas.geekcollection.data.ItemRepository
import com.cricas.geekcollection.data.local.AppDatabase
import com.cricas.geekcollection.data.remote.OkHttpFetcher
import com.cricas.geekcollection.data.settings.SettingsRepository
import com.cricas.geekcollection.core.sync.FirebaseClient
import com.cricas.geekcollection.core.sync.FirebaseConfig
import com.cricas.geekcollection.core.sync.SyncEngine
import com.cricas.geekcollection.recognition.MlKitImageRecognizer
import com.cricas.geekcollection.sync.RoomSyncStore
import com.cricas.geekcollection.sync.SyncManager

/**
 * Hand-rolled dependency container. Small enough that a DI framework would
 * add more ceremony than value.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val imageStore: ImageStore by lazy { ImageStore(appContext) }
    private val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val repository: ItemRepository by lazy {
        ItemRepository(database.itemDao(), imageStore).also { repo ->
            repo.onLocalChange = { syncManager.scheduleAfterChange() }
        }
    }

    val syncManager: SyncManager by lazy {
        val firebase = FirebaseClient(
            http = httpFetcher,
            configProvider = { settings.current.let { FirebaseConfig(it.firebaseProjectId, it.firebaseApiKey) } },
            authStore = settings.authStore,
        )
        SyncManager(firebase, SyncEngine(firebase, RoomSyncStore(database.itemDao(), imageStore, settings)), settings)
    }
    val recognizer: MlKitImageRecognizer by lazy { MlKitImageRecognizer(appContext) }

    val rawgProvider: RawgProvider by lazy {
        RawgProvider(httpFetcher, apiKeyProvider = { settings.current.rawgApiKey.takeIf { it.isNotBlank() } })
    }

    private val httpFetcher by lazy { OkHttpFetcher() }

    val lookupService: LookupService by lazy {
        LookupService(
            listOf(
                OpenLibraryProvider(httpFetcher),
                BoardGameGeekProvider(httpFetcher),
                rawgProvider,
                WikipediaProvider(httpFetcher, language = { settings.current.wikipediaLanguage }),
                UpcItemDbProvider(httpFetcher),
            )
        )
    }

    /** Hands a pre-filled item from the scan flow to the edit screen. */
    val draftHolder = DraftHolder()
}

class DraftHolder {
    private var draft: CollectionItem? = null

    fun put(result: LookupResult, fallback: CollectionItem?) {
        val base = result.toItem(fallback?.category ?: ItemCategory.OTHER)
        draft = base.copy(
            platform = base.platform ?: fallback?.platform,
            barcode = base.barcode ?: fallback?.barcode,
            localImagePath = fallback?.localImagePath,
            completionPercent = if (base.category.supportsCompletion) 0 else null,
        ).normalized()
    }

    fun put(item: CollectionItem) {
        draft = item
    }

    fun consume(): CollectionItem? = draft.also { draft = null }
}

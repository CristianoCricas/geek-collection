package com.cricas.geekcollection

import android.app.Application
import com.cricas.geekcollection.di.AppContainer

class GeekCollectionApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

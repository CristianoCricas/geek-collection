package com.cricas.geekcollection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cricas.geekcollection.ui.navigation.GeekCollectionNavHost
import com.cricas.geekcollection.ui.theme.GeekCollectionTheme

class MainActivity : ComponentActivity() {
    private lateinit var syncManager: com.cricas.geekcollection.sync.SyncManager

    override fun onResume() {
        super.onResume()
        syncManager.autoSync()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as GeekCollectionApp).container
        syncManager = container.syncManager
        setContent {
            GeekCollectionTheme {
                GeekCollectionNavHost(container = container)
            }
        }
    }
}

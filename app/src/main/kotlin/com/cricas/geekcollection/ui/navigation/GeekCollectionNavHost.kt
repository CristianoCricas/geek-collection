package com.cricas.geekcollection.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cricas.geekcollection.di.AppContainer
import com.cricas.geekcollection.ui.detail.ItemDetailScreen
import com.cricas.geekcollection.ui.edit.EditItemScreen
import com.cricas.geekcollection.ui.library.LibraryScreen
import com.cricas.geekcollection.ui.scan.ScanScreen
import com.cricas.geekcollection.ui.settings.SettingsScreen

object Routes {
    const val LIBRARY = "library"
    const val DETAIL = "item/{itemId}"
    const val EDIT = "edit?itemId={itemId}&fromDraft={fromDraft}"
    const val SCAN = "scan"
    const val SETTINGS = "settings"

    fun detail(itemId: Long) = "item/$itemId"
    fun edit(itemId: Long? = null, fromDraft: Boolean = false) =
        "edit?itemId=${itemId ?: 0L}&fromDraft=$fromDraft"
}

@Composable
fun GeekCollectionNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                container = container,
                onOpenItem = { navController.navigate(Routes.detail(it)) },
                onAddManually = { navController.navigate(Routes.edit()) },
                onAddByPhoto = { navController.navigate(Routes.SCAN) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
        ) { entry ->
            val itemId = entry.arguments?.getLong("itemId") ?: 0L
            ItemDetailScreen(
                container = container,
                itemId = itemId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.edit(itemId)) },
            )
        }
        composable(
            route = Routes.EDIT,
            arguments = listOf(
                navArgument("itemId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("fromDraft") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val itemId = entry.arguments?.getLong("itemId") ?: 0L
            val fromDraft = entry.arguments?.getBoolean("fromDraft") ?: false
            EditItemScreen(
                container = container,
                itemId = itemId.takeIf { it != 0L },
                fromDraft = fromDraft,
                onBack = { navController.popBackStack() },
                onSaved = { savedId ->
                    // After saving a new item, replace the edit screen with its detail page.
                    navController.navigate(Routes.detail(savedId)) {
                        popUpTo(Routes.LIBRARY) { inclusive = false }
                    }
                },
            )
        }
        composable(Routes.SCAN) {
            ScanScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onProceedToEdit = {
                    navController.navigate(Routes.edit(fromDraft = true)) {
                        popUpTo(Routes.LIBRARY) { inclusive = false }
                    }
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(container = container, onBack = { navController.popBackStack() })
        }
    }
}

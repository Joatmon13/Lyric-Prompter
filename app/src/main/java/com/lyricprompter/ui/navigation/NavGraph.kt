package com.lyricprompter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lyricprompter.ui.add.AddSongScreen
import com.lyricprompter.ui.library.LibraryScreen
import com.lyricprompter.ui.perform.PerformScreen
import com.lyricprompter.ui.setlist.SetlistDetailScreen
import com.lyricprompter.ui.setlist.SetlistListScreen
import com.lyricprompter.ui.settings.SettingsScreen
import com.lyricprompter.ui.song.SongDetailScreen
import com.lyricprompter.ui.song.SongEditorScreen

/**
 * Navigation routes for the app.
 */
object Routes {
    const val LIBRARY = "library"
    const val SONG_DETAIL = "song/{songId}"
    const val SONG_EDITOR = "song/{songId}/edit"
    const val SONG_EDITOR_NEW = "song/new/edit"
    const val ADD_SONG = "add"
    const val PERFORM = "perform/{songId}"
    const val SETLIST_LIST = "setlists"
    const val SETLIST_DETAIL = "setlist/{setlistId}"
    const val SETTINGS = "settings"

    fun songDetail(songId: String) = "song/$songId"
    fun songEditor(songId: String) = "song/$songId/edit"
    fun perform(songId: String) = "perform/$songId"
    fun setlistDetail(setlistId: String) = "setlist/$setlistId"
}

/**
 * Main navigation host for the app.
 */
@Composable
fun LyricPrompterNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.LIBRARY
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Library (Home) Screen
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onSongClick = { songId ->
                    navController.navigate(Routes.songDetail(songId))
                },
                onAddSongClick = {
                    navController.navigate(Routes.ADD_SONG)
                },
                onSetlistsClick = {
                    navController.navigate(Routes.SETLIST_LIST)
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        // Song Detail Screen
        composable(
            route = Routes.SONG_DETAIL,
            arguments = listOf(
                navArgument("songId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getString("songId") ?: return@composable
            SongDetailScreen(
                songId = songId,
                onBackClick = { navController.popBackStack() },
                onEditClick = { navController.navigate(Routes.songEditor(songId)) },
                onPerformClick = { navController.navigate(Routes.perform(songId)) },
                onDeleted = { navController.popBackStack() }
            )
        }

        // Song Editor Screen (existing song)
        composable(
            route = Routes.SONG_EDITOR,
            arguments = listOf(
                navArgument("songId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getString("songId") ?: return@composable
            SongEditorScreen(
                songId = songId,
                onBackClick = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        // Song Editor Screen (new song)
        composable(Routes.SONG_EDITOR_NEW) {
            SongEditorScreen(
                songId = null,
                onBackClick = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        // Add Song Screen
        composable(Routes.ADD_SONG) {
            AddSongScreen(
                onBackClick = { navController.popBackStack() },
                onSongAdded = { songId ->
                    navController.popBackStack()
                    navController.navigate(Routes.songDetail(songId))
                },
                onManualEntry = {
                    navController.popBackStack()
                    navController.navigate(Routes.SONG_EDITOR_NEW)
                }
            )
        }

        // Performance Screen
        composable(
            route = Routes.PERFORM,
            arguments = listOf(
                navArgument("songId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getString("songId") ?: return@composable
            PerformScreen(
                songId = songId,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Setlist List Screen
        composable(Routes.SETLIST_LIST) {
            SetlistListScreen(
                onBackClick = { navController.popBackStack() },
                onSetlistClick = { setlistId ->
                    navController.navigate(Routes.setlistDetail(setlistId))
                },
                onCreateSetlist = { /* Handle new setlist */ }
            )
        }

        // Setlist Detail Screen
        composable(
            route = Routes.SETLIST_DETAIL,
            arguments = listOf(
                navArgument("setlistId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val setlistId = backStackEntry.arguments?.getString("setlistId") ?: return@composable
            SetlistDetailScreen(
                setlistId = setlistId,
                onBackClick = { navController.popBackStack() },
                onStartSetlist = { /* Start setlist performance */ },
                onSongClick = { songId ->
                    navController.navigate(Routes.songDetail(songId))
                }
            )
        }

        // Settings Screen
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

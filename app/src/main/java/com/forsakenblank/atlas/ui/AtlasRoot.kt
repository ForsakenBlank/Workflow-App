package com.forsakenblank.atlas.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.forsakenblank.atlas.ui.calendar.CalendarScreen
import com.forsakenblank.atlas.ui.explorer.ExplorerScreen
import com.forsakenblank.atlas.ui.explorer.TrashScreen
import com.forsakenblank.atlas.ui.home.HomeScreen
import com.forsakenblank.atlas.ui.notes.NoteEditorScreen
import com.forsakenblank.atlas.ui.notes.NotesScreen
import com.forsakenblank.atlas.ui.settings.SettingsScreen
import com.forsakenblank.atlas.ui.track.TrackScreen
import com.forsakenblank.atlas.ui.track.TrackerDetailScreen

enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Notes("notes", "Notes", Icons.Outlined.EditNote, Icons.Filled.EditNote),
    Calendar("calendar", "Calendar", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    Home("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Track("track", "Track", Icons.Outlined.Insights, Icons.Filled.Insights),
    Explorer("explorer", "Explorer", Icons.Outlined.FolderOpen, Icons.Filled.FolderOpen),
}

// one snackbar host for the whole app so "Logged. Undo" survives tab switches
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

class AtlasNavigator(private val nav: NavHostController) {
    fun openNote(id: Long) = nav.navigate("note/$id")
    fun openTracker(id: Long) = nav.navigate("tracker/$id")
    fun openSettings() = nav.navigate("settings")
    fun openTrash() = nav.navigate("trash")
    fun back() = nav.popBackStack()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtlasRoot() {
    val nav = rememberNavController()
    val navigator = remember(nav) { AtlasNavigator(nav) }
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tab = Tab.entries.firstOrNull { it.route == route }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            topBar = {
                if (tab != null) {
                    TopAppBar(
                        title = { Text(if (tab == Tab.Home) "Atlas" else tab.label) },
                        actions = {
                            IconButton(onClick = navigator::openSettings) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (tab != null) {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = t == tab,
                                onClick = {
                                    nav.navigate(t.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(if (t == tab) t.selectedIcon else t.icon, contentDescription = t.label) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            // the bars handle system insets, detail screens bring their own scaffold
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Tab.Home.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(Tab.Home.route) { HomeScreen(navigator) }
                composable(Tab.Notes.route) { NotesScreen(navigator) }
                composable(Tab.Calendar.route) { CalendarScreen() }
                composable(Tab.Track.route) { TrackScreen(navigator) }
                composable(Tab.Explorer.route) { ExplorerScreen(navigator) }
                composable(
                    "note/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    NoteEditorScreen(entry.arguments!!.getLong("id"), navigator)
                }
                composable(
                    "tracker/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    TrackerDetailScreen(entry.arguments!!.getLong("id"), navigator)
                }
                composable("settings") { SettingsScreen(navigator) }
                composable("trash") { TrashScreen(navigator) }
            }
        }
    }
}

package com.rork.weatherloom.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rork.weatherloom.audio.LoomAudio
import com.rork.weatherloom.audio.Sfx
import com.rork.weatherloom.core.level.DailyForecast
import com.rork.weatherloom.core.level.LevelLibrary
import com.rork.weatherloom.data.GameRepository
import com.rork.weatherloom.ui.components.rememberLoomPhase
import com.rork.weatherloom.ui.puzzle.PuzzleRoute
import com.rork.weatherloom.ui.screens.AlmanacScreen
import com.rork.weatherloom.ui.screens.DailyScreen
import com.rork.weatherloom.ui.screens.LevelEntry
import com.rork.weatherloom.ui.screens.LevelsScreen
import com.rork.weatherloom.ui.screens.TerrariumScreen
import com.rork.weatherloom.ui.theme.Loom

private enum class Tab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
) {
    Terrarium("terrarium", "Terrarium", Icons.Rounded.Yard, Icons.Outlined.Yard),
    Levels("levels", "Levels", Icons.Rounded.Terrain, Icons.Outlined.Terrain),
    Daily("daily", "Daily", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    Almanac("almanac", "Almanac", Icons.Rounded.MenuBook, Icons.Outlined.MenuBook)
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val repo = remember { GameRepository.get(context) }
    val save by repo.save.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showTabs = Tab.entries.any { it.route == route }
    val phase = rememberLoomPhase(save.reducedMotion)

    Scaffold(
        containerColor = Loom.Canvas,
        bottomBar = {
            if (showTabs) {
                NavigationBar(containerColor = Loom.Surface, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    LoomAudio.play(Sfx.Tap, 0.7f)
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = {
                                Text(tab.label, style = MaterialTheme.typography.labelSmall)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Loom.Ink,
                                selectedTextColor = Loom.Ink,
                                unselectedIconColor = Loom.Moss,
                                unselectedTextColor = Loom.Moss,
                                indicatorColor = Loom.SurfaceSunk
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        val tabPadding = PaddingValues(bottom = padding.calculateBottomPadding())

        NavHost(
            navController = navController,
            startDestination = Tab.Terrarium.route,
            modifier = if (showTabs) {
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
            } else {
                Modifier.fillMaxSize()
            }
        ) {
            composable(Tab.Terrarium.route) {
                val unlocked = save.collectibles.mapNotNull { LevelLibrary.collectible(it) }
                val nextId = repo.nextUnsolved()
                TerrariumScreen(
                    unlocked = unlocked,
                    solvedCount = repo.solvedCount(),
                    dailyStreak = repo.dailyStreak(DailyForecast.dayKey(), DailyForecast.recentKeys(30)),
                    continueLevel = nextId?.let { LevelLibrary.level(it) },
                    lastCollectible = save.lastCollectible?.let { LevelLibrary.collectible(it) },
                    phase = phase,
                    reducedMotion = save.reducedMotion,
                    contentPadding = tabPadding,
                    onContinue = { nextId?.let { navController.navigate("puzzle/$it") } },
                    onOpenAlmanac = { navController.navigate(Tab.Almanac.route) }
                )
            }

            composable(Tab.Levels.route) {
                val entries = LevelLibrary.levels.map {
                    LevelEntry(it, repo.ratingOf(it.id), repo.isUnlocked(it.id))
                }
                LevelsScreen(
                    chapters = LevelLibrary.chapters,
                    entries = entries,
                    contentPadding = tabPadding,
                    onOpen = { navController.navigate("puzzle/${it.id}") }
                )
            }

            composable(Tab.Daily.route) {
                val key = DailyForecast.dayKey()
                DailyScreen(
                    today = DailyForecast.forDay(key),
                    todayKey = key,
                    completedToday = key in save.dailyHistory,
                    streak = repo.dailyStreak(key, DailyForecast.recentKeys(30)),
                    history = save.dailyHistory.toSet(),
                    contentPadding = tabPadding,
                    onPlay = { navController.navigate("puzzle/daily-$key") }
                )
            }

            composable(Tab.Almanac.route) {
                AlmanacScreen(
                    collectibles = LevelLibrary.collectibles,
                    discovered = save.collectibles.toSet(),
                    reducedMotion = save.reducedMotion,
                    musicEnabled = save.musicEnabled,
                    soundEnabled = save.soundEnabled,
                    contentPadding = tabPadding,
                    phase = phase,
                    onReducedMotion = repo::setReducedMotion,
                    onMusicEnabled = {
                        repo.setMusicEnabled(it)
                        LoomAudio.setMusicEnabled(it)
                    },
                    onSoundEnabled = {
                        repo.setSoundEnabled(it)
                        LoomAudio.setSfxEnabled(it)
                        if (it) LoomAudio.play(Sfx.Tap, 0.7f)
                    }
                )
            }

            composable("puzzle/{levelId}") { entry ->
                val id = entry.arguments?.getString("levelId").orEmpty()
                PuzzleRoute(
                    levelId = id,
                    reducedMotion = save.reducedMotion,
                    onExit = { navController.popBackStack() },
                    onOpenLevel = { next ->
                        navController.navigate("puzzle/$next") {
                            popUpTo("puzzle/{levelId}") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

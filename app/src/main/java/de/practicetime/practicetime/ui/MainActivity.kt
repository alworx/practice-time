/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2022 Matthias Emde
 *
 * Parts of this software are licensed under the MIT license
 *
 * Copyright (c) 2022, Javier Carbone, author Michael Prommersberger
 */

package de.practicetime.practicetime.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat.startActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.google.android.material.composethemeadapter3.Mdc3Theme
import de.practicetime.practicetime.BuildConfig
import de.practicetime.practicetime.PracticeTime
import de.practicetime.practicetime.R
import de.practicetime.practicetime.database.entities.LibraryFolder
import de.practicetime.practicetime.database.entities.LibraryItem
import de.practicetime.practicetime.getActivity
import de.practicetime.practicetime.shared.ThemeSelections
import de.practicetime.practicetime.ui.goals.GoalsFragmentHolder
import de.practicetime.practicetime.ui.goals.ProgressUpdate
import de.practicetime.practicetime.ui.intro.AppIntroActivity
import de.practicetime.practicetime.ui.library.Library
import de.practicetime.practicetime.ui.sessionlist.SessionListFragmentHolder
import de.practicetime.practicetime.ui.statistics.StatisticsFragmentHolder
import de.practicetime.practicetime.utils.ExportDatabaseContract
import de.practicetime.practicetime.utils.ExportImportDialog
import de.practicetime.practicetime.utils.ImportDatabaseContract
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


sealed class Screen(
    val route: String,
    val navigationBarData: NavigationBarData? = null
) {
    object Sessions : Screen(
        route = "sessionList",
        NavigationBarData(
            title = R.string.navigationSessionsTitle,
            staticIcon = R.drawable.ic_sessions,
            animatedIcon = R.drawable.avd_sessions,
        )
    )
    object Goals : Screen(
        route = "goals",
        NavigationBarData(
            title = R.string.navigationGoalsTitle,
            staticIcon = R.drawable.ic_goals,
            animatedIcon = R.drawable.avd_goals
        )
    )
    object Statistics : Screen(
        route = "statisticsOverview",
        NavigationBarData(
            title = R.string.navigationStatisticsTitle,
            staticIcon = R.drawable.ic_bar_chart,
            animatedIcon = R.drawable.avd_bar_chart
        )
    )
    object Library : Screen(
        route = "library",
        NavigationBarData(
            title = R.string.navigationLibraryTitle,
            staticIcon = R.drawable.ic_library,
            animatedIcon = R.drawable.avd_library
        )
    )
    object ProgressUpdate : Screen(
        route = "progressUpdate",
    )

    data class NavigationBarData(
        @StringRes val title: Int,
        @DrawableRes val staticIcon: Int,
        @DrawableRes val animatedIcon: Int
    )
}

class MainActivity : AppCompatActivity() {

    private val navItems = listOf(
        Screen.Sessions,
        Screen.Goals,
        Screen.Statistics,
        Screen.Library
    )

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationGraphicsApi::class,
        ExperimentalAnimationApi::class
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (BuildConfig.DEBUG) {
            createDatabaseFirstRun()
//            launchAppIntroFirstRun()
        }

        val reloadDatabase = mutableStateOf(0)

        PracticeTime.exportLauncher = registerForActivityResult(
            ExportDatabaseContract()
        ) { PracticeTime.exportDatabaseCallback(applicationContext, it) }

        PracticeTime.importLauncher = registerForActivityResult(
            ImportDatabaseContract()
        ) {
            PracticeTime.importDatabaseCallback(applicationContext, it)
            reloadDatabase.value++
        }

        setContent {
            val mainState = rememberMainState()

            LaunchedEffect(reloadDatabase.value) {
                mainState.loadDatabase()
            }

            mainState.setTheme(try {
                PracticeTime.prefs.getInt(
                    PracticeTime.PREFERENCES_KEY_THEME,
                    ThemeSelections.SYSTEM.ordinal
                ).let { ordinal ->
                    ThemeSelections.values().first { it.ordinal == ordinal }
                }
            } catch (ex: Exception) {
                ThemeSelections.SYSTEM
            })

            Mdc3Theme {
                Scaffold(
                    bottomBar = {
                        val navBackStackEntry by mainState.navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        val showNavigationBar = currentDestination?.hierarchy?.any { dest ->
                            navItems.any { it.route == dest.route }
                        } == true

                        AnimatedVisibility(
                            visible = showNavigationBar,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(400)
                            ),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(400)
                            )
                        ) {
                            Box {
                                NavigationBar {
                                    navItems.forEach { screen ->
                                        val selected =
                                            currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                        val painterCount = 5
                                        var activePainter by remember { mutableStateOf(0) }
                                        val painter = rememberVectorPainter(
                                            image = ImageVector.vectorResource(screen.navigationBarData!!.staticIcon)
                                        )
                                        val animatedPainters = (0..painterCount).map {
                                            rememberAnimatedVectorPainter(
                                                animatedImageVector = AnimatedImageVector.animatedVectorResource(
                                                    screen.navigationBarData.animatedIcon
                                                ),
                                                atEnd = selected && activePainter == it
                                            )
                                        }
                                        NavigationBarItem(
                                            icon = {
                                                Image(
                                                    painter = if (selected) animatedPainters[activePainter] else painter,
                                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                                                    contentDescription = null
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = stringResource(screen.navigationBarData.title),
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                )
                                            },
                                            selected = selected,
                                            onClick = {
                                                if (!selected) activePainter =
                                                    (activePainter + 1) % painterCount
                                                mainState.navController.navigate(screen.route) {
                                                    // Pop up to the start destination of the graph to
                                                    // avoid building up a large stack of destinations
                                                    // on the back stack as users select items
                                                    popUpTo(mainState.navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    // Avoid multiple copies of the same destination when
                                                    // reselecting the same item
                                                    launchSingleTop = true
                                                    // Restore state when reselecting a previously selected item
                                                    restoreState = true
                                                }
                                            }
                                        )
                                    }
                                }

                                /** Navbar Scrim */
                                AnimatedVisibility(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .zIndex(1f),
                                    visible = mainState.showNavBarScrim.value,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Box(modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    val animationDuration = 400
                    AnimatedNavHost(
                        mainState.navController,
                        startDestination = Screen.Sessions.route,
                        Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                        enterTransition = {
                            slideInVertically(
                                animationSpec = tween(animationDuration),
                                initialOffsetY = { fullHeight -> -(fullHeight / 10) }
                            ) + fadeIn(
                                animationSpec = tween(
                                    animationDuration / 2,
                                    animationDuration / 2
                                )
                            )
                        },
                        exitTransition = {
                            slideOutVertically(
                                animationSpec = tween(animationDuration),
                                targetOffsetY = { fullHeight -> (fullHeight / 10) }
                            ) + fadeOut(animationSpec = tween(animationDuration / 2))
                        }
                    ) {
                        composable(
                            route = Screen.Sessions.route,
                            exitTransition = {
                                if(initialState.destination.route == Screen.ProgressUpdate.route) {
                                    fadeOut(tween(0))
                                } else null
                            }
                        ) { SessionListFragmentHolder(mainState, getActivity()) }
                        composable(
                            route = Screen.Goals.route,
                        ) { GoalsFragmentHolder(mainState) }
                        composable(
                            route = Screen.Statistics.route,
                        ) { StatisticsFragmentHolder() }
                        composable(
                            route = Screen.Library.route,
                        ) { Library (mainState) }
                        composable(
                            route = Screen.ProgressUpdate.route,
                            enterTransition = { fadeIn(tween(0)) }
                        ) { ProgressUpdate(mainState) }
                    }

                    /** Export / Import Dialog */
                    ExportImportDialog(
                        show = mainState.showExportImportDialog.value,
                        onDismissHandler = { mainState.showExportImportDialog.value = false }
                    )

                    // if there is a new session added to the intent, navigate to progress update
                    intent.extras?.getLong("KEY_SESSION")?.let { newSessionId ->
                        intent.removeExtra("KEY_SESSION")
                        mainState.navigateTo(Screen.ProgressUpdate.route)
                    }
                }
            }
        }
    }

    private fun launchAppIntroFirstRun() {
        if (!PracticeTime.prefs.getBoolean(PracticeTime.PREFERENCES_KEY_APPINTRO_DONE, false)) {
            val i = Intent(this, AppIntroActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NO_HISTORY
            startActivity(i)
        }
    }

    private fun createDatabaseFirstRun() {
        lifecycleScope.launch {

            // FIRST RUN routine
            if (PracticeTime.prefs.getBoolean(PracticeTime.PREFERENCES_KEY_FIRSTRUN, true)) {

                listOf(
                    LibraryFolder(name="Schupra"),
                    LibraryFolder(name="Fagott"),
                    LibraryFolder(name="Gesang"),
                ).forEach {
                    PracticeTime.libraryFolderDao.insert(it)
                    delay(1000) //make sure folders have different createdAt values
                }

                // populate the libraryItem table on first run
                listOf(
                    LibraryItem(name="Die Schöpfung", colorIndex=0, libraryFolderId = 1),
                    LibraryItem(name="Beethoven Septett", colorIndex=1, libraryFolderId = 1),
                    LibraryItem(name="Schostakowitsch 9.", colorIndex=2, libraryFolderId = 2),
                    LibraryItem(name="Trauermarsch c-Moll", colorIndex=3, libraryFolderId = 2),
                    LibraryItem(name="Adagio", colorIndex=4, libraryFolderId = 3),
                    LibraryItem(name="Eine kleine Gigue", colorIndex=5, libraryFolderId = 3),
                    LibraryItem(name="Andantino", colorIndex=6),
                    LibraryItem(name="Klaviersonate", colorIndex=7),
                    LibraryItem(name="Trauermarsch", colorIndex=8),
                ).forEach {
                    PracticeTime.libraryItemDao.insert(it)
                    delay(1000) //make sure items have different createdAt values
                }

                PracticeTime.prefs.edit().putBoolean(PracticeTime.PREFERENCES_KEY_FIRSTRUN, false).apply()
            }
        }
    }

    // periodically check if session is still running (if it is) to remove the badge if yes
    override fun onResume() {
        super.onResume()
        if (!BuildConfig.DEBUG)
            launchAppIntroFirstRun()
//        runnable = object : Runnable {
//            override fun run() {
//                if (PracticeTime.serviceIsRunning) {
//                    bottomNavigationView.getOrCreateBadge(R.id.sessionListFragment)
//                } else {
//                    bottomNavigationView.removeBadge(R.id.sessionListFragment)
//                }
//                if (PracticeTime.serviceIsRunning)
//                    handler.postDelayed(this, 1000)
//            }
//        }
//        handler = Handler(Looper.getMainLooper()).also {
//            it.post(runnable)
//        }
    }

    // remove the callback. Otherwise, the runnable will keep going and when entering the activity again,
    // there will be twice as much and so on...
//    override fun onStop() {
//        super.onStop()
//        handler.removeCallbacks(runnable)
//    }
}

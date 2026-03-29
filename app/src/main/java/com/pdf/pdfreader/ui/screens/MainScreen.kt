package com.pdf.pdfreader.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.pdf.pdfreader.ui.navigation.BottomNavItem
import com.pdf.pdfreader.ui.navigation.bottomNavItems
import com.pdf.pdfreader.ui.viewmodel.MainViewModel
import com.pdf.pdfreader.ui.viewmodel.PdfViewModel

@Composable
fun MainScreen(
    onNavigateToReader: (String) -> Unit,
    onNavigateToReaderWithSearch: (String, Int, String) -> Unit,
    mainViewModel: MainViewModel
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                bottomNavItems.forEach { item ->
                    val isSelected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = isSelected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                val pdfViewModel: PdfViewModel = hiltViewModel()
                HomeScreen(
                    viewModel = pdfViewModel,
                    onNavigateToSettings = { navController.navigate(BottomNavItem.Settings.route) },
                    onNavigateToReader = onNavigateToReader,
                    onNavigateToReaderWithSearch = onNavigateToReaderWithSearch
                )
            }
            composable("recent") {
                RecentScreen(
                    paddingValues = innerPadding,
                    onNavigateToReader = onNavigateToReader
                )
            }
            composable("favorite") {
                FavoriteScreen(
                    paddingValues = innerPadding,
                    onNavigateToReader = onNavigateToReader
                )
            }
            composable("settings_tab") {
                SettingsScreen(
                    mainViewModel = mainViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

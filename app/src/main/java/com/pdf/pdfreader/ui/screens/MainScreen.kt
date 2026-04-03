package com.pdf.pdfreader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
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

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.fillMaxSize()
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
                    paddingValues = PaddingValues(bottom = 100.dp),
                    onNavigateToReader = onNavigateToReader
                )
            }
            composable("favorite") {
                FavoriteScreen(
                    paddingValues = PaddingValues(bottom = 100.dp),
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

        // Floating Bottom Nav Bar overlay
        CuratorBottomNavBar(
            navController = navController,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun CuratorBottomNavBar(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), spotColor = Color.Black.copy(alpha = 0.05f))
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                val fgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor)
                        .clickable {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = fgColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = fgColor
                    )
                }
            }
        }
    }
}

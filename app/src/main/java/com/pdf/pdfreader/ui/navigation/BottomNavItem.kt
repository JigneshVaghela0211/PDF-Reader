package com.pdf.pdfreader.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem("home", "Home", Icons.Default.Home)
    object Recent : BottomNavItem("recent", "Recent", Icons.Default.History)
    object Favorite : BottomNavItem("favorite", "Favorite", Icons.Default.Favorite)
    object Settings : BottomNavItem("settings_tab", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Recent,
    BottomNavItem.Favorite,
    BottomNavItem.Settings
)

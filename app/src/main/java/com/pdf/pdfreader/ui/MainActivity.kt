package com.pdf.pdfreader.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pdf.pdfreader.ui.screens.HomeScreen
import com.pdf.pdfreader.ui.screens.MainScreen
import com.pdf.pdfreader.ui.screens.SettingsScreen
import com.pdf.pdfreader.ui.screens.SplashScreen
import com.pdf.pdfreader.ui.theme.PDFReaderTheme
import com.pdf.pdfreader.ui.viewmodel.MainViewModel
import com.pdf.pdfreader.ui.viewmodel.PdfViewModel
import com.pdf.pdfreader.ui.viewmodel.PdfReaderViewModel
import com.pdf.pdfreader.ui.screens.PdfReaderScreen
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val theme by mainViewModel.themeState.collectAsState()
            
            PDFReaderTheme(appTheme = theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "splash"
                    ) {
                        composable("splash") {
                            SplashScreen(
                                onNavigateToHome = {
                                    navController.navigate("main") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("main") {
                            MainScreen(
                                mainViewModel = mainViewModel,
                                onNavigateToReader = { path ->
                                    navController.navigate("pdf_reader/${Uri.encode(path)}")
                                },
                                onNavigateToReaderWithSearch = { path, pageIndex, query ->
                                    navController.navigate("pdf_reader/${Uri.encode(path)}?pageIndex=$pageIndex&searchQuery=${Uri.encode(query)}")
                                }
                            )
                        }
                        composable(
                            route = "pdf_reader/{path}?pageIndex={pageIndex}&searchQuery={searchQuery}",
                            arguments = listOf(
                                navArgument("path") { type = NavType.StringType },
                                navArgument("pageIndex") { type = NavType.IntType; defaultValue = -1 },
                                navArgument("searchQuery") { type = NavType.StringType; nullable = true; defaultValue = null }
                            )
                        ) { backStackEntry ->
                            val path = backStackEntry.arguments?.getString("path") ?: ""
                            val pageIndex = backStackEntry.arguments?.getInt("pageIndex") ?: -1
                            val searchQuery = backStackEntry.arguments?.getString("searchQuery")?.let { Uri.decode(it) }
                            val readerViewModel: PdfReaderViewModel = hiltViewModel()
                            PdfReaderScreen(
                                viewModel = readerViewModel,
                                path = Uri.decode(path),
                                initialPageIndex = pageIndex,
                                searchQuery = searchQuery,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

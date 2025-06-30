package com.example.swiftcard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.swiftcard.ui.AddEdit.AddEditScreen
import com.example.swiftcard.ui.DetailScreen.BusinessCardDetailScreen
import com.example.swiftcard.ui.ScanBusinessCard.ScanBusinessCardScreen
import com.example.swiftcard.ui.home.HomeScreen
import com.example.swiftcard.ui.theme.SwiftCardTheme
import com.example.swiftcard.util.Routes
import com.example.swiftcard.util.Screen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
<<<<<<< HEAD
=======
        enableEdgeToEdge()
>>>>>>> d403a9bbed3c91b0f5fb5115fa24772c0ca68cc0
        setContent {
            SwiftCardTheme {

                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }

                NavHost(navController = navController, startDestination = Routes.HomeScreen) {

                    // Use Routes.BUSINESS_CARD_LIST for the composable route
                    composable(Routes.HomeScreen) {
                        HomeScreen(
                            onNavigate = { route ->
                                // Map your utility Routes to the actual navigation routes
                                when (route) {
                                    Routes.ScanBusinessCardScreen -> navController.navigate(Routes.ScanBusinessCardScreen)

                                    Routes.AddBusinessCardScreen -> navController.navigate(Routes.ScanBusinessCardScreen)

                                    else -> navController.navigate(route)
                                }
                            },
                            onNavigateToEdit = { businessCardId ->
                                navController.navigate(Routes.createAddEditBusinessCardRoute(businessCardId))
                            },
                            onNavigateToDetail = { businessCardId ->
                                navController.navigate(Routes.createBusinessCardDetailRoute(businessCardId))
                            },
                            scaffoldState = snackbarHostState
                        )
                    }

                    // Use Routes.SCAN_BUSINESS_CARD for the composable route
                    composable(Routes.ScanBusinessCardScreen) {
                        ScanBusinessCardScreen (
                            onScanComplete = { extractedData ->
                                // Navigate to AddEdit screen with extracted data
                                // Use Routes.createAddEditBusinessCardRoute for the new card
                                navController.navigate(Routes.createAddEditBusinessCardRoute("null")) {
                                    popUpTo(Routes.ScanBusinessCardScreen) { inclusive = true } // Clear scan screen from back stack
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // Use Routes.ADD_EDIT_BUSINESS_CARD for the composable route
                    composable(
                        route = Routes.AddBusinessCardScreen, // Use the constant with the placeholder
                        arguments = listOf(
                            navArgument("businessCardId"){
                                type = NavType.StringType
                                nullable = true
                                defaultValue = "null"
                            }
                            )
                    ) { backStackEntry ->
                        val businessCardId = backStackEntry.arguments?.getString("businessCardId")
                        AddEditScreen(
                            businessCardId = if (businessCardId == "null") null else businessCardId,
                            onSaveComplete = { navController.popBackStack() },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // Use Routes.BUSINESS_CARD_DETAIL for the composable route
                    composable(
                        route = Routes.ViewBusinessCardScreen, // Use the constant with the placeholder
                        arguments = listOf(navArgument("businessCardId") {
                            type = NavType.StringType
                            nullable = false
                        })
                    ) { backStackEntry ->
                        val businessCardId = backStackEntry.arguments?.getString("businessCardId") ?: ""
                        BusinessCardDetailScreen(
                            businessCardId = businessCardId,
                            onEditClick = { idToEdit ->
                                navController.navigate(Routes.createAddEditBusinessCardRoute(idToEdit))
                            },
                            onDeleteComplete = { navController.popBackStack() },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }


            }
        }
    }
}


package com.example.swiftcard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
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
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                        ScanBusinessCardScreen(
                            onScanComplete = { extractedDataJson ->
                                // Construct the route using your Routes object and query parameters
                                navController.navigate(
                                    "${Routes.AddBusinessCardScreen}?cardId=null&extractedDataJson=${extractedDataJson}"
                                ) {
                                    popUpTo(Routes.ScanBusinessCardScreen) { inclusive = true }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // Use Routes.ADD_EDIT_BUSINESS_CARD for the composable route
                    composable(
                        route = "${Routes.AddBusinessCardScreen}?cardId={cardId}&extractedDataJson={extractedDataJson}",
                        arguments = listOf(
                            navArgument("cardId") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("extractedDataJson") { type = NavType.StringType; nullable = true; defaultValue = null }
                        )
                    ) { backStackEntry ->
                        val cardId = backStackEntry.arguments?.getString("cardId")
                        val extractedDataJson = backStackEntry.arguments?.getString("extractedDataJson")

                        val extractedDataMap: Map<String, String>? = if (!extractedDataJson.isNullOrBlank()) {
                            Gson().fromJson(extractedDataJson, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type)
                        } else {
                            null
                        }

                        AddEditScreen(
                            cardId = cardId,
                            extractedData = extractedDataMap,
                            onPopBackStack = { navController.popBackStack() }
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


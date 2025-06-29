package com.example.swiftcard.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.swiftcard.ui.components.BusinessCardItem
import com.example.swiftcard.util.Routes
import com.example.swiftcard.util.UiEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel : HomeScreenViewModel = hiltViewModel(),
    onNavigate: (String) -> Unit,
    onNavigateToEdit : (String) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    scaffoldState: SnackbarHostState
) {
    val businessCards = viewModel.businessCard.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.Navigate -> {
                    // Map your Routes to actual navigation destinations
                    when (event.route) {
                        Routes.AddBusinessCardScreen -> onNavigate(Routes.AddBusinessCardScreen) // Route to scan first
                        else -> onNavigate(event.route)
                    }
                }
                is UiEvent.ShowSnackBar -> {
                    scaffoldState.showSnackbar(
                        message = event.message,
                        actionLabel = event.action
                    )
                }
                UiEvent.PopBackStack -> { /* Handled by NavHost if needed, or by onBack() in specific screens */ }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Business Cards") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Trigger the event in your ViewModel
                    viewModel.onHomeEvent(HomeScreenEvent.onAddBusinessCardClick)
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Scan New Card")
            }
        },
        snackbarHost = { SnackbarHost(scaffoldState) }
    ) { paddingValues ->
        if (businessCards.value.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "No business cards found. Tap '+' to scan a new one!")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(businessCards.value, key = {it.id}) { card ->
                    BusinessCardItem(
                        businessCard = card,
                        onItemClick = {
                            onNavigateToDetail(card.id)
                        },
                        onDeleteClick = {
                            viewModel.deleteBusinessCard(card)
                        }
                    )
                }
            }
        }
    }


}
package com.example.swiftcard.ui.DetailScreen


import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

// BusinessCardDetailScreen: Displays the detailed information of a single business card.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessCardDetailScreen(
    businessCardId: String,
    viewModel: BusinessCardDetailViewModel = hiltViewModel(), // Injects ViewModel
    onEditClick: (String) -> Unit,
    onDeleteComplete: () -> Unit,
    onBack: () -> Unit
) {
    // Load card details when the screen is composed
    viewModel.loadBusinessCard(businessCardId)
    val businessCard by viewModel.businessCard.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Card Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    businessCard?.let { card ->
                        IconButton(onClick = { onEditClick(card.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Card")
                        }
                        IconButton(onClick = {
                            viewModel.deleteBusinessCard(card.id)
                            onDeleteComplete() // Navigate back after delete
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Card")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        businessCard?.let { card ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                card.imageURL?.let { imageUrl ->
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Full Business Card Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp) // Adjust height as needed
                            .padding(bottom = 16.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Text(text = "Name: ${card.name}", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Company: ${card.company}", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Title: ${card.title}", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))

                // Add more fields as needed
            }
        } ?: run {
            // Handle case where card is not found or still loading
            CircularProgressIndicator(modifier = Modifier.wrapContentSize(Alignment.Center))
        }
    }
}
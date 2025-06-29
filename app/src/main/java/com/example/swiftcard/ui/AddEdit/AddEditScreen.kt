package com.example.swiftcard.ui.AddEdit


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.swiftcard.data.model.BusinessCard

// AddEditBusinessCardScreen: For adding new business cards or editing existing ones.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    businessCardId: String?, // Nullable for adding new cards
    viewModel: AddEditViewModel = hiltViewModel(),
    onSaveComplete: () -> Unit,
    onBack: () -> Unit
) {
    // Load existing card if ID is provided
    LaunchedEffect(businessCardId) {
        if (businessCardId != null) {
            viewModel.loadBusinessCard(businessCardId)
        }
    }

    // Collect state from ViewModel
    val cardToEdit by viewModel.businessCard.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    // Internal state for text fields
    var name by remember { mutableStateOf(cardToEdit?.name ?: "") }
    var company by remember { mutableStateOf(cardToEdit?.company ?: "") }
    var title by remember { mutableStateOf(cardToEdit?.title ?: "") }
    var imageUrl by remember { mutableStateOf(cardToEdit?.imageURL ?: "") }


    // Update internal state when cardToEdit changes (e.g., after loading)
    LaunchedEffect(cardToEdit) {
        cardToEdit?.let {
            name = it.name
            company = it.company
            title = it.title
            imageUrl = it.imageURL ?: ""

        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (businessCardId == null) "Add Business Card" else "Edit Business Card") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val card = BusinessCard(
                            id = businessCardId ?: "", // Use existing ID or empty for new
                            name = name,
                            company = company,
                            title = title,
                            imageURL = imageUrl.ifEmpty { null },

                        )
                        viewModel.saveBusinessCard(card)
                        onSaveComplete() // Navigate back after saving
                    }, enabled = !isSaving) {
                        Icon(Icons.Default.Done, contentDescription = "Save Card")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image input field (for manually entering URL or showing preview)
            OutlinedTextField(
                value = imageUrl,
                onValueChange = { imageUrl = it },
                label = { Text("Image URL (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            // Display image preview if URL is available
            if (imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Card Image Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                label = { Text("Company") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Job Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )


            if (isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Saving...", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
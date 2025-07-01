package com.example.swiftcard.ui.AddEdit // Adjust package if different

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage // Import AsyncImage from Coil
import com.example.swiftcard.R // Assuming you have placeholder drawable here
import com.example.swiftcard.data.model.BusinessCard
import com.example.swiftcard.util.UiEvent
import kotlinx.coroutines.flow.collectLatest // Import collectLatest


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen( // Or whatever your Add/Edit composable is named
    cardId: String?, // Receive ID for editing existing card
    viewModel: AddEditViewModel = hiltViewModel(),
    onPopBackStack: () -> Unit, // Callback to navigate back
    extractedData: Map<String, String>? = null
) {
    val context = LocalContext.current
    val businessCard by viewModel.businessCard.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isImageUploading by viewModel.isImageUploading.collectAsState() // Observe image uploading state

    val snackbarHostState = remember { SnackbarHostState() }

    // State for individual input fields
    var name by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    LaunchedEffect(extractedData) {
        extractedData?.let { data ->
            name = data["name"] ?: ""
            company = data["company"] ?: ""
            title = data["title"] ?: ""
        }
    }

    // Update form fields when businessCard state changes (for editing)
    LaunchedEffect(businessCard) {
        businessCard?.let {
            name = it.name
            company = it.company
            title = it.title
        }
    }

    // Load business card if editing
    LaunchedEffect(cardId) {
        cardId?.let {
            if (it.isNotEmpty()) {
                viewModel.loadBusinessCard(it)
            }
        }
    }

    // Collect UI Events (Snackbars, PopBackStack)
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collectLatest { event ->
            when (event) {
                UiEvent.PopBackStack -> onPopBackStack()
                is UiEvent.ShowSnackBar -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.action
                    )
                }
                else -> Unit // Handle other UiEvents if any
            }
        }
    }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                viewModel.onImageSelected(it) // Call ViewModel to handle upload
            }
        }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (cardId.isNullOrEmpty()) "Add Business Card" else "Edit Business Card") },
                navigationIcon = {
                    IconButton(onClick = onPopBackStack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveBusinessCard(
                                BusinessCard(
                                    id = cardId ?: businessCard?.id ?: "", // Use existing ID if editing, or generated ID if already set by image upload, otherwise empty for new
                                    name = name,
                                    company = company,
                                    title = title,
                                    imageURL = businessCard?.imageURL // Pass the current imageURL from state
                                )
                            )
                        },
                        enabled = !isSaving && !isImageUploading // Disable save while saving or uploading image
                    ) {
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Profile Picture/Image Picker Area
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape) // Makes it circular
                    .clickable(enabled = !isImageUploading) { // Clickable to pick image
                        imagePickerLauncher.launch("image/*")
                    }
                    .align(Alignment.CenterHorizontally)
            ) {
                AsyncImage(
                    model = businessCard?.imageURL, // Load image from URL
                    contentDescription = "Business Card Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.img_2), // Add a placeholder drawable (e.g., a generic profile icon)
                    error = painterResource(R.drawable.img_3) // Add an error drawable
                )
                if (isImageUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Image",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                label = { Text("Company") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
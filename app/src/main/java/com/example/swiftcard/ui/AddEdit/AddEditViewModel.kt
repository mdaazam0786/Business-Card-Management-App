package com.example.swiftcard.ui.AddEdit


import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swiftcard.data.model.BusinessCard
import com.example.swiftcard.data.repository.BusinessCardRepository
import com.example.swiftcard.util.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// AddEditBusinessCardViewModel: Manages state and logic for adding/editing business cards.
@HiltViewModel
class AddEditViewModel @Inject constructor(
    private val repository: BusinessCardRepository
) : ViewModel() {

    private val _uiEvent = Channel<UiEvent>() // Add this
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _businessCard = MutableStateFlow<BusinessCard?>(null)
    val businessCard: StateFlow<BusinessCard?> = _businessCard.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isImageUploading = MutableStateFlow(false) // New state for image upload
    val isImageUploading: StateFlow<Boolean> = _isImageUploading.asStateFlow()

    // Loads an existing business card for editing.
    fun loadBusinessCard(id: String) {
        viewModelScope.launch {
            try {
                _businessCard.value = repository.getBusinessCardById(id)
            } catch (e: Exception) {
                println("Error loading business card for edit: ${e.message}")
            }
        }
    }

    // Saves (inserts or updates) a business card.
    fun saveBusinessCard(card: BusinessCard) {
        _isSaving.value = true
        viewModelScope.launch {
            try {
                repository.saveBusinessCard(card)
            } catch (e: Exception) {
                println("Error saving business card: ${e.message}")
                sendUiEvent(UiEvent.ShowSnackBar(message = "Error saving card: ${e.message}"))
            } finally {
                _isSaving.value = false
            }
        }
    }
    fun onImageSelected(imageUri: Uri) {
        _isImageUploading.value = true
        viewModelScope.launch {
            try {
                val currentCard = _businessCard.value
                val cardId = currentCard?.id ?: "" // Use existing ID or provide a new one (important for storage path)

                if (cardId.isEmpty()) {

                    sendUiEvent(UiEvent.ShowSnackBar(message = "Please save card details first if adding new image for new card."))
                    _isImageUploading.value = false
                    return@launch
                }

                sendUiEvent(UiEvent.ShowSnackBar(message = "Uploading image..."))
                val imageUrl = repository.uploadImage(imageUri, cardId) // Call new repository function

                // Update the businessCard StateFlow with the new image URL
                _businessCard.value = _businessCard.value?.copy(imageURL = imageUrl)

                sendUiEvent(UiEvent.ShowSnackBar(message = "Image uploaded successfully!"))
            } catch (e: Exception) {
                println("Error uploading image: ${e.message}")
                sendUiEvent(UiEvent.ShowSnackBar(message = "Error uploading image: ${e.message}"))
            } finally {
                _isImageUploading.value = false
            }
        }
    }

    private fun sendUiEvent(event: UiEvent) {
        viewModelScope.launch {
            _uiEvent.send(event)
        }
    }
}
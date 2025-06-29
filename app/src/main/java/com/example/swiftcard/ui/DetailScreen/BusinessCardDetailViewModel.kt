package com.example.swiftcard.ui.DetailScreen


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swiftcard.data.model.BusinessCard
import com.example.swiftcard.data.repository.BusinessCardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// BusinessCardDetailViewModel: Manages state and logic for the business card detail screen.
@HiltViewModel
class BusinessCardDetailViewModel @Inject constructor(
    private val repository: BusinessCardRepository
) : ViewModel() {

    private val _businessCard = MutableStateFlow<BusinessCard?>(null)
    val businessCard: StateFlow<BusinessCard?> = _businessCard.asStateFlow()

    // Loads a specific business card by its ID.
    fun loadBusinessCard(id: String) {
        viewModelScope.launch {
            try {
                _businessCard.value = repository.getBusinessCardById(id)
            } catch (e: Exception) {
                println("Error loading business card details: ${e.message}")
                _businessCard.value = null // Clear on error
            }
        }
    }

    // Deletes a business card by its ID.
    fun deleteBusinessCard(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteBusinessCard(id)
                // Optionally clear the state or navigate back
            } catch (e: Exception) {
                println("Error deleting business card: ${e.message}")
            }
        }
    }
}
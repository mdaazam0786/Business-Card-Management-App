package com.example.swiftcard.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swiftcard.data.model.BusinessCard
import com.example.swiftcard.data.repository.BusinessCardRepository
import com.example.swiftcard.util.Routes
import com.example.swiftcard.util.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    private val repository : BusinessCardRepository
) : ViewModel() {

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _businessCards = MutableStateFlow<List<BusinessCard>>(emptyList())
    val businessCard : StateFlow<List<BusinessCard>> = _businessCards.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllBusinessCardsRealtime().collect { cards ->
                _businessCards.value = cards
            }
        }
    }


    fun deleteBusinessCard(businessCard: BusinessCard) {
        viewModelScope.launch {
            try {
                repository.deleteBusinessCard(businessCard.id)
                // UI will automatically update via the flow from Firebase
            } catch (e: Exception) {
                println("Error deleting business card: ${e.message}")
            }
        }
    }

    fun onHomeEvent(event : HomeScreenEvent){
        when(event){
            HomeScreenEvent.onAddBusinessCardClick -> sendUiEvent(UiEvent.Navigate(Routes.AddBusinessCardScreen))
        }
    }

    private fun sendUiEvent(event : UiEvent){
        viewModelScope.launch {
            _uiEvent.send(event)
        }

    }
}
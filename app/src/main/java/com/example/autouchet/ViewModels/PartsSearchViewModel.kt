package com.example.autouchet.ViewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autouchet.Api.*
import kotlinx.coroutines.launch

class PartsSearchViewModel : ViewModel() {

    private val apiKey = "37e36c51-9a06-4d27-8a48-8f7d5a08d8a7"
    private val repository = PartsRepository(apiKey)

    private val _searchResults = MutableLiveData<List<UmapiPart>>()
    val searchResults: LiveData<List<UmapiPart>> = _searchResults

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _priceComparison = MutableLiveData<PartPriceComparison?>()
    val priceComparison: LiveData<PartPriceComparison?> = _priceComparison

    private val _analogs = MutableLiveData<List<UmapiPart>>()
    val analogs: LiveData<List<UmapiPart>> = _analogs

    private val _matchedPart = MutableLiveData<Pair<String?, String?>>()
    val matchedPart: LiveData<Pair<String?, String?>> = _matchedPart

    fun searchParts(article: String? = null, brand: String? = null, name: String? = null) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            repository.searchParts(article, brand, name)
                .onSuccess { parts ->
                    _searchResults.value = parts
                }
                .onFailure { e ->
                    _error.value = e.message
                }
            _isLoading.value = false
        }
    }

    fun getPriceComparison(article: String, brand: String) {
        _isLoading.value = true
        viewModelScope.launch {
            repository.getPriceComparison(article, brand)
                .onSuccess { comparison ->
                    _priceComparison.value = comparison
                }
                .onFailure { e -> _error.value = e.message }
            _isLoading.value = false
        }
    }

    fun getAnalogs(article: String, brand: String) {
        _isLoading.value = true
        viewModelScope.launch {
            repository.getAnalogs(article, brand)
                .onSuccess { parts -> _analogs.value = parts }
                .onFailure { e -> _error.value = e.message }
            _isLoading.value = false
        }
    }

    fun matchExpense(expense: com.example.autouchet.Models.Expense) {
        val (article, brand) = repository.matchExpenseToPart(expense)
        _matchedPart.value = Pair(article, brand)
        if (article != null) {
            searchParts(article = article, brand = brand)
        }
    }

    fun clearError() {
        _error.value = null
    }
}
package com.example.receiptreminder.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.receiptreminder.domain.repository.ProductRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProductListViewModel(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProductListUiState>(ProductListUiState.Loading)
    val uiState: StateFlow<ProductListUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun retry() {
        _uiState.value = ProductListUiState.Loading
        load()
    }

    private fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            try {
                productRepository.getProductsStream().collect { products ->
                    _uiState.value = ProductListUiState.Success(sortProductsForList(products))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = ProductListUiState.Error
            }
        }
    }
}

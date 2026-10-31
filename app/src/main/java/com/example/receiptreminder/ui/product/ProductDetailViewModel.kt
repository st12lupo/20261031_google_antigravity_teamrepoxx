package com.example.receiptreminder.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.receiptreminder.domain.repository.ProductRepository
import com.example.receiptreminder.domain.repository.ShoppingListRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

class ProductDetailViewModel(
    private val productId: String?,
    private val productRepository: ProductRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val today: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductDetailUiState())
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() {
        setContent(ProductDetailContent.Loading)
        load()
    }

    private fun setContent(content: ProductDetailContent) {
        _uiState.value = _uiState.value.copy(content = content)
    }

    private fun load() {
        loadJob?.cancel()
        val id = productId
        if (id.isNullOrBlank()) {
            setContent(ProductDetailContent.NotFound)
            return
        }
        loadJob = viewModelScope.launch {
            try {
                combine(
                    productRepository.getProductsStream(),
                    productRepository.getProductPurchasesStream(id)
                ) { products, purchases ->
                    val product = products.firstOrNull { it.id == id }
                    if (product == null) {
                        ProductDetailContent.NotFound
                    } else {
                        ProductDetailContent.Loaded(product, purchases.sortedByDescending { it.purchasedOn })
                    }
                }.collect { setContent(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setContent(ProductDetailContent.Error)
            }
        }
    }

    private val loadedProduct get() = (_uiState.value.content as? ProductDetailContent.Loaded)?.product

    // --- 買い物リスト操作 (B-2, F-5, F-6) ---

    fun markPurchased() {
        val p = loadedProduct ?: return
        runAction(ProductMessage.PURCHASED) { shoppingListRepository.markAsPurchased(p.id) }
    }

    fun snooze() {
        val p = loadedProduct ?: return
        runAction(ProductMessage.SNOOZED) { shoppingListRepository.snooze(p.id) }
    }

    fun stopTracking() {
        val p = loadedProduct ?: return
        runAction(ProductMessage.STOPPED) { shoppingListRepository.stopTracking(p.id) }
    }

    /** P-4 予測対象のオン・オフ */
    fun setTracked(tracked: Boolean) {
        val p = loadedProduct ?: return
        runAction(if (tracked) ProductMessage.TRACKED_ON else ProductMessage.TRACKED_OFF) {
            productRepository.updateTrackedStatus(p.id, tracked)
        }
    }

    // --- 購入の追加・修正・削除 (B-1, B-4) ---

    fun startAddPurchase() {
        if (loadedProduct == null) return
        _uiState.value = _uiState.value.copy(
            editor = PurchaseEditorState(
                dateText = formatDateInput(today()),
                quantityText = "1"
            )
        )
    }

    fun startEditPurchase(purchaseId: String) {
        val record = (_uiState.value.content as? ProductDetailContent.Loaded)
            ?.purchases?.firstOrNull { it.id == purchaseId } ?: return
        _uiState.value = _uiState.value.copy(
            editor = PurchaseEditorState(
                editingId = record.id,
                dateText = formatDateInput(record.purchasedOn),
                quantityText = record.quantity.toString()
            )
        )
    }

    fun updateEditor(dateText: String? = null, quantityText: String? = null) {
        val editor = _uiState.value.editor ?: return
        _uiState.value = _uiState.value.copy(
            editor = editor.copy(
                dateText = dateText ?: editor.dateText,
                quantityText = quantityText ?: editor.quantityText,
                dateError = if (dateText != null) null else editor.dateError,
                quantityError = if (quantityText != null) false else editor.quantityError
            )
        )
    }

    fun dismissEditor() {
        _uiState.value = _uiState.value.copy(editor = null)
    }

    fun saveEditor() {
        val state = _uiState.value
        val editor = state.editor ?: return
        val loaded = state.content as? ProductDetailContent.Loaded ?: return
        when (val result = validatePurchaseInput(editor.dateText, editor.quantityText, today())) {
            is PurchaseValidation.Invalid -> {
                _uiState.value = state.copy(
                    editor = editor.copy(
                        dateError = result.dateError,
                        quantityError = result.quantityError
                    )
                )
            }
            is PurchaseValidation.Valid -> {
                val value = result.value
                val editingId = editor.editingId
                if (editingId == null) {
                    _uiState.value = state.copy(editor = null)
                    runAction(ProductMessage.ADDED) {
                        shoppingListRepository.addManualPurchase(
                            productName = loaded.product.name,
                            purchasedOn = value.date,
                            quantity = value.quantity
                        )
                    }
                } else {
                    val original = loaded.purchases.firstOrNull { it.id == editingId }
                    _uiState.value = state.copy(editor = null)
                    if (original == null) {
                        // 編集中に他で削除された
                        _uiState.value = _uiState.value.copy(message = ProductMessage.FAILED)
                        return
                    }
                    runAction(ProductMessage.UPDATED) {
                        productRepository.updatePurchase(
                            original.copy(purchasedOn = value.date, quantity = value.quantity)
                        )
                    }
                }
            }
        }
    }

    fun requestDelete(purchaseId: String) {
        val record = (_uiState.value.content as? ProductDetailContent.Loaded)
            ?.purchases?.firstOrNull { it.id == purchaseId } ?: return
        _uiState.value = _uiState.value.copy(deleteTarget = record)
    }

    fun dismissDelete() {
        _uiState.value = _uiState.value.copy(deleteTarget = null)
    }

    fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        _uiState.value = _uiState.value.copy(deleteTarget = null)
        runAction(ProductMessage.DELETED) { productRepository.deletePurchase(target.id) }
    }

    fun messageShown() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun runAction(success: ProductMessage, block: suspend () -> Unit) {
        viewModelScope.launch {
            val message = try {
                block()
                success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProductMessage.FAILED
            }
            _uiState.value = _uiState.value.copy(message = message)
        }
    }
}

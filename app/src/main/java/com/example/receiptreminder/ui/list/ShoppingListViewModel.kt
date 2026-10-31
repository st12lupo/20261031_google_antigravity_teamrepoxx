package com.example.receiptreminder.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.receiptreminder.domain.model.ShoppingItem
import com.example.receiptreminder.domain.model.StockStatus
import com.example.receiptreminder.domain.repository.ShoppingListRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/** 買い物リストの 1 行 */
data class ShoppingRow(
    val productId: String,
    val name: String,
    val remainingDays: Int,
    val isEstimated: Boolean
)

enum class NameError { EMPTY, TOO_LONG }
enum class DateError { INVALID, FUTURE }
enum class QuantityError { INVALID, OUT_OF_RANGE }

/** 手動追加フォームの状態。日付と数量は入力中の文字列のまま持つ */
data class AddFormState(
    val name: String = "",
    val date: String,
    val quantity: String = "1",
    val nameError: NameError? = null,
    val dateError: DateError? = null,
    val quantityError: QuantityError? = null,
    val isSaving: Boolean = false
)

data class ShoppingListUiState(
    val isLoading: Boolean = true,
    /** 一覧の取得に失敗した */
    val loadFailed: Boolean = false,
    /** 購入済みなどの操作に失敗した(表示したら consumeActionError で消す) */
    val actionFailed: Boolean = false,
    /** なくなりそう(切れる日を過ぎた商品を含む) */
    val urgent: List<ShoppingRow> = emptyList(),
    /** まだ大丈夫 */
    val safe: List<ShoppingRow> = emptyList(),
    /** null のときは手動追加ダイアログを表示しない */
    val addForm: AddFormState? = null
) {
    val isEmpty: Boolean get() = !isLoading && !loadFailed && urgent.isEmpty() && safe.isEmpty()
}

class ShoppingListViewModel(
    private val repository: ShoppingListRepository,
    private val today: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observe()
    }

    /** 一覧の取得をやり直す */
    fun retry() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        observe()
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.getShoppingItemsStream()
                .catch {
                    // 購入データはログに出さない
                    _uiState.update { s -> s.copy(isLoading = false, loadFailed = true) }
                }
                .collect { items ->
                    val rows = items
                        .filter { it.product.status != StockStatus.NOT_TRACKED }
                        .sortedBy { it.product.remainingDays }
                    val (urgent, safe) = rows.partition { it.product.status != StockStatus.SUFFICIENT }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadFailed = false,
                            urgent = urgent.map(::toRow),
                            safe = safe.map(::toRow)
                        )
                    }
                }
        }
    }

    private fun toRow(item: ShoppingItem) = ShoppingRow(
        productId = item.product.id,
        name = item.product.name,
        remainingDays = item.product.remainingDays,
        isEstimated = item.product.isEstimated
    )

    /** 購入済み(数量は 1。B-2) */
    fun onPurchased(productId: String) = runAction { repository.markAsPurchased(productId, 1) }

    /** まだある */
    fun onStillHave(productId: String) = runAction { repository.snooze(productId) }

    /** もう買わない */
    fun onDoNotBuy(productId: String) = runAction { repository.stopTracking(productId) }

    fun consumeActionError() {
        _uiState.update { it.copy(actionFailed = false) }
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(actionFailed = true) }
            }
        }
    }

    // ---- 手動追加 (B-1) ----

    fun openAddForm() {
        _uiState.update { it.copy(addForm = AddFormState(date = DATE_FORMAT.format(today()))) }
    }

    fun dismissAddForm() {
        _uiState.update { it.copy(addForm = null) }
    }

    fun onNameChange(value: String) = updateForm { it.copy(name = value, nameError = null) }
    fun onDateChange(value: String) = updateForm { it.copy(date = value, dateError = null) }
    fun onQuantityChange(value: String) = updateForm { it.copy(quantity = value, quantityError = null) }

    private fun updateForm(transform: (AddFormState) -> AddFormState) {
        _uiState.update { s -> s.addForm?.let { s.copy(addForm = transform(it)) } ?: s }
    }

    /** 入力を検証して保存する。誤りがあればフォームにエラーを出して保存しない */
    fun submitAddForm() {
        val form = _uiState.value.addForm ?: return
        if (form.isSaving) return

        val name = form.name.trim()
        val nameError = when {
            name.isEmpty() -> NameError.EMPTY
            name.length > MAX_NAME_LENGTH -> NameError.TOO_LONG
            else -> null
        }
        val date = parseDate(form.date.trim())
        val dateError = when {
            date == null -> DateError.INVALID
            date.isAfter(today()) -> DateError.FUTURE
            else -> null
        }
        val quantity = form.quantity.trim().toIntOrNull()
        val quantityError = when {
            quantity == null -> QuantityError.INVALID
            quantity !in 1..MAX_QUANTITY -> QuantityError.OUT_OF_RANGE
            else -> null
        }
        if (nameError != null || dateError != null || quantityError != null) {
            updateForm {
                it.copy(nameError = nameError, dateError = dateError, quantityError = quantityError)
            }
            return
        }

        updateForm { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                repository.addManualPurchase(name, date!!, quantity!!)
                _uiState.update { it.copy(addForm = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { s ->
                    s.copy(addForm = s.addForm?.copy(isSaving = false), actionFailed = true)
                }
            }
        }
    }

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text, DATE_FORMAT)
    } catch (e: DateTimeParseException) {
        null
    }

    companion object {
        const val MAX_NAME_LENGTH = 50
        const val MAX_QUANTITY = 99
        private val DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    }
}

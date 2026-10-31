package com.example.receiptreminder.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.receiptreminder.domain.model.ReceiptCandidate
import com.example.receiptreminder.domain.model.ReceiptItemCandidate
import com.example.receiptreminder.domain.repository.ReceiptReviewService
import com.example.receiptreminder.domain.repository.ReceiptScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

enum class ReviewPhase { ChooseSource, Scanning, ScanFailed, Editing }

enum class ReviewResult { Saved, Cancelled }

enum class FieldError { Required, InvalidDate, InvalidNumber, InvalidQuantity }

/** 確認画面で編集中の商品行。数値は入力途中の文字列で保持する */
data class ItemForm(
    val id: String,
    val rawOcrText: String = "",
    val name: String = "",
    val quantity: String = "1",
    val price: String = "",
    val isTracked: Boolean = true,
    val mergedNotice: String? = null
)

/** 確認画面で編集中のレシート */
data class ReceiptForm(
    val receiptId: String = "",
    val storeName: String = "",
    val date: String = "",
    val total: String = "",
    val items: List<ItemForm> = emptyList()
)

data class ItemErrors(
    val name: FieldError? = null,
    val quantity: FieldError? = null,
    val price: FieldError? = null
) {
    val hasError get() = name != null || quantity != null || price != null
}

data class FormValidation(
    val store: FieldError? = null,
    val date: FieldError? = null,
    val total: FieldError? = null,
    val noItems: Boolean = false,
    val items: Map<String, ItemErrors> = emptyMap()
) {
    val isValid
        get() = store == null && date == null && total == null && !noItems &&
            items.values.none { it.hasError }
}

data class ReceiptReviewUiState(
    val phase: ReviewPhase = ReviewPhase.ChooseSource,
    val form: ReceiptForm = ReceiptForm(),
    /** 撮影で作られた画像があり、保存時に削除するとき true */
    val hasCapturedImages: Boolean = false,
    /** 利用者が選択した画像を読み取った結果を表示しているとき true */
    val hasPickedImages: Boolean = false,
    val isDuplicate: Boolean = false,
    /** 保存を一度試みたあとに true。以降は入力エラーを表示する */
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val finished: ReviewResult? = null
) {
    val validation: FormValidation get() = form.validate()
}

private val DATE_FORMATTERS = listOf("uuuu/M/d", "uuuu-M-d").map {
    DateTimeFormatter.ofPattern(it).withResolverStyle(ResolverStyle.STRICT)
}
private val DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu/MM/dd")

private fun normalizeNumber(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC)
        .replace("¥", "").replace("円", "").replace(",", "").replace(" ", "").trim()

internal fun parseDate(text: String): LocalDate? {
    val t = Normalizer.normalize(text, Normalizer.Form.NFKC).trim()
    if (t.isEmpty()) return null
    for (f in DATE_FORMATTERS) {
        try {
            return LocalDate.parse(t, f)
        } catch (_: Exception) {
        }
    }
    return null
}

internal fun parseNonNegativeInt(text: String): Int? =
    normalizeNumber(text).takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()

internal fun ReceiptForm.validate(): FormValidation {
    val itemErrors = items.associate { item ->
        item.id to ItemErrors(
            name = if (item.name.isBlank()) FieldError.Required else null,
            quantity = when {
                item.quantity.isBlank() -> FieldError.Required
                (parseNonNegativeInt(item.quantity) ?: 0) < 1 -> FieldError.InvalidQuantity
                else -> null
            },
            price = when {
                item.price.isBlank() -> FieldError.Required
                parseNonNegativeInt(item.price) == null -> FieldError.InvalidNumber
                else -> null
            }
        )
    }
    return FormValidation(
        store = if (storeName.isBlank()) FieldError.Required else null,
        date = when {
            date.isBlank() -> FieldError.Required
            parseDate(date) == null -> FieldError.InvalidDate
            else -> null
        },
        total = when {
            total.isBlank() -> FieldError.Required
            parseNonNegativeInt(total) == null -> FieldError.InvalidNumber
            else -> null
        },
        noItems = items.isEmpty(),
        items = itemErrors
    )
}

/**
 * レシート確認画面の ViewModel。
 * 画像の取得元の選択、読み取り、確認・修正・保存・取り消しを扱う。
 * 撮影で作られた画像だけを削除し、利用者が選択した元の画像は削除しない。
 */
class ReceiptReviewViewModel(
    private val scanner: ReceiptScanner,
    private val service: ReceiptReviewService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiptReviewUiState())
    val uiState: StateFlow<ReceiptReviewUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var duplicateJob: Job? = null
    private var newItemCounter = 0
    private var capturedImageUris: List<String> = emptyList()

    /** 撮影で得た画像を読み取る */
    fun onImagesCaptured(imageUris: List<String>) = recognize(imageUris, captured = true)

    /** 利用者が選択した保存済みの画像を読み取る */
    fun onImagesPicked(imageUris: List<String>) = recognize(imageUris, captured = false)

    /** カメラを起動できなかったとき、読み取り失敗と同じ選択肢を出す */
    fun onCaptureUnavailable() {
        _uiState.value = ReceiptReviewUiState(phase = ReviewPhase.ScanFailed)
    }

    /** 取得元の選択に戻る。再撮影と画像の選び直しに使う */
    fun chooseSourceAgain() {
        scanJob?.cancel()
        duplicateJob?.cancel()
        deleteCapturedImages()
        _uiState.value = ReceiptReviewUiState()
    }

    private fun deleteCapturedImages() {
        val uris = capturedImageUris
        capturedImageUris = emptyList()
        if (uris.isNotEmpty()) scanner.deleteImages(uris)
    }

    private fun recognize(imageUris: List<String>, captured: Boolean) {
        if (imageUris.isEmpty()) return
        scanJob?.cancel()
        duplicateJob?.cancel()
        deleteCapturedImages()
        if (captured) capturedImageUris = imageUris
        _uiState.value = ReceiptReviewUiState(phase = ReviewPhase.Scanning)
        scanJob = viewModelScope.launch {
            val result = try {
                scanner.scanReceipt(imageUris)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            result.fold(
                onSuccess = { showCandidate(it) },
                onFailure = { _uiState.update { s -> s.copy(phase = ReviewPhase.ScanFailed) } }
            )
        }
    }

    /** 手動入力: 空の候補で確認画面を開く */
    fun startManualInput() {
        scanJob?.cancel()
        duplicateJob?.cancel()
        deleteCapturedImages()
        _uiState.value = ReceiptReviewUiState(
            phase = ReviewPhase.Editing,
            form = ReceiptForm(receiptId = "manual")
        )
    }

    private fun showCandidate(c: ReceiptCandidate) {
        _uiState.value = ReceiptReviewUiState(
            phase = ReviewPhase.Editing,
            hasCapturedImages = capturedImageUris.isNotEmpty(),
            hasPickedImages = capturedImageUris.isEmpty(),
            form = ReceiptForm(
                receiptId = c.id,
                storeName = c.storeName,
                date = c.purchasedOn.format(DISPLAY_DATE_FORMAT),
                total = c.totalAmount.toString(),
                items = c.items.map {
                    ItemForm(
                        id = it.id,
                        rawOcrText = it.rawOcrText,
                        name = it.normalizedName,
                        quantity = it.quantity.toString(),
                        price = it.price.toString(),
                        isTracked = it.isTracked,
                        mergedNotice = it.mergedNotice
                    )
                }
            )
        )
        refreshDuplicate()
    }

    fun onStoreNameChange(value: String) {
        editForm { it.copy(storeName = value) }
        refreshDuplicate()
    }

    fun onDateChange(value: String) {
        editForm { it.copy(date = value) }
        refreshDuplicate()
    }

    fun onTotalChange(value: String) {
        editForm { it.copy(total = value) }
        refreshDuplicate()
    }

    fun onItemNameChange(id: String, value: String) = editItem(id) { it.copy(name = value) }
    fun onItemQuantityChange(id: String, value: String) = editItem(id) { it.copy(quantity = value) }
    fun onItemPriceChange(id: String, value: String) = editItem(id) { it.copy(price = value) }

    fun addItem() {
        val id = "new-${newItemCounter++}"
        editForm { it.copy(items = it.items + ItemForm(id = id)) }
    }

    fun removeItem(id: String) {
        editForm { f -> f.copy(items = f.items.filterNot { it.id == id }) }
    }

    private fun editItem(id: String, transform: (ItemForm) -> ItemForm) {
        editForm { f -> f.copy(items = f.items.map { if (it.id == id) transform(it) else it }) }
    }

    private fun editForm(transform: (ReceiptForm) -> ReceiptForm) {
        _uiState.update { it.copy(form = transform(it.form), saveFailed = false) }
    }

    /** 店名、日付、合計金額が変わったら重複を判定し直す(R-5) */
    private fun refreshDuplicate() {
        duplicateJob?.cancel()
        val form = _uiState.value.form
        val date = parseDate(form.date)
        val total = parseNonNegativeInt(form.total)
        if (form.storeName.isBlank() || date == null || total == null) {
            _uiState.update { it.copy(isDuplicate = false) }
            return
        }
        duplicateJob = viewModelScope.launch {
            val duplicate = try {
                service.checkDuplicate(form.storeName.trim(), date.toString(), total)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _uiState.update { it.copy(isDuplicate = duplicate) }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.phase != ReviewPhase.Editing || state.finished != null) return
        if (!state.form.validate().isValid) {
            _uiState.update { it.copy(showErrors = true) }
            return
        }
        val f = state.form
        val candidate = ReceiptCandidate(
            id = f.receiptId,
            storeName = f.storeName.trim(),
            purchasedOn = parseDate(f.date)!!,
            totalAmount = parseNonNegativeInt(f.total)!!,
            isDuplicateWarning = state.isDuplicate,
            items = f.items.map {
                ReceiptItemCandidate(
                    id = it.id,
                    rawOcrText = it.rawOcrText,
                    normalizedName = it.name.trim(),
                    quantity = parseNonNegativeInt(it.quantity)!!,
                    price = parseNonNegativeInt(it.price)!!,
                    isTracked = it.isTracked,
                    mergedNotice = it.mergedNotice
                )
            }
        )
        _uiState.update { it.copy(showErrors = true, isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            val result = try {
                service.saveReceipt(candidate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (result.isSuccess) deleteCapturedImages()
            _uiState.update {
                if (result.isSuccess) it.copy(isSaving = false, finished = ReviewResult.Saved)
                else it.copy(isSaving = false, saveFailed = true)
            }
        }
    }

    /** 取り消し: 取り込み中のレシートを破棄して閉じる */
    fun cancel() {
        if (_uiState.value.isSaving || _uiState.value.finished != null) return
        scanJob?.cancel()
        duplicateJob?.cancel()
        deleteCapturedImages()
        viewModelScope.launch {
            try {
                service.discardReceipt()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            _uiState.update { it.copy(finished = ReviewResult.Cancelled) }
        }
    }

    class Factory(
        private val scanner: ReceiptScanner,
        private val service: ReceiptReviewService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReceiptReviewViewModel(scanner, service) as T
    }
}

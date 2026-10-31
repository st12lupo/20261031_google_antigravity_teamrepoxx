package com.example.receiptreminder.ui.product

import com.example.receiptreminder.domain.model.Product
import com.example.receiptreminder.domain.model.PurchaseRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** 購入の入力で許す数量の上限 */
const val MAX_PURCHASE_QUANTITY = 99

enum class DateInputError { INVALID, FUTURE }

/** 購入の追加・修正ダイアログの入力状態。editingId が null なら追加 */
data class PurchaseEditorState(
    val editingId: String? = null,
    val dateText: String,
    val quantityText: String,
    val dateError: DateInputError? = null,
    val quantityError: Boolean = false
)

/** 操作結果の通知(文言は UI 側で資源から引く) */
enum class ProductMessage {
    PURCHASED, SNOOZED, STOPPED, TRACKED_ON, TRACKED_OFF, ADDED, UPDATED, DELETED, FAILED
}

/** 検証済みの購入入力 */
data class ValidatedPurchase(val date: LocalDate, val quantity: Int)

sealed interface PurchaseValidation {
    data class Valid(val value: ValidatedPurchase) : PurchaseValidation
    data class Invalid(val dateError: DateInputError?, val quantityError: Boolean) : PurchaseValidation
}

private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT)

fun parseDateInput(text: String): LocalDate? =
    runCatching { LocalDate.parse(text.trim().replace('/', '-'), DATE_FORMAT) }.getOrNull()

fun formatDateInput(date: LocalDate): String = date.toString()

/** 日付と数量の入力を検証する。未来の日付、1〜99 以外の数量は不正 */
fun validatePurchaseInput(dateText: String, quantityText: String, today: LocalDate): PurchaseValidation {
    val date = parseDateInput(dateText)
    val dateError = when {
        date == null -> DateInputError.INVALID
        date.isAfter(today) -> DateInputError.FUTURE
        else -> null
    }
    val quantity = quantityText.trim().toIntOrNull()
    val quantityError = quantity == null || quantity !in 1..MAX_PURCHASE_QUANTITY
    return if (dateError == null && !quantityError && date != null && quantity != null) {
        PurchaseValidation.Valid(ValidatedPurchase(date, quantity))
    } else {
        PurchaseValidation.Invalid(dateError, quantityError)
    }
}

/** 周期の表示用。30.0 は「30」、7.5 は「7.5」 */
fun formatCycleDays(days: Double): String {
    val rounded = Math.round(days * 10.0) / 10.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
}

/** 一覧の並び: 予測対象を残り日数の少ない順、そのあとに対象外を名前順 */
fun sortProductsForList(products: List<Product>): List<Product> =
    products.filter { it.isTracked }.sortedBy { it.remainingDays } +
        products.filter { !it.isTracked }.sortedBy { it.name }

sealed interface ProductListUiState {
    data object Loading : ProductListUiState
    data object Error : ProductListUiState
    data class Success(val products: List<Product>) : ProductListUiState
}

sealed interface ProductDetailContent {
    data object Loading : ProductDetailContent
    data object NotFound : ProductDetailContent
    data object Error : ProductDetailContent
    data class Loaded(val product: Product, val purchases: List<PurchaseRecord>) : ProductDetailContent
}

data class ProductDetailUiState(
    val content: ProductDetailContent = ProductDetailContent.Loading,
    val editor: PurchaseEditorState? = null,
    val deleteTarget: PurchaseRecord? = null,
    val message: ProductMessage? = null
)

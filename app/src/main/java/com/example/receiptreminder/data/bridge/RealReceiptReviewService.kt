package com.example.receiptreminder.data.bridge

import com.example.receiptreminder.domain.model.ReceiptCandidate
import com.example.receiptreminder.domain.repository.ReceiptReviewService as UiReceiptReviewService
import domain.model.ReceiptItemData
import domain.model.TrackingDefault
import domain.receipt.ProductNormalizer
import domain.receipt.ReceiptScanner as OcrReceiptScanner
import domain.repository.ReminderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import receipt.normalize.DefaultProductNormalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * レシート確認画面での重複チェック・保存・破棄、および画像削除（R-6）を管理するブリッジ実装。
 */
class RealReceiptReviewService(
    private val reminderRepository: ReminderRepository,
    private val ocrScanner: OcrReceiptScanner? = null,
    private val normalizer: ProductNormalizer? = null,
    initialPendingReceipt: ReceiptCandidate? = null,
    initialImageUri: String? = null,
    private val trigger: DataUpdateTrigger = DataUpdateTrigger(),
    aliasStore: LearnedAliasStore? = null
) : UiReceiptReviewService {

    /** 別名の永続化。名寄せ器が DefaultProductNormalizer のときだけ使える */
    private val aliasStore: LearnedAliasStore? = aliasStore
        ?: (normalizer as? DefaultProductNormalizer)?.let { LearnedAliasStore(reminderRepository, it) }

    private val _pendingReceipt = MutableStateFlow<ReceiptCandidate?>(initialPendingReceipt)
    private var pendingImageUri: String? = initialImageUri

    /**
     * スキャナーまたは外部から確認待ちレシートと元画像URIをセットする。
     */
    fun setPendingReceipt(receipt: ReceiptCandidate?, imageUri: String? = null) {
        _pendingReceipt.value = receipt
        pendingImageUri = imageUri
    }

    /**
     * 現在保持している元画像URIを取得（テスト等確認用）
     */
    fun getPendingImageUri(): String? = pendingImageUri

    override suspend fun getPendingReceipt(): ReceiptCandidate? {
        return _pendingReceipt.value
    }

    override suspend fun checkDuplicate(storeName: String, dateString: String, totalAmount: Int): Boolean {
        val date = parseDate(dateString) ?: return false
        val duplicate = reminderRepository.findDuplicateReceipt(
            storeName = storeName.trim(),
            purchasedOnEpochDay = date.toEpochDay(),
            totalAmount = totalAmount
        )
        return duplicate != null
    }

    override suspend fun saveReceipt(receipt: ReceiptCandidate): Result<Unit> {
        return runCatching {
            aliasStore?.ensureLoaded()
            val epochDay = receipt.purchasedOn.toEpochDay()

            // 予測対象外の商品も購入履歴には残す。予測の対象かどうかは isTracked で商品に持たせる(P-2, P-4)
            val corrections = mutableListOf<Triple<String, String, String>>()
            // P-2, R-4: 利用者が取り消した別名。メモリからは先に外し、Room は商品の保存後に消す
            val cancelledAliases = mutableListOf<Pair<String, String>>()
            val itemDataList = receipt.items.map { item ->
                // 入力した名前の照合キーが読み取った表記(raw)のキーと同じなら、別名を取り消す操作とみなし、
                // 別名をたどらず入力した名前で保存する (「特売牛乳」を「牛乳」に直したあと、戻したとき)
                val rawForCancel = item.rawOcrText.trim()
                val store = aliasStore
                if (store != null && rawForCancel.isNotEmpty() &&
                    aliasKeyOf(rawForCancel).isNotEmpty() &&
                    aliasKeyOf(rawForCancel) == aliasKeyOf(item.normalizedName)
                ) {
                    val target = store.forgetInMemory(rawForCancel)
                    if (target != null) cancelledAliases += rawForCancel to target
                }
                // 確認画面で入力した名前も、スキャン時と同じ別名・辞書の完全一致を通す (「ぎゅうにゅう」は牛乳)。
                // 一致しなければ容量表記などを除いた名前のまま別の商品にする
                val (name, category) = resolveName(item.normalizedName)

                // P-2: 確認画面で自動の名寄せと違う名前に直した行は、予測対象かどうかに関係なく別名として覚える。
                // 解決後の名前と、取り消しの処理が済んだ状態での自動の結果を比べる。同じ商品なら覚えない。
                // 読み取った表記そのものへの修正(部分一致で別の商品になる「液体洗剤ホワイト」を、そのままの名前に直したとき)や、
                // 別名を取り消した行(「国産米」)でも、自動の結果と違えば raw -> 入力した名前を覚える。
                // 別名を外した結果が入力した名前と同じ(「特売牛乳」の取り消し)なら、何も覚えない
                val raw = item.rawOcrText.trim()
                if (name.isNotEmpty() && raw.isNotEmpty() && normalizer != null &&
                    normalizer.normalize(raw).normalizedName != name
                ) {
                    corrections += Triple(raw, name, category)
                }

                ReceiptItemData(
                    rawName = item.rawOcrText.ifBlank { name },
                    normalizedName = name,
                    category = category,
                    quantity = item.quantity.toDouble().coerceAtLeast(1.0),
                    price = item.price,
                    isTracked = item.isTracked
                )
            }

            try {
                reminderRepository.saveReceiptAndPurchases(
                    storeName = receipt.storeName.trim(),
                    purchasedOnEpochDay = epochDay,
                    totalAmount = receipt.totalAmount,
                    items = itemDataList
                )
            } catch (e: Throwable) {
                // 保存できなかったときは、取り消した別名をメモリに戻す(Room にはまだ残っている)
                cancelledAliases.forEach { (raw, target) -> aliasStore?.restoreInMemory(raw, target) }
                throw e
            }

            // 取り消した別名を Room から消す。ここで失敗してもレシートは保存済みなので失敗にしない
            aliasStore?.let { store ->
                cancelledAliases.forEach { (raw, _) ->
                    try {
                        store.forgetPersisted(raw)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            }

            // 商品が保存されたあとで別名を永続化する。ここで失敗してもレシートは保存済みなので失敗にしない
            val store = aliasStore
            if (store != null) {
                corrections.forEach { (raw, name, category) ->
                    try {
                        val product = reminderRepository.getOrCreateProduct(name, category)
                        store.learn(raw, name, product.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            }
            trigger.notifyChanged()

            // R-6: 保存後にレシート画像ファイルを削除し、端末内に残さない
            pendingImageUri?.let { uri ->
                ocrScanner?.deleteImage(uri)
            }

            _pendingReceipt.value = null
            pendingImageUri = null
        }
    }

    /** 名前を別名と辞書の完全一致で解決する。一致しなければ cleanText した名前と既定カテゴリ */
    private fun resolveName(input: String): Pair<String, String> {
        val trimmed = input.trim()
        val default = normalizer as? DefaultProductNormalizer ?: return trimmed to "日用品"
        default.resolveExact(trimmed)?.let { return it.normalizedName to it.category }
        return (default.cleanText(trimmed).ifBlank { trimmed }) to "日用品"
    }

    private fun aliasKeyOf(text: String): String =
        (normalizer as? DefaultProductNormalizer)?.aliasKeyOf(text) ?: text.trim()

    /** 保存済みの別名を名寄せ辞書へ読み込む(最初の一度だけ) */
    suspend fun ensureAliasesLoaded() {
        aliasStore?.ensureLoaded()
    }

    /**
     * 予測対象にするかの既定。スキャンと、確認画面で名前を直したときの両方がこの判定を使う(P-3, P-4, F-6)。
     * 未登録なら辞書に一致するか。登録済みなら予測対象のまま、または「もう買わない」の印があるか
     */
    suspend fun defaultTracked(name: String, matchedInDictionary: Boolean): Boolean {
        val trimmed = name.trim()
        val registered = if (trimmed.isEmpty()) null else runCatching {
            reminderRepository.getAllProducts().firstOrNull { it.name == trimmed }
        }.getOrNull()
        return TrackingDefault.isTrackedByDefault(registered, matchedInDictionary)
    }

    /** 名前を直したときに予測対象にするかの目安。スキャンと同じ defaultTracked の判定 (R-4, P-4, F-6) */
    override suspend fun suggestTracked(name: String): Boolean? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        ensureAliasesLoaded()
        // 部分一致では判定しない。入力した名前が別名か辞書に完全一致するときだけ辞書の商品とみなす
        val default = normalizer as? DefaultProductNormalizer
        if (default == null) {
            return defaultTracked(trimmed, normalizer?.normalize(trimmed)?.isMatchedInDictionary == true)
        }
        val resolved = default.resolveExact(trimmed)
        return defaultTracked(
            resolved?.normalizedName ?: default.cleanText(trimmed).ifBlank { trimmed },
            resolved?.isMatchedInDictionary == true
        )
    }

    override suspend fun discardReceipt() {
        // 取り込み中止時も画像を削除し、端末内に残さない (R-6)
        pendingImageUri?.let { uri ->
            ocrScanner?.deleteImage(uri)
        }
        _pendingReceipt.value = null
        pendingImageUri = null
    }

    private fun parseDate(dateString: String): LocalDate? {
        val trimmed = dateString.trim()
        return runCatching { LocalDate.parse(trimmed) }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("uuuu/M/d")) }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("uuuu-M-d")) }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("uuuu/MM/dd")) }.getOrNull()
    }
}

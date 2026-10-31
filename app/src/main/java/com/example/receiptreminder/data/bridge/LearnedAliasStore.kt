package com.example.receiptreminder.data.bridge

import domain.repository.ReminderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import receipt.normalize.DefaultProductNormalizer

/**
 * 確認画面での修正から覚えた別名(P-2)を、メモリ上の名寄せ辞書と Room の間でつなぐ。
 * 読み込みは最初に使うときに一度だけ行うので、アプリの起動処理を待たせない。
 */
class LearnedAliasStore(
    private val reminderRepository: ReminderRepository,
    private val normalizer: DefaultProductNormalizer
) {
    private val mutex = Mutex()
    private var loaded = false

    /** 保存済みの別名を名寄せ辞書へ読み込む。読み込めなかったときは次の機会にやり直す */
    suspend fun ensureLoaded() {
        mutex.withLock {
            if (loaded) return
            try {
                val productsById = reminderRepository.getAllProducts().associateBy { it.id }
                // 同じ照合キーの行が複数あっても、あとから保存した行(id が大きい)が最後に登録されて勝つ
                reminderRepository.getAllAliases().sortedBy { it.id }.forEach { alias ->
                    val product = productsById[alias.productId] ?: return@forEach
                    normalizer.registerAlias(alias.rawText, product.name)
                }
                loaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 別名が読めなくても取り込みは続けられる。次の呼び出しで再試行する
            }
        }
    }

    /**
     * 別名を覚える。メモリ上は即座に、Room へは商品が存在する前提で保存する。
     * Room のキー(rawText)もメモリと同じ照合キーに揃えるので、全半角や容量表記が違う入力は 1 行にまとまる。
     * 以前の版が生の文字列で保存した行のうち同じキーのものは、同じ商品を指すよう更新して、再起動後も最後の修正が勝つようにする。
     */
    suspend fun learn(rawText: String, productName: String, productId: Long) {
        val key = normalizer.aliasKeyOf(rawText)
        if (key.isEmpty() || productName.isBlank()) return
        normalizer.registerAlias(rawText, productName)
        reminderRepository.saveAlias(key, productId)
        reminderRepository.getAllAliases()
            .filter { it.rawText != key && it.productId != productId && normalizer.aliasKeyOf(it.rawText) == key }
            .forEach { reminderRepository.saveAlias(it.rawText, productId) }
    }

    /** 別名をメモリから外す。外した別名の行き先の商品名を返す(なければ null)。Room はそのままにする */
    fun forgetInMemory(rawText: String): String? = normalizer.removeAlias(rawText)

    /** forgetInMemory で外した別名をメモリに戻す */
    fun restoreInMemory(rawText: String, productName: String) = normalizer.registerAlias(rawText, productName)

    /**
     * 別名を Room から消す(P-2: 利用者の取り消し)。以前の版が生の文字列で保存した行も、同じ照合キーなら一緒に消す。
     */
    suspend fun forgetPersisted(rawText: String) {
        val key = normalizer.aliasKeyOf(rawText)
        if (key.isEmpty()) return
        reminderRepository.getAllAliases()
            .filter { normalizer.aliasKeyOf(it.rawText) == key }
            .forEach { reminderRepository.deleteAlias(it.rawText) }
    }

    /** 全データの削除に合わせて、メモリ上と Room の別名をすべて消す */
    suspend fun clear() {
        mutex.withLock {
            normalizer.clearAliases()
            reminderRepository.deleteAllAliases()
            loaded = false
        }
    }
}

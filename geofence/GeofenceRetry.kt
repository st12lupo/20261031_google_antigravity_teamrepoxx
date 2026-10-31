package geofence

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * block を最大 delaysMs.size + 1 回まで試す。失敗するたびに delaysMs の間隔で待つ。
 * 成功したら true、すべて失敗したら false。キャンセルは握りつぶさず投げ直す。
 * 権限がないとき(SecurityException)は待っても直らないので、すぐ諦めて false を返す。
 */
suspend fun retryWithBackoff(
    delaysMs: List<Long>,
    block: suspend () -> Unit
): Boolean {
    var attempt = 0
    while (true) {
        try {
            block()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            return false
        } catch (_: Exception) {
            if (attempt >= delaysMs.size) return false
            delay(delaysMs[attempt])
            attempt++
        }
    }
}

package geofence

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.receiptreminder.ReceiptReminderApplication
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * 再起動・更新後の再登録が一時的に失敗したとき、WorkManager で後から確実にやり直す(N-6)。
 * 権限がないときは待っても直らないので終える(アプリを開いたときに onStart で再登録される)。
 */
class GeofenceResyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? ReceiptReminderApplication)?.container
            ?: return Result.retry()
        val waits = inputData.getInt(KEY_WAITS, 0)
        return decide(runAttemptCount, isDeviceLocationEnabled(applicationContext), waits, applicationContext) {
            container.geofenceManager.recheckAllGeofences()
        }
    }

    companion object {
        const val UNIQUE_NAME = "geofence_resync"
        const val MAX_ATTEMPTS = 6
        const val KEY_WAITS = "waits"

        /** 位置情報オフの間、この間隔で見に来る(固定。WorkManager の backoff は使わない。S-5/N-6) */
        const val WAIT_INTERVAL_MINUTES = 5L

        /** 待つ回数の上限。5 分 x 288 回 = 24 時間。超えたら諦める(アプリを開くと onStart で再登録される) */
        const val MAX_WAITS = 288

        /** 結果の決め方。WorkManager に依存せず確かめられるよう分けてある */
        internal enum class Outcome { Success, Retry, GiveUp, Wait }

        /**
         * 位置情報が無効のあいだは登録しても消えるので、試さず固定間隔(WAIT_INTERVAL_MINUTES)で待つ。
         * 待ちは新しい作業として予約し直すので、失敗の回数(runAttemptCount)には数えない。
         * 復帰直後の 1 回の失敗で諦めることはなく、上限(MAX_WAITS 回)を超えたときだけ諦める。
         */
        internal suspend fun outcomeOf(
            runAttemptCount: Int,
            locationEnabled: Boolean,
            waitCount: Int,
            block: suspend () -> Unit
        ): Outcome =
            if (!locationEnabled) {
                if (waitCount + 1 >= MAX_WAITS) Outcome.GiveUp else Outcome.Wait
            } else {
                attempt(runAttemptCount, block)
            }

        internal suspend fun outcomeOf(
            runAttemptCount: Int,
            locationEnabled: Boolean,
            block: suspend () -> Unit
        ): Outcome = outcomeOf(runAttemptCount, locationEnabled, 0, block)

        internal suspend fun outcomeOf(runAttemptCount: Int, block: suspend () -> Unit): Outcome =
            outcomeOf(runAttemptCount, true, block)

        private suspend fun attempt(runAttemptCount: Int, block: suspend () -> Unit): Outcome =
            try {
                block()
                Outcome.Success
            } catch (e: CancellationException) {
                throw e
            } catch (_: SecurityException) {
                Outcome.Success
            } catch (_: Exception) {
                if (runAttemptCount + 1 >= MAX_ATTEMPTS) Outcome.GiveUp else Outcome.Retry
            }

        private suspend fun decide(
            runAttemptCount: Int,
            locationEnabled: Boolean,
            waits: Int,
            context: Context,
            block: suspend () -> Unit
        ): ListenableWorker.Result =
            when (outcomeOf(runAttemptCount, locationEnabled, waits, block)) {
                Outcome.Wait -> {
                    enqueue(context, waits + 1, WAIT_INTERVAL_MINUTES)
                    ListenableWorker.Result.success()
                }
                Outcome.Success -> ListenableWorker.Result.success()
                Outcome.Retry -> ListenableWorker.Result.retry()
                Outcome.GiveUp -> ListenableWorker.Result.failure()
            }

        fun enqueue(context: Context) = enqueue(context, 0, 0L)

        private fun enqueue(context: Context, waits: Int, delayMinutes: Long) {
            val request = OneTimeWorkRequestBuilder<GeofenceResyncWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_WAITS to waits))
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

package com.clouddock.webdav.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.clouddock.webdav.CloudDockApplication
import com.clouddock.webdav.data.AccountRepository
import com.clouddock.webdav.data.model.WebDavAccount
import com.clouddock.webdav.data.remote.WebDavException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val accountId = inputData.getString(KEY_ACCOUNT_ID) ?: return Result.success()
        val app = applicationContext as? CloudDockApplication ?: return Result.success()
        val account = app.container.accountRepository.find(accountId) ?: return Result.success()

        return try {
            val summary = app.container.syncEngine.sync(account)
            val message = summary.describe()
            app.container.accountRepository.updateSyncInfo(
                account.id,
                System.currentTimeMillis(),
                message
            )
            syncTags(account.id)
            Result.success(workDataOf(KEY_SUMMARY to message))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Background sync failed for ${account.id}", e)
            val message = readableError(e)
            app.container.accountRepository.updateSyncInfo(
                account.id,
                System.currentTimeMillis(),
                "同步失败：$message"
            )
            if (shouldRetry(e) && runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun shouldRetry(error: Throwable): Boolean {
        if (error is IOException) return true
        val status = (error as? WebDavException)?.statusCode ?: return true
        return status >= 500 || status == 408 || status == 429
    }

    private fun readableError(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: "网络或服务器错误"

    private fun syncTags(accountId: String) {
        Log.d(TAG, "Background sync completed for $accountId")
    }

    companion object {
        private const val TAG = "CloudDockWorker"
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_SUMMARY = "summary"

        fun schedule(context: Context, account: WebDavAccount) {
            val workManager = WorkManager.getInstance(context)
            val name = uniqueName(account.id)
            if (account.syncIntervalMinutes <= 0) {
                workManager.cancelUniqueWork(name)
                return
            }
            val interval = account.syncIntervalMinutes.coerceAtLeast(15).toLong()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_ACCOUNT_ID to account.id))
                .addTag(TAG)
                .addTag(account.id)
                .build()
            workManager.enqueueUniquePeriodicWork(
                name,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context, accountId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(accountId))
        }

        fun ensurePeriodicWork(context: Context, repository: AccountRepository) {
            repository.accounts.value.forEach { schedule(context, it) }
        }

        private fun uniqueName(accountId: String) = "clouddock.periodic.$accountId"
    }
}

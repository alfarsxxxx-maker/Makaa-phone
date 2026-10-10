package com.example.sync

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.example.core.AppResult
import com.example.database.MekkaPhoneDatabase
import com.example.repository.MekkaPhoneRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Android-native Background Synchronization Service & Scheduler (Part 3 Rules 12, 13, 14).
 *
 * Implements:
 * - Network constraints (`NETWORK_TYPE_ANY` or `NETWORK_TYPE_UNMETERED` for Wi-Fi only)
 * - Deterministic exponential backoff retry (`BACKOFF_POLICY_EXPONENTIAL`)
 * - Persistent outbox state in Room (`sync_queue`)
 * - Failure recovery (`jobFinished(params, true)` on transient error)
 * - Strict idempotency (no duplicate financial or operational transactions)
 * - Truthful cloud boundary: only transmits when a real remote endpoint URL is configured.
 */
class MekkaBackgroundSyncJobService : JobService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        serviceScope.launch {
            var needsReschedule = false
            try {
                val db = MekkaPhoneDatabase.getInstance(applicationContext)
                val repository = MekkaPhoneRepository(db)
                val config = repository.observeShopConfiguration().first()

                // Truthful Sync Rule (Rule 12 & 13): Never fake cloud sync in background if no remote endpoint is configured.
                if (config.autoSyncEnabled && config.cloudSyncEndpointUrl.isNotBlank()) {
                    val result = repository.runDeltaSyncCycle(
                        isNetworkAvailable = true,
                        simulateTransientFailure = false
                    )
                    if (result is AppResult.Error || (result is AppResult.Success && result.data.retryingCount > 0)) {
                        needsReschedule = true
                    }
                }
            } catch (_: Exception) {
                needsReschedule = true
            } finally {
                jobFinished(params, needsReschedule)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        serviceScope.cancel()
        return true // Reschedule with exponential backoff if interrupted by system constraints
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

object MekkaBackgroundSyncScheduler {
    const val SYNC_JOB_ID = 9301

    /**
     * Schedules or updates the Android JobScheduler background sync task with network & backoff constraints.
     */
    fun scheduleOrUpdateBackgroundSync(
        context: Context,
        policy: BackgroundSyncPolicy
    ): Boolean {
        return try {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                ?: return false

            if (!policy.autoSyncEnabled || !policy.isRemoteEndpointConfigured) {
                scheduler.cancel(SYNC_JOB_ID)
                return false
            }

            val component = ComponentName(context, MekkaBackgroundSyncJobService::class.java)
            val networkType = if (policy.requireUnmeteredWifi) {
                JobInfo.NETWORK_TYPE_UNMETERED
            } else {
                JobInfo.NETWORK_TYPE_ANY
            }

            val jobInfo = JobInfo.Builder(SYNC_JOB_ID, component)
                .setRequiredNetworkType(networkType)
                .setBackoffCriteria(
                    policy.initialBackoffMillis.coerceAtLeast(10_000L),
                    JobInfo.BACKOFF_POLICY_EXPONENTIAL
                )
                .setPeriodic((policy.periodicIntervalMinutes.coerceAtLeast(15) * 60_000L))
                .build()

            scheduler.schedule(jobInfo) == JobScheduler.RESULT_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    fun cancelBackgroundSync(context: Context) {
        runCatching {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            scheduler?.cancel(SYNC_JOB_ID)
        }
    }
}

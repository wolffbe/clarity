package dev.clarity

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * Re-applies the policy every 15 minutes so anything that slipped in (an app
 * installed from Play, a setting nudged) is put back. enforce() is idempotent.
 */
class EnforcementJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        PolicyEnforcer.enforce(this)
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val JOB_ID = 1

        fun schedule(context: Context) {
            val scheduler =
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, EnforcementJob::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build()
            scheduler.schedule(job)
        }

        fun cancel(context: Context) {
            val scheduler =
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            scheduler.cancel(JOB_ID)
        }
    }
}

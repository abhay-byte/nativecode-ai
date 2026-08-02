package com.zenithblue.nativecode.github

import com.zenithblue.nativecode.terminal.ShellJob

/**
 * Cancel handle for an in-flight GitHub connect session.
 * [cancel] kills any active shell job and marks the session cancelled.
 */
class GhAuthSession {
    @Volatile
    var cancelled: Boolean = false
        private set

    @Volatile
    private var job: ShellJob? = null

    fun attach(job: ShellJob) {
        this.job = job
        if (cancelled) job.cancel()
    }

    fun cancel() {
        cancelled = true
        job?.cancel()
        job = null
    }

    fun clearJob() {
        job = null
    }
}

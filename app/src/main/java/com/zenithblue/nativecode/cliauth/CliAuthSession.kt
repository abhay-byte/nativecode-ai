package com.zenithblue.nativecode.cliauth

import com.zenithblue.nativecode.terminal.ShellJob

/** Cancel handle for an in-flight AI CLI auth session. */
class CliAuthSession {
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

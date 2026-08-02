package com.ivarna.nativecode

import java.io.File

enum class CpuMethod {
    PROC_STAT_ROOT,
    PROC_STAT_DIRECT,
    FREQ_BASED,
    UNAVAILABLE
}

data class CpuSnapshot(
    val overallPercent: Float,     // 0.0 - 100.0
    val perCore: Map<Int, Float>,  // coreIndex -> 0.0 - 100.0
    val method: CpuMethod,         // PROC_STAT_ROOT, PROC_STAT_DIRECT, FREQ_BASED, UNAVAILABLE
    val source: String             // "/proc/stat" or "frequency"
)

object CpuUtilizationProvider {
    private var lastReadTimestamp = 0L
    private var cachedSnapshot = CpuSnapshot(0f, emptyMap(), CpuMethod.UNAVAILABLE, "none")

    private var prevTotal = 0L
    private var prevIdle = 0L

    @Synchronized
    fun getCpuSnapshot(): CpuSnapshot {
        val now = System.currentTimeMillis()
        if (now - lastReadTimestamp < 900L && cachedSnapshot.method != CpuMethod.UNAVAILABLE) {
            return cachedSnapshot
        }

        // Approach 1 / 3: Direct /proc/stat read (non-root on <= Android 7 or if readable)
        val procStatSnapshot = tryReadProcStat()
        if (procStatSnapshot != null) {
            lastReadTimestamp = now
            cachedSnapshot = procStatSnapshot
            return procStatSnapshot
        }

        // Approach 2: Frequency-Based Fallback (No-root on Android 8+)
        val freqSnapshot = tryReadFreqBased()
        if (freqSnapshot != null) {
            lastReadTimestamp = now
            cachedSnapshot = freqSnapshot
            return freqSnapshot
        }

        lastReadTimestamp = now
        cachedSnapshot = CpuSnapshot(0f, emptyMap(), CpuMethod.UNAVAILABLE, "none")
        return cachedSnapshot
    }

    private fun tryReadProcStat(): CpuSnapshot? {
        try {
            val file = File("/proc/stat")
            if (!file.canRead() || !file.exists()) return null

            val line = file.bufferedReader().use { it.readLine() } ?: return null
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 8 || parts[0] != "cpu") return null

            val user = parts[1].toLong()
            val nice = parts[2].toLong()
            val system = parts[3].toLong()
            val idle = parts[4].toLong()
            val iowait = parts[5].toLong()
            val irq = parts[6].toLong()
            val softirq = parts[7].toLong()
            val steal = if (parts.size > 8) parts[8].toLong() else 0L

            val currentIdle = idle + iowait
            val currentTotal = user + nice + system + currentIdle + irq + softirq + steal

            val totalDelta = currentTotal - prevTotal
            val idleDelta = currentIdle - prevIdle

            prevTotal = currentTotal
            prevIdle = currentIdle

            if (totalDelta <= 0L) {
                return CpuSnapshot(0f, emptyMap(), CpuMethod.PROC_STAT_DIRECT, "/proc/stat")
            }

            val activeDelta = totalDelta - idleDelta
            val usagePercent = ((activeDelta.toFloat() / totalDelta.toFloat()) * 100f).coerceIn(0f, 100f)

            return CpuSnapshot(usagePercent, emptyMap(), CpuMethod.PROC_STAT_DIRECT, "/proc/stat")
        } catch (e: Exception) {
            return null
        }
    }

    private fun tryReadFreqBased(): CpuSnapshot? {
        try {
            val cpuDir = File("/sys/devices/system/cpu")
            if (!cpuDir.exists() || !cpuDir.isDirectory) return null

            val coreFiles = cpuDir.listFiles { _, name -> name.matches(Regex("cpu[0-9]+")) }
            if (coreFiles.isNullOrEmpty()) return null

            val perCoreMap = mutableMapOf<Int, Float>()
            var totalCoreUsage = 0f
            var activeCores = 0

            for (file in coreFiles) {
                val coreIndex = file.name.substring(3).toIntOrNull() ?: continue
                val cpufreqDir = File(file, "cpufreq")

                val curFreqFile = File(cpufreqDir, "scaling_cur_freq").let {
                    if (it.exists()) it else File(cpufreqDir, "cur_freq")
                }
                val minFreqFile = File(cpufreqDir, "cpuinfo_min_freq")
                val maxFreqFile = File(cpufreqDir, "cpuinfo_max_freq")

                if (!curFreqFile.exists() || !minFreqFile.exists() || !maxFreqFile.exists()) {
                    perCoreMap[coreIndex] = 0f
                    continue
                }

                val cur = curFreqFile.readText().trim().toFloatOrNull() ?: 0f
                val min = minFreqFile.readText().trim().toFloatOrNull() ?: 0f
                val max = maxFreqFile.readText().trim().toFloatOrNull() ?: 0f

                if (cur <= 0f || max <= min || max <= 0f) {
                    perCoreMap[coreIndex] = 0f
                } else {
                    val coreUsage = (((cur - min) / (max - min)) * 100f).coerceIn(0f, 100f)
                    perCoreMap[coreIndex] = coreUsage
                    totalCoreUsage += coreUsage
                    activeCores++
                }
            }

            val numCores = if (activeCores > 0) activeCores else coreFiles.size
            val overall = if (numCores > 0) (totalCoreUsage / numCores).coerceIn(0f, 100f) else 0f

            return CpuSnapshot(overall, perCoreMap, CpuMethod.FREQ_BASED, "frequency")
        } catch (e: Exception) {
            return null
        }
    }
}

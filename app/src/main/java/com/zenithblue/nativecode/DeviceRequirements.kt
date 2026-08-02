package com.zenithblue.nativecode

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import java.io.File

/**
 * Device capability checks for onboarding "Requirements" page.
 *
 * Hard gate: free internal storage > 10 GB.
 * Soft warns: RAM > 7000 MB, swap > 7000 MB.
 * Soft SoC: Snapdragon 8 Gen 2 / Dimensity 9200 / Tensor G3 / Exynos 2200 or newer.
 * SoC ID is best-effort (Build.SOC_*, props, platform codenames) → UNKNOWN when unsure.
 */
object DeviceRequirements {

    private const val TAG = "DeviceRequirements"

    /** Strictly greater than 10 GiB free. */
    const val MIN_FREE_STORAGE_BYTES = 10L * 1024L * 1024L * 1024L

    /** Soft: total RAM must exceed 7000 MiB. */
    const val MIN_RAM_MB = 7000L

    /** Soft: total swap/zram must exceed 7000 MiB. */
    const val MIN_SWAP_MB = 7000L

    enum class Severity { HARD, SOFT }

    enum class Status {
        PASS,
        FAIL,
        WARN,
        UNKNOWN
    }

    data class CheckResult(
        val id: String,
        val title: String,
        val measured: String,
        val requirement: String,
        val status: Status,
        val severity: Severity,
        val detail: String
    )

    data class Snapshot(
        val freeStorageBytes: Long,
        val totalStorageBytes: Long,
        val ramTotalMb: Long,
        val swapTotalMb: Long,
        val socLabel: String,
        val socRaw: String,
        val socMeetsMinimum: Boolean?,
        val checks: List<CheckResult>
    ) {
        val hardBlocked: Boolean get() = checks.any { it.severity == Severity.HARD && it.status == Status.FAIL }
        val hasSoftWarnings: Boolean get() = checks.any {
            it.severity == Severity.SOFT && (it.status == Status.WARN || it.status == Status.UNKNOWN)
        }
    }

    fun evaluate(context: Context): Snapshot {
        val storage = readStorage(context)
        val mem = readMemory(context)
        val soc = identifySoc()

        val storageStatus = if (storage.freeBytes > MIN_FREE_STORAGE_BYTES) Status.PASS else Status.FAIL
        val ramStatus = when {
            mem.ramTotalMb <= 0L -> Status.UNKNOWN
            mem.ramTotalMb > MIN_RAM_MB -> Status.PASS
            else -> Status.WARN
        }
        val swapStatus = when {
            mem.swapTotalMb <= 0L -> Status.WARN // no/little swap reported
            mem.swapTotalMb > MIN_SWAP_MB -> Status.PASS
            else -> Status.WARN
        }
        val cpuStatus = when (soc.meetsMinimum) {
            true -> Status.PASS
            false -> Status.WARN
            null -> Status.UNKNOWN
        }

        val checks = listOf(
            CheckResult(
                id = "storage",
                title = "Storage",
                measured = formatBytes(storage.freeBytes) + " free",
                requirement = "> 10 GB free",
                status = storageStatus,
                severity = Severity.HARD,
                detail = if (storageStatus == Status.PASS) {
                    "Enough free space for rootfs + packages."
                } else {
                    "Need more than 10 GB free internal storage to continue."
                }
            ),
            CheckResult(
                id = "ram",
                title = "RAM",
                measured = if (mem.ramTotalMb > 0) "${mem.ramTotalMb} MB total" else "unavailable",
                requirement = "> 7000 MB (~8 GB)",
                status = ramStatus,
                severity = Severity.SOFT,
                detail = when (ramStatus) {
                    Status.PASS -> "Memory headroom looks healthy."
                    Status.WARN -> "Below ~8 GB — heavy AI/IDE workloads may thrash."
                    else -> "Could not read total RAM."
                }
            ),
            CheckResult(
                id = "swap",
                title = "Swap / zRAM",
                measured = if (mem.swapTotalMb > 0) "${mem.swapTotalMb} MB total" else "0 MB / none",
                requirement = "> 7000 MB (~8 GB)",
                status = swapStatus,
                severity = Severity.SOFT,
                detail = when (swapStatus) {
                    Status.PASS -> "Swap/zRAM capacity is adequate."
                    Status.WARN -> "Low swap — enable large zRAM if the ROM allows; experience may stutter."
                    else -> "Could not read SwapTotal."
                }
            ),
            CheckResult(
                id = "cpu",
                title = "CPU / SoC",
                measured = soc.displayName,
                requirement = "SD 8 Gen2 / D9200 / Tensor G3 / Exynos 2200+",
                status = cpuStatus,
                severity = Severity.SOFT,
                detail = when (cpuStatus) {
                    Status.PASS -> "Meets recommended chipset floor."
                    Status.WARN -> "Below recommended SoC — expect slower builds/AI."
                    Status.UNKNOWN -> "SoC not identified (Android often hides marketing names). Proceed at your own risk."
                    Status.FAIL -> "SoC not identified."
                }
            )
        )

        return Snapshot(
            freeStorageBytes = storage.freeBytes,
            totalStorageBytes = storage.totalBytes,
            ramTotalMb = mem.ramTotalMb,
            swapTotalMb = mem.swapTotalMb,
            socLabel = soc.displayName,
            socRaw = soc.rawSignals,
            socMeetsMinimum = soc.meetsMinimum,
            checks = checks
        )
    }

    // ── Storage ──────────────────────────────────────────────────────────────

    private data class StorageInfo(val freeBytes: Long, val totalBytes: Long)

    private fun readStorage(context: Context): StorageInfo {
        val candidates = listOf(
            runCatching { Environment.getDataDirectory() }.getOrNull(),
            context.filesDir,
            context.getExternalFilesDir(null)
        ).filterNotNull()

        var bestFree = 0L
        var bestTotal = 0L
        for (dir in candidates) {
            try {
                val path = dir.absolutePath
                if (path.isBlank()) continue
                val stat = StatFs(path)
                val free = stat.availableBlocksLong * stat.blockSizeLong
                val total = stat.blockCountLong * stat.blockSizeLong
                if (free > bestFree) {
                    bestFree = free
                    bestTotal = total
                }
            } catch (e: Exception) {
                Log.w(TAG, "StatFs failed for $dir: ${e.message}")
            }
        }
        return StorageInfo(bestFree, bestTotal)
    }

    // ── Memory ───────────────────────────────────────────────────────────────

    private data class MemInfo(val ramTotalMb: Long, val swapTotalMb: Long)

    private fun readMemory(context: Context): MemInfo {
        var ramTotalKb = 0L
        var swapTotalKb = 0L

        try {
            val file = File("/proc/meminfo")
            if (file.canRead()) {
                file.forEachLine { line ->
                    when {
                        line.startsWith("MemTotal:") ->
                            ramTotalKb = line.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() ?: ramTotalKb
                        line.startsWith("SwapTotal:") ->
                            swapTotalKb = line.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() ?: swapTotalKb
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "meminfo read failed: ${e.message}")
        }

        if (ramTotalKb <= 0L) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                if (am != null) {
                    val mi = ActivityManager.MemoryInfo()
                    am.getMemoryInfo(mi)
                    ramTotalKb = mi.totalMem / 1024
                }
            } catch (e: Exception) {
                Log.w(TAG, "MemoryInfo failed: ${e.message}")
            }
        }

        return MemInfo(ramTotalMb = ramTotalKb / 1024, swapTotalMb = swapTotalKb / 1024)
    }

    // ── SoC ──────────────────────────────────────────────────────────────────

    private data class SocId(
        val displayName: String,
        val meetsMinimum: Boolean?,
        val rawSignals: String
    )

    /**
     * Tier: null = unknown, false = known below floor, true = meets/exceeds floor.
     * Floor ≈ Snapdragon 8 Gen 2 / Dimensity 9200 / Tensor G3 / Exynos 2200.
     */
    private fun identifySoc(): SocId {
        val parts = mutableListOf<String>()
        fun add(label: String, value: String?) {
            val v = value?.trim().orEmpty()
            if (v.isNotEmpty()) parts += "$label=$v"
        }

        add("HARDWARE", Build.HARDWARE)
        add("BOARD", Build.BOARD)
        add("DEVICE", Build.DEVICE)
        add("PRODUCT", Build.PRODUCT)
        add("MANUFACTURER", Build.MANUFACTURER)
        add("BRAND", Build.BRAND)
        add("MODEL", Build.MODEL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add("SOC_MANUFACTURER", Build.SOC_MANUFACTURER)
            add("SOC_MODEL", Build.SOC_MODEL)
        }

        val propKeys = listOf(
            "ro.soc.model",
            "ro.soc.manufacturer",
            "ro.board.platform",
            "ro.hardware",
            "ro.hardware.chipname",
            "ro.chipname",
            "ro.product.board",
            "ro.mediatek.platform",
            "ro.boot.hardware.revision"
        )
        val props = linkedMapOf<String, String>()
        for (key in propKeys) {
            val v = sysProp(key)
            if (v.isNotEmpty()) {
                props[key] = v
                add(key, v)
            }
        }

        val raw = parts.joinToString(" ")
        val blob = raw.lowercase()

        // Prefer marketing name from SOC_MODEL / ro.soc.model
        val socModel = listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null,
            props["ro.soc.model"]
        ).map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

        val platform = listOfNotNull(
            props["ro.board.platform"],
            props["ro.mediatek.platform"],
            Build.HARDWARE,
            props["ro.hardware"],
            props["ro.hardware.chipname"],
            props["ro.chipname"],
            Build.BOARD
        ).joinToString(" ").lowercase()

        val match = matchKnownSoc(socModel, platform, blob)
        if (match != null) {
            Log.i(TAG, "SoC → ${match.displayName} meets=${match.meetsMinimum} raw=$raw")
            return match.copy(rawSignals = raw)
        }

        Log.i(TAG, "SoC → UNKNOWN raw=$raw")
        return SocId(displayName = "Unknown", meetsMinimum = null, rawSignals = raw)
    }

    private fun matchKnownSoc(socModel: String, platform: String, blob: String): SocId? {
        val m = socModel.lowercase().trim()
        val p = platform.lowercase()
        val b = blob

        // ── Google Tensor ────────────────────────────────────────────────────
        when {
            m.contains("tensor g5") || b.contains("tensor g5") || p.contains("lise") ->
                return SocId("Google Tensor G5", true, "")
            m.contains("tensor g4") || b.contains("tensor g4") || p.contains("zumapro") || p.contains("gs401") ->
                return SocId("Google Tensor G4", true, "")
            m.contains("tensor g3") || b.contains("tensor g3") || p.contains("zuma") || p.contains("gs301") ->
                return SocId("Google Tensor G3", true, "")
            m.contains("tensor g2") || b.contains("tensor g2") || p.contains("cloudripper") || p.contains("gs201") ->
                return SocId("Google Tensor G2", false, "")
            m.contains("tensor") && (m.contains("g1") || p.contains("slider") || p.contains("gs101")) ->
                return SocId("Google Tensor G1", false, "")
            m.contains("tensor") ->
                return SocId(socModel.ifBlank { "Google Tensor" }, null, "")
        }

        // ── Qualcomm Snapdragon (marketing name first) ───────────────────────
        val sdMarketing = parseSnapdragonMarketing(m.ifBlank { b })
        if (sdMarketing != null) return sdMarketing

        // Platform / part codes
        val qcom = matchQualcommPlatform(p, b)
        if (qcom != null) return qcom

        // ── MediaTek Dimensity / Helio ───────────────────────────────────────
        val mtkMarketing = parseDimensityMarketing(m.ifBlank { b })
        if (mtkMarketing != null) return mtkMarketing
        val mtk = matchMediatekPlatform(p, b, m)
        if (mtk != null) return mtk

        // ── Samsung Exynos ───────────────────────────────────────────────────
        val exy = matchExynos(m, p, b)
        if (exy != null) return exy

        // Generic Snapdragon string without gen
        if (m.contains("snapdragon") || b.contains("snapdragon")) {
            return SocId(socModel.ifBlank { "Snapdragon (unparsed)" }, null, "")
        }
        if (m.contains("dimensity") || b.contains("dimensity")) {
            return SocId(socModel.ifBlank { "Dimensity (unparsed)" }, null, "")
        }
        if (m.contains("exynos") || b.contains("exynos")) {
            return SocId(socModel.ifBlank { "Exynos (unparsed)" }, null, "")
        }

        return null
    }

    private fun parseSnapdragonMarketing(text: String): SocId? {
        val t = text.lowercase()
        // Elite / 8s / Gen order matters (longest match)
        val patterns = listOf(
            Regex("""snapdragon\s*8\s*elite""") to ("Snapdragon 8 Elite" to true),
            Regex("""snapdragon\s*8s?\s*gen\s*4""") to ("Snapdragon 8 Gen 4" to true),
            Regex("""snapdragon\s*8s\s*gen\s*3""") to ("Snapdragon 8s Gen 3" to true),
            Regex("""snapdragon\s*8\+\s*gen\s*3""") to ("Snapdragon 8+ Gen 3" to true),
            Regex("""snapdragon\s*8\s*gen\s*3""") to ("Snapdragon 8 Gen 3" to true),
            Regex("""snapdragon\s*8\+\s*gen\s*2""") to ("Snapdragon 8+ Gen 2" to true),
            Regex("""snapdragon\s*8\s*gen\s*2""") to ("Snapdragon 8 Gen 2" to true),
            Regex("""snapdragon\s*8\+\s*gen\s*1""") to ("Snapdragon 8+ Gen 1" to false),
            Regex("""snapdragon\s*8\s*gen\s*1""") to ("Snapdragon 8 Gen 1" to false),
            Regex("""snapdragon\s*888\+?""") to ("Snapdragon 888" to false),
            Regex("""snapdragon\s*870""") to ("Snapdragon 870" to false),
            Regex("""snapdragon\s*865\+?""") to ("Snapdragon 865" to false),
            Regex("""snapdragon\s*7\+?\s*gen\s*[1-4]""") to ("Snapdragon 7-series" to false),
            Regex("""snapdragon\s*6\s*gen""") to ("Snapdragon 6-series" to false),
            Regex("""snapdragon\s*4\s*gen""") to ("Snapdragon 4-series" to false)
        )
        for ((re, pair) in patterns) {
            if (re.containsMatchIn(t)) return SocId(pair.first, pair.second, "")
        }
        // SM part numbers sometimes appear as model
        val sm = Regex("""\bsm(\d{4})\b""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        if (sm != null) return smPartToSoc(sm)
        return null
    }

    private fun smPartToSoc(sm: Int): SocId? {
        // Rough SM ladder: 8550 = 8 Gen 2, 8450 = 8 Gen 1, 8650 = 8 Gen 3, 8750 = 8 Elite
        return when {
            sm >= 8750 -> SocId("Snapdragon 8 Elite (SM$sm)", true, "")
            sm >= 8650 -> SocId("Snapdragon 8 Gen 3 (SM$sm)", true, "")
            sm >= 8635 -> SocId("Snapdragon 8s Gen 3 (SM$sm)", true, "")
            sm >= 8550 -> SocId("Snapdragon 8 Gen 2 (SM$sm)", true, "")
            sm >= 8475 -> SocId("Snapdragon 8+ Gen 1 (SM$sm)", false, "")
            sm >= 8450 -> SocId("Snapdragon 8 Gen 1 (SM$sm)", false, "")
            sm >= 8350 -> SocId("Snapdragon 888-class (SM$sm)", false, "")
            sm >= 8250 -> SocId("Snapdragon 865-class (SM$sm)", false, "")
            sm in 7000..7999 -> SocId("Snapdragon 7-series (SM$sm)", false, "")
            sm in 6000..6999 -> SocId("Snapdragon 6-series (SM$sm)", false, "")
            else -> null
        }
    }

    private fun matchQualcommPlatform(platform: String, blob: String): SocId? {
        val p = "$platform $blob"
        // Codename → (label, meets)
        val map = listOf(
            // Meets floor (8 Gen 2+)
            listOf("sm8750", "sun", "canoe") to ("Snapdragon 8 Elite" to true),
            listOf("sm8650", "pineapple") to ("Snapdragon 8 Gen 3" to true),
            listOf("sm8635", "lanai") to ("Snapdragon 8s Gen 3" to true),
            listOf("sm8550", "kalama") to ("Snapdragon 8 Gen 2" to true),
            // Below floor
            listOf("sm8475", "waipio") to ("Snapdragon 8+ Gen 1" to false),
            listOf("sm8450", "taro") to ("Snapdragon 8 Gen 1" to false),
            listOf("lahaina", "sm8350") to ("Snapdragon 888" to false),
            listOf("kona", "sm8250") to ("Snapdragon 865" to false),
            listOf("msmnile", "sm8150") to ("Snapdragon 855" to false),
            listOf("lito", "sm7250") to ("Snapdragon 765G-class" to false),
            listOf("bengal", "holi", "blair", "parrot", "ravelin") to ("Snapdragon mid-range" to false)
        )
        for ((keys, pair) in map) {
            if (keys.any { p.contains(it) }) return SocId(pair.first, pair.second, "")
        }
        // SM#### anywhere in blob
        val sm = Regex("""\bsm(\d{4})\b""").find(p)?.groupValues?.get(1)?.toIntOrNull()
        if (sm != null) return smPartToSoc(sm)
        return null
    }

    private fun parseDimensityMarketing(text: String): SocId? {
        val t = text.lowercase()
        val re = Regex("""dimensity\s*(\d{4})\s*([a-z+]*)""")
        val m = re.find(t) ?: return null
        val num = m.groupValues[1].toIntOrNull() ?: return null
        val suffix = m.groupValues[2]
        val label = "Dimensity $num${if (suffix.isNotEmpty()) " $suffix" else ""}".trim()
        // 9200 floor
        return SocId(label, num >= 9200, "")
    }

    private fun matchMediatekPlatform(platform: String, blob: String, socModel: String): SocId? {
        val p = "$platform $blob $socModel".lowercase()
        // MT part → (label, meets)  — Dimensity 9200 = MT6985
        val map = listOf(
            listOf("mt6991", "mt6993") to ("Dimensity 9400-class" to true),
            listOf("mt6989") to ("Dimensity 9300" to true),
            listOf("mt6985") to ("Dimensity 9200" to true),
            listOf("mt6983") to ("Dimensity 9000" to false),
            listOf("mt6897") to ("Dimensity 8300" to false),
            listOf("mt6896") to ("Dimensity 8200" to false),
            listOf("mt6895") to ("Dimensity 8100" to false),
            listOf("mt6886") to ("Dimensity 7200" to false),
            listOf("mt6877", "mt6833", "mt676") to ("MediaTek mid-range" to false)
        )
        for ((keys, pair) in map) {
            if (keys.any { p.contains(it) }) return SocId(pair.first, pair.second, "")
        }
        val mt = Regex("""\bmt(\d{4})\b""").find(p)?.groupValues?.get(1)?.toIntOrNull()
        if (mt != null) {
            // Heuristic: MT698x high-end; 6985+ meets
            val meets = mt >= 6985
            val known = mt >= 6800
            if (known) return SocId("MediaTek MT$mt", meets, "")
        }
        return null
    }

    private fun matchExynos(socModel: String, platform: String, blob: String): SocId? {
        val t = "$socModel $platform $blob".lowercase()
        // Marketing numbers
        val gen = Regex("""exynos\s*(\d{4})""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        if (gen != null) {
            return SocId("Exynos $gen", gen >= 2200, "")
        }
        // Internal s5e codes
        when {
            t.contains("s5e9955") || t.contains("s5e9945") ->
                return SocId("Exynos 2400-class", true, "")
            t.contains("s5e9925") ->
                return SocId("Exynos 2200", true, "")
            t.contains("s5e9840") || t.contains("exynos2100") || t.contains("s5e9830") ->
                return SocId("Exynos 2100 or older", false, "")
            t.contains("xclipse") && (t.contains("940") || t.contains("920")) ->
                return SocId("Exynos (Xclipse 9xx)", true, "")
            t.contains("xclipse") ->
                return SocId("Exynos (Xclipse)", null, "")
            t.contains("exynos") ->
                return SocId(socModel.ifBlank { "Exynos (unparsed)" }, null, "")
        }
        return null
    }

    private fun sysProp(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java, String::class.java)
            (get.invoke(null, key, "") as? String).orEmpty().trim()
        } catch (_: Throwable) {
            try {
                val p = ProcessBuilder("getprop", key)
                    .redirectErrorStream(true)
                    .start()
                p.inputStream.bufferedReader().use { it.readText() }.trim()
            } catch (_: Throwable) {
                ""
            }
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            String.format(java.util.Locale.US, "%.1f GB", gb)
        } else {
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            String.format(java.util.Locale.US, "%.0f MB", mb)
        }
    }
}

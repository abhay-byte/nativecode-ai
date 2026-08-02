package com.zenithblue.nativecode.marketplace

/** Package kind: component = runtime/lib; app = launchable (often X11). */
enum class PackageKind {
    COMPONENT,
    APP;

    companion object {
        fun from(raw: String?): PackageKind =
            if (raw.equals("app", ignoreCase = true)) APP else COMPONENT
    }
}

data class MpCategory(
    val id: String,
    val title: String,
    val order: Int,
    val description: String = ""
)

data class MpLaunch(
    val type: String = "x11",
    val command: String,
    val workdir: String = "/home/flux",
    val needsDisplay: Boolean = true
)

data class MpPackage(
    val id: String,
    val name: String,
    val kind: PackageKind,
    val category: String,
    val summary: String,
    val description: String,
    val version: String,
    val arch: List<String>,
    val env: List<String>,
    val deps: List<String>,
    val aptProvides: List<String>,
    val sizeHintMb: Int,
    val scriptPath: String,
    val experimental: Boolean = false,
    val launch: MpLaunch? = null,
    /** Optional catalog icon path (repo-relative or https). Falls back to bundled mark. */
    val icon: String? = null
) {
    fun supportsEnv(method: String): Boolean =
        env.isEmpty() || env.any { it.equals(method, ignoreCase = true) }
}

data class MpCatalog(
    val schemaVersion: Int,
    val generatedAt: String,
    val minAppVersion: Int,
    val categories: List<MpCategory>,
    val packages: List<MpPackage>
) {
    fun packageById(id: String): MpPackage? = packages.find { it.id == id }

    fun categoriesSorted(): List<MpCategory> = categories.sortedBy { it.order }
}

/** Local registry entry for a marketplace install. */
data class RegistryEntry(
    val id: String,
    val env: String,
    val kind: PackageKind,
    val version: String,
    val installedAt: String,
    val sizeBytes: Long,
    val aptProvides: List<String>,
    val paths: List<String> = emptyList(),
    val launchCommand: String? = null,
    val launchType: String? = null,
    val source: String = "marketplace",
    val state: String = "installed"
) {
    val key: String get() = InstallRegistry.key(env, id)
    val isApp: Boolean get() = kind == PackageKind.APP
}

/** One dpkg-installed package. */
data class AptPackage(
    val name: String,
    val version: String,
    val sizeBytes: Long,
    val section: String,
    val status: String
)

data class AptCategoryGroup(
    val title: String,
    val sectionKey: String,
    val packages: List<AptPackage>
) {
    val totalBytes: Long get() = packages.sumOf { it.sizeBytes }
}

sealed class InstallEvent {
    data class Log(val line: String) : InstallEvent()
    data class Progress(val status: String) : InstallEvent()
    data class Size(val bytes: Long) : InstallEvent()
    data class Paths(val paths: List<String>) : InstallEvent()
    data class Finished(val success: Boolean, val exitCode: Int, val sizeBytes: Long, val paths: List<String>) :
        InstallEvent()
}

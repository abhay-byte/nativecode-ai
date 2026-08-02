package com.zenithblue.nativecode.marketplace

import android.content.Context
import com.zenithblue.nativecode.terminal.LinuxCommandBuilder

/**
 * Installed launchable apps for the Apps page.
 * Primary source: InstallRegistry kind=app for current env (not full apt / SM).
 */
data class AppListItem(
    val id: String,
    val title: String,
    val summary: String,
    val version: String,
    val sizeBytes: Long,
    val launchCommand: String?,
    val launchType: String?,
    val source: String,
    val registry: RegistryEntry,
    val catalog: MpPackage?
)

object AppsInventory {

    fun listInstalledApps(
        ctx: Context,
        method: String = LinuxCommandBuilder.currentMethod,
        catalog: MpCatalog? = null
    ): List<AppListItem> {
        val env = if (method == "chroot") "chroot" else "proot"
        return InstallRegistry.forEnv(ctx, env)
            .filter { it.state == "installed" && it.kind == PackageKind.APP }
            .sortedBy { it.id.lowercase() }
            .map { e ->
                val pkg = catalog?.packageById(e.id)
                AppListItem(
                    id = e.id,
                    title = pkg?.name?.takeIf { it.isNotBlank() } ?: e.id,
                    summary = pkg?.summary?.takeIf { it.isNotBlank() }
                        ?: pkg?.description?.takeIf { it.isNotBlank() }
                        ?: e.launchCommand.orEmpty(),
                    version = e.version.ifBlank { pkg?.version.orEmpty() },
                    sizeBytes = e.sizeBytes,
                    launchCommand = e.launchCommand ?: pkg?.launch?.command,
                    launchType = e.launchType ?: pkg?.launch?.type,
                    source = e.source,
                    registry = e,
                    catalog = pkg
                )
            }
    }
}

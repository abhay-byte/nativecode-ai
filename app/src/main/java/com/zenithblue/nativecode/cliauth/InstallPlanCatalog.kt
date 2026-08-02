package com.ivarna.nativecode.cliauth

/**
 * Inventory lines for onboarding install-plan page (C6).
 * Guest Debian only — not host APK updates.
 */
object InstallPlanCatalog {

    data class InventoryLine(val label: String, val detail: String)

    fun baseInventory(method: String): List<InventoryLine> =
        if (method == "chroot") {
            listOf(
                InventoryLine("Root access", "KernelSU / Magisk"),
                InventoryLine("Debian 13 chroot base", "setup_debian13_chroot.sh"),
                InventoryLine("Guest packages / users", "setup_debian_family.sh"),
                InventoryLine("Hardware acceleration", "setup_hw_accel_debian.sh")
            )
        } else {
            listOf(
                InventoryLine("Host bootstrap", "bootstrap.tar + host configs"),
                InventoryLine("Debian proot guest", "flux_install / proot-distro"),
                InventoryLine("Guest packages / users", "setup_debian_family.sh"),
                InventoryLine("Hardware acceleration", "setup_hw_accel_debian.sh")
            )
        }

    fun customizationInventory(): List<InventoryLine> = listOf(
        InventoryLine("Desktop themes & shell polish", "setup_customization_debian.sh")
    )

    fun aiCliInventory(): List<InventoryLine> {
        val head = listOf(
            InventoryLine("Node.js / NVM", "setup_cli_tools.sh (guest only)")
        )
        val tools = CliToolCatalog.ALL.map {
            InventoryLine(it.displayName, "bin: ${it.bin}")
        }
        return head + tools
    }
}

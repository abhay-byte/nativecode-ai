package com.zenithblue.nativecode.marketplace

import android.content.Context
import java.io.File

object MarketplacePaths {
    const val OWNER = "abhay-byte"
    const val REPO = "nativecode-marketplace"
    const val REF = "main"

    const val RAW_BASE =
        "https://raw.githubusercontent.com/$OWNER/$REPO/$REF"

    fun catalogUrl(): String = "$RAW_BASE/catalog.json"

    fun rawUrl(relativePath: String): String =
        "$RAW_BASE/${relativePath.trimStart('/')}"

    fun root(ctx: Context): File =
        File(ctx.filesDir, "marketplace").also { it.mkdirs() }

    fun cacheDir(ctx: Context): File =
        File(root(ctx), "cache").also { it.mkdirs() }

    fun catalogCache(ctx: Context): File =
        File(cacheDir(ctx), "catalog.json")

    fun packageCacheDir(ctx: Context, id: String): File =
        File(cacheDir(ctx), "packages/$id").also { it.mkdirs() }

    fun libCacheDir(ctx: Context): File =
        File(cacheDir(ctx), "lib").also { it.mkdirs() }

    fun registryFile(ctx: Context): File =
        File(root(ctx), "registry.json")

    fun inventoryCache(ctx: Context, env: String): File =
        File(root(ctx), "inventory-$env.json")
}

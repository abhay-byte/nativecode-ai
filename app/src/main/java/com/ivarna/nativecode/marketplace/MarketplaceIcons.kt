package com.ivarna.nativecode.marketplace

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.ivarna.nativecode.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Package icons:
 * - catalog [MpPackage.icon] (https or repo-relative) when set
 * - else known bundled marks (blender / box64)
 * - else generic fallback [R.drawable.mp_ic_package]
 */
object MarketplaceIcons {
    private val io = Executors.newSingleThreadExecutor()

    /** Only packages with a real art asset — others use fallback. */
    fun bundledRes(pkgId: String): Int = when (pkgId.lowercase()) {
        "blender" -> R.drawable.mp_ic_blender
        "box64" -> R.drawable.mp_ic_box64
        else -> R.drawable.mp_ic_package
    }

    fun bind(view: ImageView, pkg: MpPackage) {
        // Always show something immediately (bundled or generic fallback)
        view.setImageResource(bundledRes(pkg.id))
        val path = pkg.icon?.trim().orEmpty()
        if (path.isEmpty()) return
        val ctx = view.context.applicationContext
        val cache = iconCacheFile(ctx, pkg.id, path)
        if (cache.isFile && cache.length() > 32) {
            applyBitmap(view, cache)
            return
        }
        io.execute {
            try {
                val url = if (path.startsWith("http://") || path.startsWith("https://")) {
                    path
                } else {
                    MarketplacePaths.rawUrl(path)
                }
                downloadTo(url, cache)
                if (cache.isFile && cache.length() > 32) {
                    view.post { applyBitmap(view, cache) }
                }
            } catch (_: Exception) { /* keep bundled / fallback */ }
        }
    }

    private fun applyBitmap(view: ImageView, file: File) {
        try {
            val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return
            val d: Drawable = BitmapDrawable(view.resources, bmp)
            view.setImageDrawable(d)
        } catch (_: Exception) { /* ignore */ }
    }

    private fun iconCacheFile(ctx: Context, id: String, pathOrUrl: String): File {
        val tag = Integer.toHexString(pathOrUrl.hashCode())
        return File(MarketplacePaths.cacheDir(ctx), "icons/${id}_$tag.png").also {
            it.parentFile?.mkdirs()
        }
    }

    private fun downloadTo(urlStr: String, dest: File) {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 NativeCode/1.0")
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) return
            dest.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            conn.disconnect()
        }
    }
}

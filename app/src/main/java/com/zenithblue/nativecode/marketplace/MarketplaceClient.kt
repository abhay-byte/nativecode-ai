package com.zenithblue.nativecode.marketplace

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Fetch + cache catalog.json and package scripts from GitHub raw. */
object MarketplaceClient {
    private const val TAG = "MarketplaceClient"
    private val CACHE_MAX_AGE_MS = TimeUnit.HOURS.toMillis(6)

    fun loadCatalog(ctx: Context, forceRefresh: Boolean = false): Result<MpCatalog> {
        val cache = MarketplacePaths.catalogCache(ctx)
        val freshEnough = cache.isFile &&
            (System.currentTimeMillis() - cache.lastModified()) < CACHE_MAX_AGE_MS
        if (!forceRefresh && freshEnough) {
            return parseCatalog(cache.readText())
        }
        return try {
            val body = httpGet(MarketplacePaths.catalogUrl())
            cache.parentFile?.mkdirs()
            cache.writeText(body)
            parseCatalog(body)
        } catch (e: Exception) {
            Log.w(TAG, "catalog fetch failed", e)
            if (cache.isFile) parseCatalog(cache.readText())
            else Result.failure(e)
        }
    }

    fun lastSyncMs(ctx: Context): Long {
        val f = MarketplacePaths.catalogCache(ctx)
        return if (f.isFile) f.lastModified() else 0L
    }

    /**
     * Download package install/uninstall + shared lib into cache.
     * Returns guest-staging ready directory under cache/packages/<id>.
     */
    fun ensurePackageScripts(ctx: Context, pkg: MpPackage): Result<File> {
        return try {
            val dir = MarketplacePaths.packageCacheDir(ctx, pkg.id)
            val base = pkg.scriptPath.trimEnd('/')
            downloadTo(MarketplacePaths.rawUrl("$base/install.sh"), File(dir, "install.sh"))
            downloadTo(MarketplacePaths.rawUrl("$base/uninstall.sh"), File(dir, "uninstall.sh"))
            // package.json optional
            try {
                downloadTo(MarketplacePaths.rawUrl("$base/package.json"), File(dir, "package.json"))
            } catch (_: Exception) { /* optional */ }

            val libDir = MarketplacePaths.libCacheDir(ctx)
            downloadTo(
                MarketplacePaths.rawUrl("lib/nc_mp_common.sh"),
                File(libDir, "nc_mp_common.sh")
            )
            Result.success(dir)
        } catch (e: Exception) {
            Log.e(TAG, "script download failed for ${pkg.id}", e)
            Result.failure(e)
        }
    }

    private fun downloadTo(url: String, dest: File) {
        val body = httpGet(url)
        dest.parentFile?.mkdirs()
        dest.writeText(body)
        dest.setExecutable(true)
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "NativeCode/1.0")
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code for $urlStr: ${body.take(200)}")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    fun parseCatalog(json: String): Result<MpCatalog> = try {
        val root = JSONObject(json)
        val cats = mutableListOf<MpCategory>()
        val catArr = root.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until catArr.length()) {
            val o = catArr.getJSONObject(i)
            cats.add(
                MpCategory(
                    id = o.getString("id"),
                    title = o.optString("title", o.getString("id")),
                    order = o.optInt("order", 100),
                    description = o.optString("description", "")
                )
            )
        }
        val pkgs = mutableListOf<MpPackage>()
        val pkgArr = root.optJSONArray("packages") ?: JSONArray()
        for (i in 0 until pkgArr.length()) {
            val o = pkgArr.getJSONObject(i)
            val launchObj = o.optJSONObject("launch")
            val launch = if (launchObj != null) {
                MpLaunch(
                    type = launchObj.optString("type", "x11"),
                    command = launchObj.optString("command", ""),
                    workdir = launchObj.optString("workdir", "/home/flux"),
                    needsDisplay = launchObj.optBoolean("needs_display", true)
                ).takeIf { it.command.isNotBlank() }
            } else null
            pkgs.add(
                MpPackage(
                    id = o.getString("id"),
                    name = o.optString("name", o.getString("id")),
                    kind = PackageKind.from(o.optString("kind")),
                    category = o.optString("category", "other"),
                    summary = o.optString("summary", ""),
                    description = o.optString("description", ""),
                    version = o.optString("version", ""),
                    arch = stringList(o.optJSONArray("arch")),
                    env = stringList(o.optJSONArray("env")),
                    deps = stringList(o.optJSONArray("deps")),
                    aptProvides = stringList(o.optJSONArray("apt_provides")),
                    sizeHintMb = o.optInt("size_hint_mb", 0),
                    scriptPath = o.optString("script_path", "packages/${o.getString("id")}"),
                    experimental = o.optBoolean("experimental", false),
                    launch = launch,
                    // Only when catalog sets icon — never invent path; UI uses generic fallback
                    icon = o.optString("icon", "").ifBlank { null }
                )
            )
        }
        Result.success(
            MpCatalog(
                schemaVersion = root.optInt("schema_version", 1),
                generatedAt = root.optString("generated_at", ""),
                minAppVersion = root.optInt("min_app_version", 1),
                categories = cats,
                packages = pkgs
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun stringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotBlank()) add(s)
            }
        }
    }
}

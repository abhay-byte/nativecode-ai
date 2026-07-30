package com.ivarna.nativecode.marketplace

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/** Persist marketplace installs per isolation env (proot|chroot). */
object InstallRegistry {

    fun key(env: String, id: String): String = "$env:$id"

    fun load(ctx: Context): MutableMap<String, RegistryEntry> {
        val file = MarketplacePaths.registryFile(ctx)
        val map = ConcurrentHashMap<String, RegistryEntry>()
        if (!file.isFile) return map
        return try {
            val root = JSONObject(file.readText())
            val entries = root.optJSONObject("entries") ?: return map
            val keys = entries.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = entries.getJSONObject(k)
                val e = parseEntry(o) ?: continue
                map[e.key] = e
            }
            map
        } catch (_: Exception) {
            ConcurrentHashMap()
        }
    }

    fun save(ctx: Context, map: Map<String, RegistryEntry>) {
        val entries = JSONObject()
        map.values.forEach { e ->
            entries.put(e.key, toJson(e))
        }
        val root = JSONObject()
            .put("schema_version", 1)
            .put("entries", entries)
        val file = MarketplacePaths.registryFile(ctx)
        file.parentFile?.mkdirs()
        file.writeText(root.toString(2))
    }

    fun get(ctx: Context, env: String, id: String): RegistryEntry? =
        load(ctx)[key(env, id)]

    fun isInstalled(ctx: Context, env: String, id: String): Boolean {
        val e = get(ctx, env, id) ?: return false
        return e.state == "installed"
    }

    fun put(ctx: Context, entry: RegistryEntry) {
        val map = load(ctx)
        map[entry.key] = entry
        save(ctx, map)
    }

    fun remove(ctx: Context, env: String, id: String) {
        val map = load(ctx)
        map.remove(key(env, id))
        save(ctx, map)
    }

    fun forEnv(ctx: Context, env: String): List<RegistryEntry> =
        load(ctx).values.filter { it.env == env && it.state == "installed" }

    /** True if apt package name is provided by any registry entry for env. */
    fun isMarketplaceApt(ctx: Context, env: String, aptName: String): Boolean {
        return forEnv(ctx, env).any { e ->
            e.id == aptName || e.aptProvides.any { it.equals(aptName, true) }
        }
    }

    fun nowIso(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    private fun parseEntry(o: JSONObject): RegistryEntry? {
        val id = o.optString("id").ifBlank { return null }
        val env = o.optString("env").ifBlank { return null }
        return RegistryEntry(
            id = id,
            env = env,
            kind = PackageKind.from(o.optString("kind")),
            version = o.optString("version", ""),
            installedAt = o.optString("installed_at", ""),
            sizeBytes = o.optLong("size_bytes", 0L),
            aptProvides = jsonStringList(o.optJSONArray("apt_provides")),
            paths = jsonStringList(o.optJSONArray("paths")),
            launchCommand = o.optString("launch_command").ifBlank { null },
            launchType = o.optString("launch_type").ifBlank { null },
            source = o.optString("source", "marketplace"),
            state = o.optString("state", "installed")
        )
    }

    private fun toJson(e: RegistryEntry): JSONObject =
        JSONObject()
            .put("id", e.id)
            .put("env", e.env)
            .put("kind", if (e.kind == PackageKind.APP) "app" else "component")
            .put("version", e.version)
            .put("installed_at", e.installedAt)
            .put("size_bytes", e.sizeBytes)
            .put("apt_provides", JSONArray(e.aptProvides))
            .put("paths", JSONArray(e.paths))
            .put("launch_command", e.launchCommand)
            .put("launch_type", e.launchType)
            .put("source", e.source)
            .put("state", e.state)

    private fun jsonStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotBlank()) add(s)
            }
        }
    }
}

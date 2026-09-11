package com.devandrefigueiredo.wasmtests

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the public GitHub Releases API of this repo. No auth needed (public repo).
 * All calls are blocking — invoke them off the main thread.
 */
object ReleaseUpdater {
    private const val OWNER = "devandrefigueiredo"
    private const val REPO = "web-assembly-tests"
    private const val LATEST_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val ASSET_NAME = "calc.wasm"

    data class Release(val version: String, val wasmUrl: String)

    /** The latest published release, or null if there is none / no calc.wasm asset. */
    fun latest(): Release? {
        val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.use { it.readBytes() }.decodeToString())
            val version = json.optString("tag_name").removePrefix("v")
            if (version.isEmpty()) return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name") == ASSET_NAME) {
                    val url = asset.optString("browser_download_url")
                    if (url.isNotEmpty()) return Release(version, url)
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads the wasm bytes of a release asset. */
    fun download(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 15000
            instanceFollowRedirects = true
        }
        try {
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /** true if [remote] is a strictly higher dotted-number version than [local]. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".")
        val l = local.split(".")
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val rv = r.getOrNull(i)?.toIntOrNull() ?: 0
            val lv = l.getOrNull(i)?.toIntOrNull() ?: 0
            if (rv != lv) return rv > lv
        }
        return false
    }
}

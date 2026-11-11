package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.web.HtmlHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Storage for custom wallpapers.
 *
 * - The index (titles only) is read once and kept in memory; source code is read lazily.
 * - Every disk operation runs on [Dispatchers.IO]; writes are atomic (temp file + rename).
 * - A wallpaper is a folder, so imported zip projects can bring their own CSS/JS/libraries/assets.
 */
class WallpaperStore(context: Context, private val settings: AppSettings) {

    private val app = context.applicationContext
    private val root = WallpaperPaths.root(app).apply { mkdirs() }
    private val indexFile = File(root, "index.json")
    private val lock = Mutex()

    private val _items = MutableStateFlow<List<Wallpaper>>(Presets.all)
    val items: StateFlow<List<Wallpaper>> = _items.asStateFlow()

    /** Reads the index (and migrates data from older app versions) without blocking the UI. */
    suspend fun load() {
        withContext(Dispatchers.IO) {
            lock.withLock {
                migrateLegacy()
                publish(readIndex())
            }
        }
    }

    fun contains(id: String): Boolean = _items.value.any { it.id == id }

    suspend fun readCode(id: String): String = withContext(Dispatchers.IO) {
        try {
            if (Presets.byId(id) != null) {
                app.assets.open("presets/$id/index.html").use { it.readBytes().toString(Charsets.UTF_8) }
            } else {
                val file = File(WallpaperPaths.dir(app, id), "index.html")
                if (file.isFile) file.readText() else ""
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read code for $id", e)
            ""
        }
    }

    suspend fun starterCode(): String = withContext(Dispatchers.IO) {
        try {
            app.assets.open("templates/starter.html").use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: IOException) {
            "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n</head>\n<body></body>\n</html>\n"
        }
    }

    suspend fun create(title: String, code: String): Wallpaper = withContext(Dispatchers.IO) {
        lock.withLock {
            val id = newId()
            val dir = WallpaperPaths.dir(app, id)
            dir.mkdirs()
            atomicWrite(File(dir, "index.html"), code.toByteArray(Charsets.UTF_8))
            val item = Wallpaper(id, cleanTitle(title), false, System.currentTimeMillis())
            val updated = listOf(item) + customItems()
            writeIndex(updated)
            publish(updated)
            item
        }
    }

    suspend fun save(id: String, title: String, code: String) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                val current = customItems()
                val old = current.firstOrNull { it.id == id } ?: return@withLock
                atomicWrite(File(WallpaperPaths.dir(app, id), "index.html"), code.toByteArray(Charsets.UTF_8))
                val now = System.currentTimeMillis()
                val updated = current.map {
                    if (it.id == id) it.copy(title = cleanTitle(title, old.title), updatedAt = now) else it
                }
                writeIndex(updated)
                publish(updated)
            }
        }
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                if (!WallpaperPaths.isCustomId(id)) return@withLock
                WallpaperPaths.dir(app, id).deleteRecursively()
                val updated = customItems().filterNot { it.id == id }
                writeIndex(updated)
                publish(updated)
            }
        }
    }

    /** Imports a single `.html` file or a `.zip` web project (must contain an index.html). */
    suspend fun importUri(uri: Uri): Result<Wallpaper> = withContext(Dispatchers.IO) {
        try {
            val name = queryName(uri) ?: "Imported"
            val input = app.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open the selected file")
            input.use { raw ->
                val stream = BufferedInputStream(raw)
                stream.mark(4)
                val magic = ByteArray(4)
                val read = stream.read(magic)
                stream.reset()
                val isZip = read == 4 &&
                    magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte() &&
                    magic[2] == 0x03.toByte() && magic[3] == 0x04.toByte()
                val title = name
                    .replace(Regex("\\.(html?|zip)$", RegexOption.IGNORE_CASE), "")
                    .replace('_', ' ')
                    .trim()
                    .ifBlank { "Imported" }
                if (isZip) {
                    Result.success(importZip(stream, title))
                } else {
                    val text = readTextLimited(stream, MAX_HTML_BYTES)
                    if (text.isBlank()) throw IOException("The selected file is empty")
                    // Files exported from older versions of this app contain injected helper code.
                    Result.success(create(title, HtmlHelper.stripInjected(text).trim()))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private suspend fun importZip(stream: InputStream, title: String): Wallpaper {
        val id = newId()
        val dir = WallpaperPaths.dir(app, id)
        try {
            dir.mkdirs()
            extractZip(stream, dir)
            flattenSingleFolder(dir)
            if (!File(dir, "index.html").isFile) throw IOException("The archive has no index.html")
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        return lock.withLock {
            val item = Wallpaper(id, cleanTitle(title), false, System.currentTimeMillis())
            val updated = listOf(item) + customItems()
            writeIndex(updated)
            publish(updated)
            item
        }
    }

    private fun extractZip(stream: InputStream, target: File) {
        val rootPath = target.canonicalPath + File.separator
        var entries = 0
        var total = 0L
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (++entries > MAX_ZIP_ENTRIES) throw IOException("The archive has too many files")
                val out = File(target, entry.name)
                // Zip-slip protection: never write outside the wallpaper folder.
                if (!out.canonicalPath.startsWith(rootPath)) throw IOException("Unsafe path in archive")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { sink ->
                        total += copyLimited(zip, sink, MAX_ZIP_BYTES - total)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    /** Zips often wrap everything in one top-level folder; move its contents up. */
    private fun flattenSingleFolder(dir: File) {
        if (File(dir, "index.html").isFile) return
        val folders = dir.listFiles()?.filter { it.isDirectory && it.name != "__MACOSX" } ?: return
        val inner = folders.singleOrNull() ?: return
        if (!File(inner, "index.html").isFile) return
        inner.listFiles()?.forEach { it.renameTo(File(dir, it.name)) }
        inner.deleteRecursively()
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("The archive is too large")
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun readTextLimited(stream: InputStream, limit: Int): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("File is too large (max ${limit / (1024 * 1024)} MB)")
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun queryName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0) name = cursor.getString(column)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    private fun customItems(): List<Wallpaper> = _items.value.filter { !it.isPreset }

    private fun publish(custom: List<Wallpaper>) {
        _items.value = Presets.all + custom
    }

    private fun newId(): String = "c" + UUID.randomUUID().toString().replace("-", "")

    private fun cleanTitle(title: String, fallback: String = "Untitled"): String =
        title.trim().ifBlank { fallback }

    private fun readIndex(): List<Wallpaper> {
        if (!indexFile.isFile) return emptyList()
        val array = try {
            JSONArray(indexFile.readText())
        } catch (e: Exception) {
            // Keep the unreadable index instead of letting the next save overwrite it.
            Log.e(TAG, "Index is unreadable, moving it aside", e)
            indexFile.renameTo(File(root, "index.json.corrupt"))
            return emptyList()
        }
        val list = ArrayList<Wallpaper>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (!WallpaperPaths.isCustomId(id)) continue
            if (!File(WallpaperPaths.dir(app, id), "index.html").isFile) continue
            list.add(Wallpaper(id, cleanTitle(o.optString("title")), false, o.optLong("updatedAt", 0L)))
        }
        return list
    }

    private fun writeIndex(custom: List<Wallpaper>) {
        val array = JSONArray()
        for (item in custom) {
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("updatedAt", item.updatedAt)
            )
        }
        atomicWrite(indexFile, array.toString().toByteArray(Charsets.UTF_8))
    }

    /** Write to a temp file, then rename, so a crash can never leave a half-written file behind. */
    private fun atomicWrite(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.writeBytes(bytes)
            tmp.delete()
        }
    }

    /** Brings wallpapers saved by the previous version of the app (single html files) over. */
    private fun migrateLegacy() {
        if (settings.legacyMigrated) return
        settings.legacyMigrated = true // set first so a crash can never make this loop
        try {
            val oldDir = File(app.filesDir, "custom_wallpapers")
            val oldIndex = File(oldDir, "custom_index.json")
            if (!oldIndex.isFile) return
            val array = JSONArray(oldIndex.readText())
            val oldActive = settings.legacyActiveId
            val migrated = ArrayList<Wallpaper>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val oldId = o.optString("id")
                val file = File(oldDir, "$oldId.html")
                if (oldId.isEmpty() || !file.isFile) continue
                val id = newId()
                val dir = WallpaperPaths.dir(app, id)
                dir.mkdirs()
                val code = HtmlHelper.stripInjected(file.readText()).trim()
                atomicWrite(File(dir, "index.html"), code.toByteArray(Charsets.UTF_8))
                migrated.add(
                    Wallpaper(
                        id,
                        cleanTitle(o.optString("title")),
                        false,
                        o.optLong("createdTimestamp", System.currentTimeMillis())
                    )
                )
                if (oldActive == oldId) settings.legacyActiveId = id
            }
            if (migrated.isNotEmpty()) writeIndex(migrated + readIndex())
            if (migrated.size == array.length()) oldDir.deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "Legacy migration failed", e)
        }
    }

    private companion object {
        const val TAG = "WallpaperStore"
        const val MAX_HTML_BYTES = 10 * 1024 * 1024
        const val MAX_ZIP_BYTES = 64L * 1024 * 1024
        const val MAX_ZIP_ENTRIES = 4000
    }
}

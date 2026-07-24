package com.darsma.glassgallery.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter

data class ImageSearchMetadata(
    val text: String,
    val labels: List<String>,
)

/**
 * Small append-only JSON cache for OCR text and image labels.
 *
 * A cache key includes the MediaStore URI, date and size so an ID reused for a
 * different file is never mistaken for an already-indexed image. Stale entries
 * are compacted when a non-empty photo library is loaded.
 */
class ImageSearchIndexStore(context: Context) {

    private val cacheFile = File(
        context.applicationContext.filesDir,
        CACHE_FILE_NAME,
    )
    private val lock = Mutex()
    private var entries: MutableMap<String, ImageSearchMetadata>? = null

    suspend fun loadFor(media: List<Video>): Map<String, ImageSearchMetadata> =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val loaded = entries ?: readCache().also { entries = it }
                val currentKeys = media.asSequence()
                    .filterNot { it.isVideo }
                    .map(Video::imageSearchCacheKey)
                    .toSet()

                if (currentKeys.isNotEmpty() && loaded.keys.retainAll(currentKeys)) {
                    rewriteCache(loaded)
                }
                loaded.filterKeys { it in currentKeys }
            }
        }

    suspend fun save(media: Video, metadata: ImageSearchMetadata): Boolean =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val loaded = entries ?: readCache().also { entries = it }
                val key = media.imageSearchCacheKey()
                if (key in loaded) return@withLock false

                appendEntry(key, metadata)
                loaded[key] = metadata
                true
            }
        }

    private fun readCache(): MutableMap<String, ImageSearchMetadata> {
        val loaded = linkedMapOf<String, ImageSearchMetadata>()
        if (!cacheFile.exists()) return loaded

        var hasDamagedLine = false
        cacheFile.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            runCatching {
                val json = JSONObject(line)
                val key = json.getString(JSON_KEY_CACHE_KEY)
                val labelsJson = json.optJSONArray(JSON_KEY_LABELS) ?: JSONArray()
                val labels = buildList {
                    for (index in 0 until labelsJson.length()) {
                        val label = labelsJson.optString(index)
                        if (label.isNotBlank()) add(label)
                    }
                }
                loaded[key] = ImageSearchMetadata(
                    text = json.optString(JSON_KEY_TEXT),
                    labels = labels,
                )
            }.onFailure {
                hasDamagedLine = true
            }
        }

        if (hasDamagedLine) rewriteCache(loaded)
        return loaded
    }

    private fun appendEntry(key: String, metadata: ImageSearchMetadata) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.appendText(
            entryJson(key, metadata).toString() + "\n",
            Charsets.UTF_8,
        )
    }

    private fun rewriteCache(values: Map<String, ImageSearchMetadata>) {
        cacheFile.parentFile?.mkdirs()
        val atomicFile = AtomicFile(cacheFile)
        val output = atomicFile.startWrite()
        try {
            val writer = OutputStreamWriter(output, Charsets.UTF_8).buffered()
            values.forEach { (key, metadata) ->
                writer.write(entryJson(key, metadata).toString())
                writer.newLine()
            }
            writer.flush()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private fun entryJson(
        key: String,
        metadata: ImageSearchMetadata,
    ): JSONObject = JSONObject()
        .put(JSON_KEY_CACHE_KEY, key)
        .put(JSON_KEY_TEXT, metadata.text)
        .put(JSON_KEY_LABELS, JSONArray(metadata.labels))

    private companion object {
        const val CACHE_FILE_NAME = "image_search_index.jsonl"
        const val JSON_KEY_CACHE_KEY = "key"
        const val JSON_KEY_TEXT = "text"
        const val JSON_KEY_LABELS = "labels"
    }
}

fun Video.imageSearchCacheKey(): String =
    "${uri}|${dateAdded}|${sizeBytes}"

package com.darsma.glassgallery.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Incrementally indexes photos with ML Kit's bundled, on-device models.
 *
 * Work is sequential to keep memory use bounded and is safe to cancel when the
 * gallery reloads. A failure in one ML pipeline still preserves output from the
 * other; a complete per-image failure is silently skipped.
 */
class ImageSearchIndexer(context: Context) {

    private val appContext = context.applicationContext
    private val store = ImageSearchIndexStore(appContext)
    private val textRecognizerDelegate = lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val textRecognizer by textRecognizerDelegate
    private val imageLabelerDelegate = lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }
    private val imageLabeler by imageLabelerDelegate

    suspend fun loadCached(media: List<Video>): Map<String, ImageSearchMetadata> =
        try {
            store.loadFor(media)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            emptyMap()
        }

    suspend fun indexMissing(
        media: List<Video>,
        cachedKeys: Set<String>,
        onIndexed: suspend (Video, ImageSearchMetadata) -> Unit,
    ) {
        val knownKeys = cachedKeys.toMutableSet()
        media.asSequence()
            .filterNot { it.isVideo }
            .forEach { photo ->
                currentCoroutineContext().ensureActive()
                val key = photo.imageSearchCacheKey()
                if (!knownKeys.add(key)) return@forEach

                val metadata = analyze(photo) ?: return@forEach
                try {
                    store.save(photo, metadata)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    // Keep the metadata available for this session even if
                    // durable cache storage is temporarily unavailable.
                }
                onIndexed(photo, metadata)
            }
    }

    fun close() {
        if (textRecognizerDelegate.isInitialized()) runCatching { textRecognizer.close() }
        if (imageLabelerDelegate.isInitialized()) runCatching { imageLabeler.close() }
    }

    private suspend fun analyze(photo: Video): ImageSearchMetadata? =
        withContext(Dispatchers.IO) {
            val input = runCatching {
                InputImage.fromFilePath(appContext, photo.uri)
            }.getOrNull() ?: return@withContext null

            val textResult = mlResult {
                textRecognizer.process(input).awaitResult().text
                    .take(MAX_INDEXED_TEXT_LENGTH)
            }
            val labelResult = mlResult {
                imageLabeler.process(input).awaitResult()
                    .asSequence()
                    .map { it.text.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(MAX_INDEXED_LABELS)
                    .toList()
            }

            if (textResult.isFailure && labelResult.isFailure) {
                return@withContext null
            }
            ImageSearchMetadata(
                text = textResult.getOrDefault(""),
                labels = labelResult.getOrDefault(emptyList()),
            )
        }

    private suspend fun <T> mlResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }

    private suspend fun <T> Task<T>.awaitResult(): T =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener(DIRECT_EXECUTOR) { completed ->
                if (!continuation.isActive) return@addOnCompleteListener
                when {
                    completed.isSuccessful -> continuation.resume(completed.result)
                    completed.isCanceled -> continuation.cancel()
                    else -> continuation.resumeWithException(
                        completed.exception
                            ?: IllegalStateException("ML Kit task failed"),
                    )
                }
            }
        }

    private companion object {
        const val MAX_INDEXED_TEXT_LENGTH = 32_768
        const val MAX_INDEXED_LABELS = 64
        val DIRECT_EXECUTOR = Executor { command -> command.run() }
    }
}

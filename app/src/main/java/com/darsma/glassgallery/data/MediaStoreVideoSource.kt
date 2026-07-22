package com.darsma.glassgallery.data

import android.content.ContentUris
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreVideoSource(private val context: Context) {

    private val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
    )

    suspend fun loadVideos(): List<Video> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<Video>()
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val bktCol  = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )
                videos += Video(
                    id           = id,
                    uri          = contentUri,
                    title        = cursor.getString(nameCol) ?: "Unknown",
                    duration     = cursor.getLong(durCol),
                    thumbnailUri = contentUri,
                    sizeBytes    = cursor.getLong(sizeCol),
                    dateAdded    = cursor.getLong(dateCol),
                    bucketName   = if (bktCol >= 0) cursor.getString(bktCol) ?: "Other" else "Other",
                )
            }
        }
        videos
    }

    suspend fun loadVideoById(id: Long): Video? = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Video.Media._ID} = ?",
            arrayOf(id.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )
                Video(
                    id           = id,
                    uri          = contentUri,
                    title        = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: "Unknown",
                    duration     = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)),
                    thumbnailUri = contentUri,
                    sizeBytes    = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)),
                    dateAdded    = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)),
                )
            } else null
        }
    }

    // ── Photos ─────────────────────────────────────────────────────────────

    private val imageProjection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
    )

    suspend fun loadImages(): List<Video> = withContext(Dispatchers.IO) {
        val images = mutableListOf<Video>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bktCol  = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                images += Video(
                    id           = id,
                    uri          = contentUri,
                    title        = cursor.getString(nameCol) ?: "Unknown",
                    duration     = 0L,
                    thumbnailUri = contentUri,
                    sizeBytes    = cursor.getLong(sizeCol),
                    dateAdded    = cursor.getLong(dateCol),
                    isVideo      = false,
                    bucketName   = if (bktCol >= 0) cursor.getString(bktCol) ?: "Other" else "Other",
                )
            }
        }
        images
    }

    /** Every video and photo on the device, newest first.
     *  MediaStore _IDs come from the shared files table, so they never
     *  collide between the two queries. */
    suspend fun loadAllMedia(): List<Video> = withContext(Dispatchers.IO) {
        val videos = runCatching { loadVideos() }.getOrDefault(emptyList())
        val images = runCatching { loadImages() }.getOrDefault(emptyList())
        (videos + images).sortedByDescending { it.dateAdded }
    }

    suspend fun loadImageById(id: Long): Video? = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            "${MediaStore.Images.Media._ID} = ?",
            arrayOf(id.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                Video(
                    id           = id,
                    uri          = contentUri,
                    title        = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "Unknown",
                    duration     = 0L,
                    thumbnailUri = contentUri,
                    sizeBytes    = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)),
                    dateAdded    = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)),
                    isVideo      = false,
                )
            } else null
        }
    }

    // ── Trash (30-day OS recycle bin) ──────────────────────────────────────

    /** Everything the user has trashed (IS_TRASHED items only), newest first. */
    suspend fun loadTrashed(): List<Video> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Video>()

        fun query(collection: android.net.Uri, isVideo: Boolean) {
            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                putString(
                    android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                )
            }
            val proj = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED,
            )
            runCatching {
                context.contentResolver.query(collection, proj, args, null)?.use { cursor ->
                    val idCol   = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    while (cursor.moveToNext()) {
                        val id  = cursor.getLong(idCol)
                        val uri = ContentUris.withAppendedId(collection, id)
                        out += Video(
                            id           = id,
                            uri          = uri,
                            title        = cursor.getString(nameCol) ?: "Unknown",
                            duration     = 0L,
                            thumbnailUri = uri,
                            sizeBytes    = cursor.getLong(sizeCol),
                            dateAdded    = cursor.getLong(dateCol),
                            isVideo      = isVideo,
                        )
                    }
                }
            }
        }
        query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true)
        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false)
        out.sortedByDescending { it.dateAdded }
    }
}

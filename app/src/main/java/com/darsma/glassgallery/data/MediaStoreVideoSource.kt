package com.darsma.glassgallery.data

import android.content.ContentUris
import android.content.Context
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
}

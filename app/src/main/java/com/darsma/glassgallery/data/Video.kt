package com.darsma.glassgallery.data

import android.net.Uri

data class Video(
    val id: Long,
    val uri: Uri,
    val title: String,
    val duration: Long,        // milliseconds
    val thumbnailUri: Uri,
    val sizeBytes: Long = 0L,  // file size in bytes
    val dateAdded: Long = 0L,  // unix seconds
    val isVideo: Boolean = true,  // false → this item is a photo
    val bucketName: String = "",  // MediaStore album/folder name
) {
    /** Human-readable file size, e.g. "24.6 MB". */
    val readableSize: String
        get() = formatBytes(sizeBytes)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024.0 && i < units.lastIndex) {
        value /= 1024.0
        i++
    }
    return if (i == 0) "${value.toInt()} ${units[i]}"
    else "%.1f %s".format(value, units[i])
}

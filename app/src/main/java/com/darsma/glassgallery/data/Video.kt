package com.darsma.glassgallery.data

import android.net.Uri

data class Video(
    val id: Long,
    val uri: Uri,
    val title: String,
    val duration: Long,
    val thumbnailUri: Uri,
)

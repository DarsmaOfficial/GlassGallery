package com.darsma.glassgallery.ui.components

import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darsma.glassgallery.data.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SheetShape = RoundedCornerShape(30.dp)

/**
 * Glass bottom sheet with everything the OS knows about a media item —
 * resolution and storage path are queried lazily off the main thread.
 */
@Composable
fun MediaDetailsSheet(
    visible: Boolean,
    video: Video?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val extra by produceState<Pair<String, String>?>(initialValue = null, video?.id, visible) {
        value = null
        val item = video ?: return@produceState
        if (!visible) return@produceState
        value = withContext(Dispatchers.IO) {
            var resolution = "—"
            var path       = "—"
            runCatching {
                context.contentResolver.query(
                    item.uri,
                    arrayOf(
                        MediaStore.MediaColumns.WIDTH,
                        MediaStore.MediaColumns.HEIGHT,
                        MediaStore.MediaColumns.RELATIVE_PATH,
                    ),
                    null, null, null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val w = cursor.getInt(0)
                        val h = cursor.getInt(1)
                        if (w > 0 && h > 0) resolution = "$w × $h"
                        path = (cursor.getString(2) ?: "") + item.title
                    }
                }
            }
            resolution to path
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Scrim.
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null,
                        onClick           = onDismiss,
                    ),
            )
        }
        // Sheet.
        AnimatedVisibility(
            visible  = visible,
            enter    = slideInVertically(initialOffsetY = { it }, animationSpec = Motion.expressive()) +
                       fadeIn(Motion.standard()),
            exit     = slideOutVertically(targetOffsetY = { it }, animationSpec = Motion.standard()) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .clip(SheetShape)
                    .liquidGlass(alpha = 0.90f)
                    .liquidGlassBorder(SheetShape)
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
            ) {
                Text(
                    text       = "Details",
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                )
                Spacer(Modifier.height(14.dp))
                val item = video
                if (item != null) {
                    DetailRow("Name", item.title)
                    DetailRow("Type", if (item.isVideo) "Video" else "Photo")
                    DetailRow("Resolution", extra?.first ?: "…")
                    if (item.isVideo) DetailRow("Duration", formatDetailDuration(item.duration))
                    DetailRow("Size", item.readableSize)
                    DetailRow(
                        "Added",
                        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
                            .format(Date(item.dateAdded * 1000L)),
                    )
                    DetailRow("Path", extra?.second ?: "…")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text     = label,
            fontSize = 13.sp,
            color    = Color.White.copy(alpha = 0.50f),
            modifier = Modifier.fillMaxWidth(0.32f),
        )
        Text(
            text       = value,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
            color      = Color.White.copy(alpha = 0.92f),
        )
    }
}

private fun formatDetailDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

package com.darsma.glassgallery.widget

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.darsma.glassgallery.MainActivity
import com.darsma.glassgallery.R
import com.darsma.glassgallery.data.FavoritesStore
import com.darsma.glassgallery.data.MediaStoreVideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FavoritesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = loadFavoriteThumbnails(context)
        val title = context.getString(R.string.widget_favorites_title)
        val emptyMessage = context.getString(R.string.widget_favorites_empty)
        val openMessage = context.getString(R.string.widget_favorites_open)

        provideContent {
            FavoritesContent(
                items = items,
                title = title,
                emptyMessage = emptyMessage,
                openMessage = openMessage,
            )
        }
    }

    private suspend fun loadFavoriteThumbnails(context: Context): List<WidgetMedia> =
        withContext(Dispatchers.IO) {
            runCatching {
                val favoriteIds = FavoritesStore(context).loadFavorites()
                if (favoriteIds.isEmpty()) return@runCatching emptyList()

                val favorites = MediaStoreVideoSource(context)
                    .loadAllMedia()
                    .asSequence()
                    .filter { it.id in favoriteIds }
                    .take(MAX_THUMBNAILS)
                    .toList()

                if (favorites.isEmpty()) return@runCatching emptyList()

                favorites.map { media ->
                    WidgetMedia(
                        bitmap = context.contentResolver.loadThumbnail(
                            media.thumbnailUri,
                            Size(THUMBNAIL_SIZE_PX, THUMBNAIL_SIZE_PX),
                            null,
                        ),
                        title = media.title,
                        isVideo = media.isVideo,
                    )
                }
            }.getOrDefault(emptyList())
        }

    private companion object {
        const val MAX_THUMBNAILS = 4
        const val THUMBNAIL_SIZE_PX = 192
    }
}

private data class WidgetMedia(
    val bitmap: Bitmap,
    val title: String,
    val isVideo: Boolean,
)

@Composable
private fun FavoritesContent(
    items: List<WidgetMedia>,
    title: String,
    emptyMessage: String,
    openMessage: String,
) {
    val openApp = actionStartActivity<MainActivity>()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xEE11131A))
            .cornerRadius(20.dp)
            .clickable(openApp)
            .padding(14.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = ColorProvider(Color(0xFFF4F1FA)),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(9.dp))

        if (items.isEmpty()) {
            EmptyFavorites(
                message = emptyMessage,
                openMessage = openMessage,
                modifier = GlanceModifier.defaultWeight(),
            )
        } else {
            FavoriteThumbnailRow(
                items = items,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun EmptyFavorites(
    message: String,
    openMessage: String,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "♡",
            style = TextStyle(
                color = ColorProvider(Color(0xFFE8B5FF)),
                fontSize = 25.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(modifier = GlanceModifier.height(3.dp))
        Text(
            text = message,
            modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(
                color = ColorProvider(Color(0xFFE4DFE9)),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
        Text(
            text = openMessage,
            modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(
                color = ColorProvider(Color(0xFFBEB7C6)),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun FavoriteThumbnailRow(
    items: List<WidgetMedia>,
    modifier: GlanceModifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, media ->
            if (index > 0) {
                Spacer(modifier = GlanceModifier.width(6.dp))
            }
            FavoriteThumbnail(
                media = media,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun FavoriteThumbnail(
    media: WidgetMedia,
    modifier: GlanceModifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF292530))
            .cornerRadius(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Image(
            provider = ImageProvider(media.bitmap),
            contentDescription = media.title,
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(12.dp),
            contentScale = ContentScale.Crop,
        )
        if (media.isVideo) {
            Text(
                text = "▶",
                modifier = GlanceModifier
                    .padding(5.dp)
                    .background(Color(0xB3000000))
                    .cornerRadius(8.dp)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 10.sp,
                ),
            )
        }
    }
}

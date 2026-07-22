package com.darsma.glassgallery.ui.trash

import android.app.Activity
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.darsma.glassgallery.data.MediaStoreVideoSource
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.ui.components.AuroraBackground
import com.darsma.glassgallery.ui.components.BouncyIconButton
import com.darsma.glassgallery.ui.components.Motion
import com.darsma.glassgallery.ui.components.glassSheen
import com.darsma.glassgallery.ui.components.liquidGlass
import com.darsma.glassgallery.ui.components.liquidGlassBorder
import com.darsma.glassgallery.ui.components.pressBounce

private val TileShape = RoundedCornerShape(16.dp)

/**
 * Recently Deleted — items sitting in the 30-day OS trash. Each tile morphs
 * out of the grid when restored or purged; both actions run through the
 * system confirmation sheet so the OS stays the source of truth.
 */
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val source  = remember { MediaStoreVideoSource(context) }

    var items   by remember { mutableStateOf<List<Video>?>(null) }
    var reloads by remember { mutableIntStateOf(0) }
    LaunchedEffect(reloads) { items = source.loadTrashed() }

    // One launcher handles restore, another handles delete-forever.
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> if (result.resultCode == Activity.RESULT_OK) reloads++ }
    val purgeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> if (result.resultCode == Activity.RESULT_OK) reloads++ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AuroraBackground()

        Column(Modifier.fillMaxSize()) {
            // ── Glass header ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .liquidGlass(alpha = 0.45f)
                    .glassSheen()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BouncyIconButton(onClick = onBack, size = 46.dp) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text       = "Recently Deleted",
                        fontSize   = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                    )
                    AnimatedContent(
                        targetState    = items?.size,
                        transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.snappy()) },
                        label          = "trash-count",
                    ) { count ->
                        Text(
                            text     = when (count) {
                                null -> "Loading…"
                                0    -> "Nothing in trash"
                                else -> "$count items · auto-erased after 30 days"
                            },
                            fontSize = 12.sp,
                            color    = Color.White.copy(alpha = 0.55f),
                        )
                    }
                }
            }

            when {
                items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                items!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text     = "Trash is empty.\nDeleted media rests here for 30 days.",
                        color    = Color.White.copy(alpha = 0.55f),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(32.dp),
                    )
                }
                else -> LazyVerticalGrid(
                    columns               = GridCells.Fixed(2),
                    modifier              = Modifier.fillMaxSize(),
                    contentPadding        = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement   = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(items = items!!, key = { it.id }) { media ->
                        TrashTile(
                            media     = media,
                            onRestore = {
                                val pi = MediaStore.createTrashRequest(
                                    context.contentResolver, listOf(media.uri), false
                                )
                                restoreLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                            },
                            onPurge   = {
                                val pi = MediaStore.createDeleteRequest(
                                    context.contentResolver, listOf(media.uri)
                                )
                                purgeLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                            },
                            modifier  = Modifier.animateItem(
                                fadeInSpec    = Motion.standard(),
                                placementSpec = Motion.expressive(),
                                fadeOutSpec   = Motion.snappy(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashTile(
    media: Video,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val appear by animateFloatAsState(
        targetValue   = if (appeared) 1f else 0f,
        animationSpec = Motion.expressive(),
        label         = "trash-appear",
    )

    Column(
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.96f)
            .clip(TileShape)
            .background(Color.White.copy(alpha = 0.05f))
            .liquidGlassBorder(TileShape),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.3f)
        ) {
            AsyncImage(
                model              = media.thumbnailUri,
                contentDescription = media.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
                alpha              = 0.45f + 0.55f * appear,  // ghosted → alive
            )
        }
        Text(
            text       = media.title,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Medium,
            color      = Color.White.copy(alpha = 0.80f),
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp),
        )
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            GlassAction("Restore", Color(0xFF7FE3A8), onRestore) {
                Icon(Icons.Rounded.Restore, null, tint = Color(0xFF7FE3A8), modifier = Modifier.size(16.dp))
            }
            GlassAction("Erase", Color(0xFFFF7A7A), onPurge) {
                Icon(Icons.Rounded.DeleteForever, null, tint = Color(0xFFFF7A7A), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun GlassAction(
    label: String,
    tint: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.88f)
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

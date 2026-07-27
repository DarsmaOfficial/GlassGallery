@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.darsma.glassgallery.ui.photo

import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.darsma.glassgallery.data.MediaStoreVideoSource
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.ui.components.BouncyIconButton
import com.darsma.glassgallery.ui.components.MediaDetailsSheet
import com.darsma.glassgallery.ui.components.Motion
import com.darsma.glassgallery.ui.components.favoriteBurst
import com.darsma.glassgallery.ui.components.liquidGlass
import com.darsma.glassgallery.ui.components.liquidGlassBorder
import com.darsma.glassgallery.ui.components.opticalGlass
import com.darsma.glassgallery.ui.gallery.GalleryUiState
import com.darsma.glassgallery.ui.theme.TabularFigures
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.delay
import com.darsma.glassgallery.ui.components.pressBounce
import com.darsma.glassgallery.ui.gallery.GalleryViewModel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private val ChromeShape = RoundedCornerShape(26.dp)

/**
 * Full-screen photo viewer. The image morphs in from its grid card via a
 * shared-element bounds transform (key "photo-{id}" — the only place this key
 * exists besides the grid card), then supports pinch-zoom, pan, double-tap
 * zoom with spring physics, tap-to-toggle chrome, and predictive back.
 */
@Composable
fun PhotoViewerScreen(
    photoId: Long,
    galleryViewModel: GalleryViewModel,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val context = LocalContext.current

    val photo: Video? by produceState<Video?>(null, photoId) {
        value = MediaStoreVideoSource(context).loadImageById(photoId)
    }

    val favorites by galleryViewModel.favorites.collectAsState()
    val isFavorite = photoId in favorites

    val photoUri = remember(photoId) {
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoId)
    }

    var chromeVisible  by remember { mutableStateOf(true) }
    var detailsVisible by remember { mutableStateOf(false) }
    var slideshowOn    by remember { mutableStateOf(false) }
    var scanInProgress by remember(photoId) { mutableStateOf(false) }
    var scanResultsVisible by remember(photoId) { mutableStateOf(false) }
    var scannedBarcodes by remember(photoId) { mutableStateOf<List<Barcode>>(emptyList()) }

    // Photos in the current gallery filter — the slideshow's playlist.
    val galleryState by galleryViewModel.uiState.collectAsState()
    val slidePhotos = remember(galleryState) {
        (galleryState as? GalleryUiState.Success)?.videos?.filter { !it.isVideo } ?: emptyList()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            galleryViewModel.removeFromList(setOf(photoId))
            onBack()
        }
    }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    // One-finger pull-down dismissal at 1x zoom. `dismissY` is an Animatable so
    // the surface follows the finger directly, then on release rides a spring
    // that *continues at the finger's measured velocity*: a quick flick throws
    // it off-screen, a gentle release settles it back home. Because it is an
    // Animatable, a fresh touch can cancel any in-flight settle/exit and hand
    // control straight back to the finger from the current value.
    val dismissY = remember { Animatable(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val dismissVelocityTracker = remember { VelocityTracker() }
    // Flick threshold expressed in dp/s and converted to px/s so it feels the
    // same across densities. ~1100 dp/s sits in the requested 800–1200 range.
    val flickThresholdPx = with(LocalDensity.current) { 1100.dp.toPx() }
    val dismissProgress = if (containerSize.height > 0) {
        (dismissY.value / (containerSize.height * 0.48f)).coerceIn(0f, 1f)
    } else 0f

    // Zoom/pan are Animatables so every settle — pinch release, double-tap,
    // reset — rides a spring instead of snapping.
    val zoom    = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scope.launch {
            val newZoom = (zoom.value * zoomChange).coerceIn(1f, 6f)
            zoom.snapTo(newZoom)
            val maxX = (containerSize.width  * (newZoom - 1f)) / 2f
            val maxY = (containerSize.height * (newZoom - 1f)) / 2f
            offsetX.snapTo((offsetX.value + panChange.x).coerceIn(-maxX, maxX))
            offsetY.snapTo((offsetY.value + panChange.y).coerceIn(-maxY, maxY))
        }
    }

    // Predictive back: shrink + fade + settle downward, exactly like the
    // video player, so both viewers speak the same gesture language.
    val backProgress = remember { Animatable(0f) }
    PredictiveBackHandler { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                backProgress.snapTo(backEvent.progress)
            }
            onBack()
        } catch (e: CancellationException) {
            backProgress.animateTo(0f, animationSpec = Motion.expressive())
        }
    }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val back = backProgress.value
                    val p = maxOf(back, dismissProgress)
                    val s = 1f - 0.14f * p
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.28f * back - 0.08f * dismissProgress
                    translationY = 26.dp.toPx() * back + dismissY.value
                    if (p > 0.001f) {
                        shape = RoundedCornerShape(48f * p)
                        clip = true
                        shadowElevation = 28.dp.toPx() * dismissProgress
                    }
                }
                .background(Color.Black.copy(alpha = 1f - 0.44f * dismissProgress))
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { chromeVisible = !chromeVisible },
                        onDoubleTap = { tap ->
                            scope.launch {
                                if (zoom.value > 1.05f) {
                                    // Spring everything home.
                                    launch { zoom.animateTo(1f, Motion.expressive()) }
                                    launch { offsetX.animateTo(0f, Motion.expressive()) }
                                    launch { offsetY.animateTo(0f, Motion.expressive()) }
                                } else {
                                    // Zoom toward the tapped point.
                                    val target = 2.6f
                                    val maxX = (containerSize.width  * (target - 1f)) / 2f
                                    val maxY = (containerSize.height * (target - 1f)) / 2f
                                    val ox = ((containerSize.width  / 2f - tap.x) * (target - 1f))
                                        .coerceIn(-maxX, maxX)
                                    val oy = ((containerSize.height / 2f - tap.y) * (target - 1f))
                                        .coerceIn(-maxY, maxY)
                                    launch { zoom.animateTo(target, Motion.expressive()) }
                                    launch { offsetX.animateTo(ox, Motion.expressive()) }
                                    launch { offsetY.animateTo(oy, Motion.expressive()) }
                                }
                            }
                        },
                    )
                }
                .pointerInput(zoom.value, containerSize.height) {
                    if (zoom.value <= 1.02f && containerSize.height > 0) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                // A fresh touch: measure velocity anew and hand
                                // control back to the finger, cancelling any
                                // in-flight settle/exit from the current value.
                                dismissVelocityTracker.resetTracking()
                                scope.launch { dismissY.stop() }
                                dragOffsetY = dismissY.value
                            },
                            onVerticalDrag = { change, dragAmount ->
                                // Feed real pointer samples so the release
                                // velocity is measured, not frame-differenced.
                                dismissVelocityTracker.addPosition(
                                    change.uptimeMillis, change.position,
                                )
                                val next = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                                if (next > 0f || dragAmount > 0f) {
                                    change.consume()
                                    dragOffsetY = next
                                    scope.launch { dismissY.snapTo(next) }
                                }
                            },
                            onDragCancel = {
                                dismissVelocityTracker.resetTracking()
                                scope.launch {
                                    dismissY.animateTo(0f, Motion.settle())
                                }
                            },
                            onDragEnd = {
                                val height = containerSize.height
                                // Real measured release velocity (px/s, +down).
                                val releaseVelocity =
                                    dismissVelocityTracker.calculateVelocity().y
                                // Dismiss on EITHER a committed pull distance OR
                                // a downward flick that beats the threshold.
                                val passedDistance =
                                    height > 0 && dismissY.value > height * 0.30f
                                val flickedToDismiss = releaseVelocity > flickThresholdPx
                                if (passedDistance || flickedToDismiss) {
                                    chromeVisible = false
                                    scope.launch {
                                        // Travelling surface continues at the
                                        // finger's speed, then navigates away.
                                        dismissY.animateTo(
                                            targetValue = height * 1.1f,
                                            animationSpec = Motion.spatial(),
                                            initialVelocity = releaseVelocity,
                                        )
                                        onBack()
                                    }
                                } else {
                                    scope.launch {
                                        // Settle home, carrying release velocity.
                                        dismissY.animateTo(
                                            targetValue = 0f,
                                            animationSpec = Motion.settle(),
                                            initialVelocity = releaseVelocity,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
                .transformable(transformState),
        ) {
            // ── The photo — shared-element morph target ───────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .sharedBounds(
                        sharedContentState      = rememberSharedContentState("photo-$photoId"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform         = { _, _ -> Motion.expressive() },
                    ),
            ) {
                AsyncImage(
                    model              = photoUri,
                    contentDescription = photo?.title,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX       = zoom.value
                            scaleY       = zoom.value
                            translationX = offsetX.value
                            translationY = offsetY.value
                        },
                )
            }

            AnimatedVisibility(
                visible = dismissY.value > 8f,
                enter = fadeIn(Motion.snappy()) + scaleIn(Motion.morph(), initialScale = 0.72f),
                exit = fadeOut(Motion.snappy()) + scaleOut(Motion.snappy(), targetScale = 0.76f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(top = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = (0.35f + dismissProgress * 0.65f).coerceIn(0f, 1f)
                            scaleX = 0.88f + dismissProgress * 0.12f
                            scaleY = 0.88f + dismissProgress * 0.12f
                        }
                        .clip(RoundedCornerShape(50))
                        .liquidGlass(alpha = 0.68f)
                        .opticalGlass(intensity = 0.90f)
                        .liquidGlassBorder(RoundedCornerShape(50))
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = if (dismissProgress > 0.72f) "Release to close" else "Pull to close",
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            // ── Top chrome ─────────────────────────────────────────────────
            AnimatedVisibility(
                visible  = chromeVisible,
                enter    = slideInVertically(initialOffsetY = { -it }, animationSpec = Motion.expressive()) +
                           fadeIn(Motion.standard()),
                exit     = slideOutVertically(targetOffsetY = { -it }, animationSpec = Motion.standard()) + fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .clip(ChromeShape)
                        .liquidGlass(alpha = 0.42f)
                        .opticalGlass(intensity = 0.76f)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BouncyIconButton(onClick = onBack, size = 46.dp) {
                        Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text       = photo?.title ?: "",
                            style      = MaterialTheme.typography.titleMedium,
                            color      = Color.White,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                        )
                        AnimatedVisibility(
                            visible = photo != null,
                            enter   = fadeIn(Motion.standard()),
                            exit    = fadeOut(),
                        ) {
                            Text(
                                text     = photo?.readableSize ?: "",
                                style    = MaterialTheme.typography.labelMedium.merge(TabularFigures),
                                color    = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }
                    // Favorite with shockwave burst.
                    PhotoFavoriteButton(
                        isFavorite = isFavorite,
                        onToggle   = { galleryViewModel.toggleFavorite(photoId) },
                    )
                    Spacer(Modifier.width(2.dp))
                    BouncyIconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, photoUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share photo"))
                        },
                        size = 46.dp,
                    ) {
                        Icon(Icons.Rounded.Share, "Share", tint = Color.White)
                    }
                    Spacer(Modifier.width(2.dp))
                    BouncyIconButton(
                        onClick = { detailsVisible = true },
                        size    = 46.dp,
                    ) {
                        Icon(Icons.Rounded.Info, "Details", tint = Color.White)
                    }
                    Spacer(Modifier.width(2.dp))
                    BouncyIconButton(
                        onClick = {
                            // Soft delete into the 30-day OS recycle bin.
                            val pi = MediaStore.createTrashRequest(
                                context.contentResolver, listOf(photoUri), true
                            )
                            deleteLauncher.launch(
                                IntentSenderRequest.Builder(pi.intentSender).build()
                            )
                        },
                        size = 46.dp,
                    ) {
                        Icon(Icons.Rounded.Delete, "Delete", tint = Color(0xFFFF7A7A))
                    }
                }
            }

            // ── Bottom chrome: Edit · Scan · Slideshow · Wallpaper ─────────
            AnimatedVisibility(
                visible  = chromeVisible && !slideshowOn,
                enter    = slideInVertically(initialOffsetY = { it }, animationSpec = Motion.expressive()) +
                           fadeIn(Motion.standard()),
                exit     = slideOutVertically(targetOffsetY = { it }, animationSpec = Motion.standard()) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 18.dp)
                        .clip(ChromeShape)
                        .liquidGlass(alpha = 0.50f)
                        .opticalGlass(intensity = 0.82f)
                        .liquidGlassBorder(ChromeShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomAction("Edit", Icons.Rounded.Edit) { onEdit(photoId) }
                    Spacer(Modifier.width(14.dp))
                    BottomAction(
                        label = if (scanInProgress) "Scanning…" else "Scan codes",
                        icon = Icons.Rounded.QrCodeScanner,
                        enabled = !scanInProgress,
                    ) {
                        scanInProgress = true
                        scope.launch {
                            try {
                                val results = scanPhotoBarcodes(context, photoUri)
                                if (results.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        "No codes found",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    scannedBarcodes = results
                                    scanResultsVisible = true
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                Log.e("PhotoViewer", "Barcode scan failed", error)
                                Toast.makeText(
                                    context,
                                    "Couldn't scan this photo",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } finally {
                                scanInProgress = false
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    BottomAction("Slideshow", Icons.Rounded.PlayArrow, enabled = slidePhotos.isNotEmpty()) {
                        slideshowOn = true
                        chromeVisible = false
                    }
                    Spacer(Modifier.width(14.dp))
                    BottomAction("Wallpaper", Icons.Rounded.Wallpaper) {
                        // System "Set as" flow — free OS intent, user picks the target.
                        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                            addCategory(Intent.CATEGORY_DEFAULT)
                            setDataAndType(photoUri, "image/*")
                            putExtra("mimeType", "image/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(intent, "Set as wallpaper"))
                        }
                    }
                }
            }

            // ── Ken-Burns slideshow overlay ─────────────────────────────────
            if (slideshowOn && slidePhotos.isNotEmpty()) {
                SlideshowOverlay(
                    photos       = slidePhotos,
                    startId      = photoId,
                    onExit       = {
                        slideshowOn   = false
                        chromeVisible = true
                    },
                )
            }

            MediaDetailsSheet(
                visible   = detailsVisible,
                video     = photo,
                onDismiss = { detailsVisible = false },
            )

            BarcodeResultsSheet(
                visible = scanResultsVisible,
                barcodes = scannedBarcodes,
                onDismiss = { scanResultsVisible = false },
            )
        }
    }
}

@Composable
private fun PhotoFavoriteButton(isFavorite: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.78f, spec = Motion.snappy())
            .size(46.dp)
            .favoriteBurst(isFavorite)
            .clip(CircleShape)
            .background(
                if (isFavorite) Color(0xFFFF5C8A).copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.10f)
            )
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = { runCatching { onToggle() } },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState    = isFavorite,
            transitionSpec = {
                scaleIn(Motion.bouncy()) + fadeIn(Motion.snappy()) togetherWith
                    scaleOut(Motion.snappy()) + fadeOut(Motion.snappy())
            },
            label = "photo-heart",
        ) { fav ->
            Icon(
                imageVector        = if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (fav) "Remove favorite" else "Add favorite",
                tint               = if (fav) Color(0xFFFF5C8A) else Color.White,
                modifier           = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun BottomAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.35f },
    ) {
        BouncyIconButton(onClick = { if (enabled) onClick() }, size = 46.dp) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.70f))
    }
}

/**
 * Ken-Burns slideshow: every 4 s the next photo crossfades in while slowly
 * zooming (alternating in/out, alternating drift direction) — the classic
 * "living photos" effect built from two stacked AsyncImages and one clock.
 */
@Composable
private fun SlideshowOverlay(
    photos: List<Video>,
    startId: Long,
    onExit: () -> Unit,
) {
    var index by remember {
        mutableIntStateOf(photos.indexOfFirst { it.id == startId }.coerceAtLeast(0))
    }
    // Advance the reel.
    LaunchedEffect(photos) {
        while (true) {
            delay(4000L)
            index = (index + 1) % photos.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onExit,
            ),
    ) {
        AnimatedContent(
            targetState    = index,
            transitionSpec = {
                fadeIn(tween(durationMillis = 1200)) togetherWith
                    fadeOut(tween(durationMillis = 1200))
            },
            label = "slideshow-frame",
        ) { i ->
            val photo = photos[i]
            // One slow zoom per slide; direction alternates so motion never repeats.
            val zoomIn = i % 2 == 0
            val driftX = if (i % 4 < 2) 1f else -1f
            val progress = remember(i) { Animatable(0f) }
            LaunchedEffect(i) {
                progress.animateTo(1f, animationSpec = tween(durationMillis = 5200))
            }
            AsyncImage(
                model              = photo.uri,
                contentDescription = photo.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        val sc = if (zoomIn) 1.06f + 0.14f * p else 1.20f - 0.14f * p
                        scaleX = sc; scaleY = sc
                        translationX = driftX * 28f * p
                        translationY = -16f * p
                    },
            )
        }
        // Exit chip.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(16.dp)
                .clip(RoundedCornerShape(50))
                .liquidGlass(alpha = 0.45f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = onExit,
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Close, "Stop slideshow", tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("${photos.size} photos", style = MaterialTheme.typography.bodyMedium.merge(TabularFigures), color = Color.White)
        }
    }
}

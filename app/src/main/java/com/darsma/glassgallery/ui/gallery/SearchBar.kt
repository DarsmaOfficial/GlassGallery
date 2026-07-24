@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.darsma.glassgallery.ui.gallery

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.ui.components.BouncyIconButton
import com.darsma.glassgallery.ui.components.Motion
import com.darsma.glassgallery.ui.components.liquidGlassBorder
import com.darsma.glassgallery.ui.components.pressBounce
import kotlinx.coroutines.delay
import kotlin.math.max

private enum class SearchBodyState { SEARCHING, RESULTS, EMPTY }

/**
 * v24 Dynamic Island search.
 *
 * The capsule and result chamber are laid out independently. Only the small
 * capsule changes width during the morph; the result chamber keeps a stable
 * final layout and reveals its surface/content on GPU layers. This avoids the
 * expensive full-grid remeasure that made v23 stutter while typing.
 */
@Composable
fun DynamicIslandSearch(
    active: Boolean,
    enabled: Boolean,
    query: String,
    results: List<Video>,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onResultClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val imeInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val hasQuery = query.isNotBlank()

        val openProgress = remember { Animatable(if (active) 1f else 0f) }
        val expandProgress = remember { Animatable(if (active && hasQuery) 1f else 0f) }

        // One deterministic choreography prevents competing animations from
        // cancelling one another when the first character is typed quickly.
        LaunchedEffect(active, hasQuery) {
            when {
                !active -> {
                    expandProgress.animateTo(0f, Motion.settle())
                    openProgress.animateTo(0f, Motion.spatial())
                }
                hasQuery -> {
                    openProgress.animateTo(1f, Motion.spatial())
                    // The liquid silhouette carries the expressiveness; the
                    // large surface itself uses a calmer spring to avoid the
                    // clipped overshoot that can read as dropped frames.
                    expandProgress.animateTo(1f, Motion.spatial())
                }
                else -> {
                    openProgress.animateTo(1f, Motion.spatial())
                    expandProgress.animateTo(0f, Motion.settle())
                }
            }
        }

        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val haptic = LocalHapticFeedback.current
        LaunchedEffect(active) {
            if (active) {
                // Let the orb complete the first part of its journey before the
                // keyboard begins its own inset animation.
                delay(92L)
                runCatching { focusRequester.requestFocus() }
            } else {
                keyboard?.hide()
            }
        }

        val open = openProgress.value.coerceIn(0f, 1f)
        val expand = expandProgress.value.coerceIn(0f, 1f)
        val widthStage = liquidSegment(expand, 0f, 0.48f)
        val bridgeStage = liquidSegment(expand, 0.08f, 0.62f)
        val panelStage = liquidSegment(expand, 0.18f, 1f)
        val contentStage = liquidSegment(expand, 0.52f, 1f)
        val fieldAlpha = liquidSegment(open, 0.24f, 1f)

        val orbSize = 38.dp
        val capsuleHeight = 56.dp
        val focusedWidth = 252.dp
        val finalWidth = (maxWidth - 20.dp).coerceAtMost(680.dp).coerceAtLeast(focusedWidth)
        val capsuleWidth = lerp(lerp(orbSize, focusedWidth, open), finalWidth, widthStage)
        val capsuleRadius = lerp(orbSize / 2, capsuleHeight / 2, open)
        val capsuleShape = RoundedCornerShape(capsuleRadius)
        val panelShape = RoundedCornerShape(30.dp)

        // Respect the live IME inset. Results remain visible while typing and
        // no longer appear only after Enter hides the keyboard.
        val availablePanelHeight = (maxHeight - topInset - imeInset - 96.dp)
            .coerceIn(196.dp, 520.dp)
        // Height is stable across query/result changes. It only follows the
        // keyboard inset, so typing never remeasures the chamber every frame.
        val targetPanelHeight = availablePanelHeight
        val panelHeight by animateDpAsState(
            targetValue = targetPanelHeight,
            animationSpec = Motion.settle(),
            label = "search-ime-aware-panel-height",
        )

        // Matches the header's reserved 38dp search slot when closed.
        val inactiveCenterOffset = maxWidth / 2 - 33.dp
        val capsuleX = inactiveCenterOffset * (1f - open)
        val capsuleY = topInset + 10.dp + 2.dp * open
        val panelY = capsuleY + capsuleHeight - 8.dp
        val availability by animateFloatAsState(
            targetValue = if (enabled || active) 1f else 0f,
            animationSpec = Motion.standard(),
            label = "search-availability",
        )

        if (active || open > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = open }
                    .background(Color(0xFF040309).copy(alpha = 0.955f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = active,
                        onClick = onClose,
                    ),
            ) {
                SearchBackdrop(open, expand, Modifier.fillMaxSize())
            }
        }

        // Stable result-chamber surface. Its final dimensions never animate on
        // each keystroke; only a GPU transform reveals the material.
        if (hasQuery || panelStage > 0.001f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = panelY)
                    .width(finalWidth)
                    .height(panelHeight)
                    .zIndex(1f)
                    .graphicsLayer {
                        alpha = panelStage
                        scaleX = 0.84f + 0.16f * panelStage
                        scaleY = 0.055f + 0.945f * panelStage
                        translationY = (1f - panelStage) * -16f
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .shadow(26.dp, panelShape, clip = false)
                    .clip(panelShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF262032).copy(alpha = 0.99f),
                                Color(0xFF111019).copy(alpha = 0.995f),
                                Color(0xFF07070C),
                            )
                        )
                    ),
            ) {
                SearchSurfaceChrome(expand, Modifier.fillMaxSize())
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = panelY)
                    .width(finalWidth)
                    .height(panelHeight)
                    .zIndex(2f)
                    .clip(panelShape)
                    .graphicsLayer {
                        alpha = contentStage
                        translationY = (1f - contentStage) * 18f
                    },
            ) {
                SearchResultsChamber(
                    query = query,
                    results = results,
                    searching = searching,
                    progress = contentStage,
                    onResultClick = onResultClick,
                    modifier = Modifier.fillMaxSize().padding(top = 10.dp),
                )
            }
        }

        // A bezier bridge stretches from the capsule into the result chamber,
        // creating the connected liquid silhouette rather than two boxes.
        LiquidSearchBridge(
            capsuleWidth = capsuleWidth,
            progress = bridgeStage,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = capsuleY + capsuleHeight - 15.dp)
                .width(finalWidth)
                .height(35.dp)
                .zIndex(2.5f),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = capsuleX, y = capsuleY)
                .width(capsuleWidth)
                .height(lerp(orbSize, capsuleHeight, open))
                .zIndex(3f)
                .graphicsLayer {
                    alpha = availability
                    scaleX = 0.90f + 0.10f * availability
                    scaleY = 0.90f + 0.10f * availability
                }
                .shadow(18.dp, capsuleShape, clip = false)
                .clip(capsuleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF30273E).copy(alpha = 0.99f),
                            Color(0xFF15131D).copy(alpha = 0.995f),
                            Color(0xFF09090E),
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled && !active,
                ) {
                    if (!active) {
                        runCatching {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpen()
                        }
                    }
                },
        ) {
            SearchSurfaceChrome(open + expand * 0.35f, Modifier.fillMaxSize())
            SearchFieldRow(
                active = active,
                query = query,
                fieldAlpha = fieldAlpha,
                focusRequester = focusRequester,
                onQueryChange = onQueryChange,
                onOpen = {
                    runCatching {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpen()
                    }
                },
                onClose = onClose,
                onSearchIme = { keyboard?.hide() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SearchFieldRow(
    active: Boolean,
    query: String,
    fieldAlpha: Float,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onSearchIme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .padding(start = if (active) 13.dp else 9.dp)
                .size(if (active) 30.dp else 20.dp)
                .clip(CircleShape)
                .background(if (active) Color.White.copy(alpha = 0.065f) else Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !active,
                    onClick = onOpen,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Search,
                if (active) null else "Open search",
                tint = Color.White.copy(alpha = 0.97f),
                modifier = Modifier.size(if (active) 18.dp else 20.dp),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 4.dp)
                .graphicsLayer {
                    alpha = fieldAlpha
                    translationX = (1f - fieldAlpha) * 14f
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    "Search photos, videos, text, labels",
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = { onQueryChange(it.take(96)) },
                enabled = active,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                cursorBrush = SolidColor(Color(0xFFC8B5FF)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // Enter is only a keyboard-dismiss action. Filtering happens
                // in real time from onValueChange and never waits for IME submit.
                keyboardActions = KeyboardActions(onSearch = { onSearchIme() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }

        if (active && query.isNotEmpty()) {
            val clearInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .pressBounce(clearInteraction, 0.82f, Motion.snappy(), haptic = false)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.075f))
                    .clickable(
                        interactionSource = clearInteraction,
                        indication = null,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onQueryChange("")
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    "Clear search",
                    tint = Color.White.copy(alpha = 0.76f),
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(5.dp))
        }

        if (active || fieldAlpha > 0.45f) {
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = fieldAlpha
                    scaleX = 0.78f + 0.22f * fieldAlpha
                    scaleY = 0.78f + 0.22f * fieldAlpha
                },
            ) {
                BouncyIconButton(
                    onClick = onClose,
                    size = 36.dp,
                    background = Color.White.copy(alpha = 0.075f),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        "Close search",
                        tint = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun SearchResultsChamber(
    query: String,
    results: List<Video>,
    searching: Boolean,
    progress: Float,
    onResultClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Empty is shown only after the complete ranking pass finishes. This
    // removes the false blank/"nothing found" flash during rapid typing.
    var showEmpty by remember(query) { mutableStateOf(false) }
    LaunchedEffect(query, results.isEmpty(), searching) {
        if (!searching && results.isEmpty()) {
            delay(72L)
            showEmpty = true
        } else {
            showEmpty = false
        }
    }
    val bodyState = when {
        results.isNotEmpty() -> SearchBodyState.RESULTS
        searching || !showEmpty -> SearchBodyState.SEARCHING
        else -> SearchBodyState.EMPTY
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveSearchGlyph(progress, Modifier.size(28.dp))
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Only the count label transforms. The query subtitle stays in
                // place while typing, so every key no longer restarts a full
                // two-line AnimatedContent measurement and slide transition.
                AnimatedContent(
                    targetState = results.size to searching,
                    transitionSpec = {
                        (slideInVertically(
                            animationSpec = Motion.settle(),
                            initialOffsetY = { it / 2 },
                        ) + fadeIn(Motion.standard())) togetherWith
                            (slideOutVertically(
                                animationSpec = Motion.snappy(),
                                targetOffsetY = { -it / 2 },
                            ) + fadeOut(Motion.snappy()))
                    },
                    label = "live-search-count",
                ) { (count, refining) ->
                    Text(
                        when {
                            count == 0 && refining -> "Searching…"
                            count == 0 -> if (showEmpty) "No matches" else "Preparing…"
                            count == 1 && refining -> "1 instant match"
                            count == 1 -> "1 live match"
                            refining -> "$count instant matches"
                            else -> "$count live matches"
                        },
                        color = Color.White.copy(alpha = 0.97f),
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Text(
                    when {
                        results.isEmpty() && searching ->
                            "Searching titles, albums, and photo content locally"
                        results.isEmpty() && showEmpty ->
                            "Try another title, album, label, or visible text"
                        searching -> "Refining relevance · “${query.trim()}”"
                        else -> "Updates as you type · “${query.trim()}”"
                    },
                    color = Color.White.copy(alpha = 0.43f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        AnimatedContent(
            targetState = bodyState,
            transitionSpec = {
                (fadeIn(Motion.standard()) + scaleIn(Motion.settle(), initialScale = 0.975f)) togetherWith
                    (fadeOut(Motion.snappy()) + scaleOut(Motion.snappy(), targetScale = 0.985f))
            },
            label = "live-search-body-transform",
            modifier = Modifier.fillMaxSize(),
        ) { state ->
            when (state) {
                SearchBodyState.SEARCHING -> SearchPreparingState(
                    query = query,
                    progress = progress,
                    modifier = Modifier.fillMaxSize(),
                )

                SearchBodyState.EMPTY -> EmptySearchResult(query, Modifier.fillMaxSize())

                SearchBodyState.RESULTS -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = results,
                        key = { _, media -> media.id },
                        contentType = { _, media -> media.isVideo },
                    ) { index, media ->
                        val staggerStart = (index.coerceAtMost(9) * 0.035f).coerceAtMost(0.30f)
                        val itemProgress = liquidSegment(progress, staggerStart, 1f)
                        SearchResultCard(
                            media = media,
                            revealProgress = itemProgress,
                            index = index,
                            onClick = { onResultClick(media) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = Motion.standard(),
                                placementSpec = Motion.settle(),
                                fadeOutSpec = Motion.snappy(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    media: Video,
    revealProgress: Float,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(19.dp)
    val imageRequest = remember(media.thumbnailUri) {
        ImageRequest.Builder(context)
            .data(media.thumbnailUri)
            // The card itself already animates; an additional bitmap
            // crossfade made first-result frames noticeably heavier.
            .crossfade(false)
            .build()
    }
    val itemEntrance = remember(media.id) { Animatable(0f) }
    LaunchedEffect(media.id) {
        // LazyVerticalGrid composes only the visible cards, so this finite
        // stagger is inexpensive. New matches condense out of the island;
        // retained matches simply glide to their new ranked position.
        delay((index.coerceAtMost(7) * 18L))
        itemEntrance.animateTo(1f, Motion.settle())
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                val p = minOf(revealProgress, itemEntrance.value).coerceIn(0f, 1f)
                alpha = p
                translationY = (1f - p) * (20f + (index % 2) * 5f)
                scaleX = 0.91f + 0.09f * p
                scaleY = 0.91f + 0.09f * p
                rotationZ = (1f - p) * if (index % 2 == 0) -1.1f else 1.1f
            }
            .pressBounce(interaction, 0.965f, Motion.snappy(), haptic = false)
            .fillMaxWidth()
            .aspectRatio(1.10f)
            .clip(shape)
            .background(Color(0xFF17131F))
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = media.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.White.copy(alpha = 0.025f),
                        0.48f to Color.Transparent,
                        1.00f to Color.Black.copy(alpha = 0.84f),
                    )
                )
            )
        )
        if (media.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.38f))
                    .liquidGlassBorder(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(21.dp).padding(start = 1.dp),
                )
            }
        }
        Text(
            media.title,
            color = Color.White,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (media.isVideo) 0.68f else 0.92f)
                .padding(start = 9.dp, bottom = 9.dp),
        )
        if (media.isVideo) {
            Text(
                formatSearchDuration(media.duration),
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.60f))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
            )
        }
        SearchCardRim(shape, Modifier.fillMaxSize())
    }
}

@Composable
private fun SearchPreparingState(
    query: String,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val safeQuery = query.trim().take(30)
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LiveSearchGlyph(progress, Modifier.size(42.dp))
        Spacer(Modifier.height(9.dp))
        Text(
            "Finding “$safeQuery”",
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "Local results are condensing into place",
            color = Color.White.copy(alpha = 0.38f),
            fontSize = 10.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(15.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchPlaceholderCard(0, progress, Modifier.weight(1f))
            SearchPlaceholderCard(1, progress, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SearchPlaceholderCard(
    index: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val p = liquidSegment(progress, index * 0.055f, 1f)
    Box(
        modifier = modifier
            .height(64.dp)
            .graphicsLayer {
                alpha = 0.30f + 0.70f * p
                scaleX = 0.94f + 0.06f * p
                scaleY = 0.88f + 0.12f * p
                translationY = (1f - p) * (8f + index * 2f)
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.085f),
                        Color(0xFFB7A0FF).copy(alpha = 0.045f),
                        Color.White.copy(alpha = 0.025f),
                    )
                )
            )
            .liquidGlassBorder(shape),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 9.dp, bottom = 9.dp)
                .width(if (index % 2 == 0) 58.dp else 76.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.13f)),
        )
    }
}

@Composable
private fun EmptySearchResult(query: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LiveSearchGlyph(1f, Modifier.size(48.dp))
        Spacer(Modifier.height(11.dp))
        Text(
            "Nothing found",
            color = Color.White.copy(alpha = 0.93f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "No media matches “${query.trim()}”",
            color = Color.White.copy(alpha = 0.44f),
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveSearchGlyph(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val p = progress.coerceIn(0f, 1f)
        val c = center
        val radius = size.minDimension * 0.42f
        drawCircle(
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color.White.copy(alpha = 0.90f),
                    0.25f to Color(0xFFC3AFFF).copy(alpha = 0.70f),
                    0.62f to Color(0xFF78DFFF).copy(alpha = 0.18f + 0.10f * p),
                    1.00f to Color.Transparent,
                ),
                center = c,
                radius = radius,
            ),
            radius = radius,
            center = c,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.34f + 0.20f * p),
            radius = size.minDimension * (0.20f + 0.035f * p),
            center = c,
            style = Stroke(1.dp.toPx()),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.94f),
            radius = size.minDimension * 0.075f,
            center = c,
        )
    }
}

@Composable
private fun SearchBackdrop(progress: Float, expand: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val alpha = progress.coerceIn(0f, 1f)
        val bloom = expand.coerceIn(0f, 1f)
        val top = Offset(size.width * (0.42f + 0.08f * bloom), size.height * 0.035f)
        val radius = max(size.width, size.height) * (0.34f + bloom * 0.08f)
        drawCircle(
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color(0xFFB79CFF).copy(alpha = 0.13f * alpha),
                    0.34f to Color(0xFF6ECFFF).copy(alpha = 0.050f * alpha),
                    1.00f to Color.Transparent,
                ),
                center = top,
                radius = radius,
            ),
            radius = radius,
            center = top,
        )
        val lower = Offset(size.width * 0.76f, size.height * 0.72f)
        drawCircle(
            Brush.radialGradient(
                listOf(
                    Color(0xFFFF6FB7).copy(alpha = 0.034f * alpha * bloom),
                    Color.Transparent,
                ),
                center = lower,
                radius = size.width * 0.58f,
            ),
            radius = size.width * 0.58f,
            center = lower,
        )
    }
}

@Composable
private fun SearchSurfaceChrome(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val p = progress.coerceIn(0f, 1.35f)
        drawRoundRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color.White.copy(alpha = 0.14f),
                    0.32f to Color(0xFFB8E9FF).copy(alpha = 0.050f),
                    0.72f to Color(0xFFC8A8FF).copy(alpha = 0.028f * p),
                    1.00f to Color.Transparent,
                ),
                center = Offset(size.width * (0.14f + 0.08f * p), 0f),
                radius = max(size.width, size.height) * 0.84f,
            ),
            cornerRadius = CornerRadius(size.minDimension / 2f, size.minDimension / 2f),
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.27f),
                    Color.White.copy(alpha = 0.055f),
                    Color(0xFFA98FFF).copy(alpha = 0.15f),
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            cornerRadius = CornerRadius(size.minDimension / 2f, size.minDimension / 2f),
            style = Stroke(1.dp.toPx()),
        )
    }
}

@Composable
private fun SearchCardRim(shape: RoundedCornerShape, modifier: Modifier = Modifier) {
    Box(modifier = modifier.liquidGlassBorder(shape))
}

@Composable
private fun LiquidSearchBridge(
    capsuleWidth: Dp,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0.001f) return@Canvas

        val centerX = size.width / 2f
        val topHalf = capsuleWidth.toPx() * (0.13f + 0.09f * p)
        val bottomHalf = size.width * (0.08f + 0.39f * p)
        val topY = 0f
        val bottomY = size.height
        val path = Path().apply {
            moveTo(centerX - topHalf, topY)
            cubicTo(
                centerX - topHalf * 1.08f,
                size.height * 0.30f,
                centerX - bottomHalf * 0.82f,
                size.height * 0.58f,
                centerX - bottomHalf,
                bottomY,
            )
            lineTo(centerX + bottomHalf, bottomY)
            cubicTo(
                centerX + bottomHalf * 0.82f,
                size.height * 0.58f,
                centerX + topHalf * 1.08f,
                size.height * 0.30f,
                centerX + topHalf,
                topY,
            )
            close()
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF17131F).copy(alpha = 0.98f * p),
                    Color(0xFF0B0A10).copy(alpha = 0.99f * p),
                )
            ),
        )
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.12f * p),
                    Color.Transparent,
                    Color(0xFFB89DFF).copy(alpha = 0.08f * p),
                )
            ),
            style = Stroke(0.8.dp.toPx()),
        )
    }
}

private fun liquidSegment(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun formatSearchDuration(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

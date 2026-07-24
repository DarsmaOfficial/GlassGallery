@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.darsma.glassgallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.ui.gallery.GalleryScreen
import com.darsma.glassgallery.ui.gallery.GalleryViewModel
import com.darsma.glassgallery.ui.photo.PhotoViewerScreen
import com.darsma.glassgallery.ui.player.PlayerScreen
import com.darsma.glassgallery.ui.trim.VideoTrimScreen
import com.darsma.glassgallery.ui.trash.TrashScreen
import com.darsma.glassgallery.ui.editor.PhotoEditorScreen
import com.darsma.glassgallery.ui.components.Motion
import com.darsma.glassgallery.ui.theme.GlassGalleryTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlassGalleryTheme {
                GlassGalleryNavHost()
            }
        }
    }
}

@Composable
private fun GlassGalleryNavHost() {
    val navController    = rememberNavController()
    val galleryViewModel: GalleryViewModel = viewModel()

    SharedTransitionLayout {
        NavHost(
            navController    = navController,
            startDestination = "gallery",
        ) {
            // Gallery — gently scales + fades. When returning from the player
            // (popEnter) it eases back up to full size; this pairs with the
            // predictive-back gesture so the gallery "receives" the morph.
            composable(
                route = "gallery",
                enterTransition     = {
                    fadeIn(Motion.standard())
                },
                exitTransition      = {
                    scaleOut(targetScale = 0.92f, animationSpec = Motion.standard()) +
                        fadeOut(Motion.snappy())
                },
                popEnterTransition  = {
                    scaleIn(initialScale = 0.92f, animationSpec = Motion.expressive()) +
                        fadeIn(Motion.standard())
                },
                popExitTransition   = {
                    fadeOut(Motion.snappy())
                },
            ) {
                GalleryScreen(
                    viewModel               = galleryViewModel,
                    animatedVisibilityScope = this,
                    sharedTransitionScope   = this@SharedTransitionLayout,
                    onVideoClick = { media: Video ->
                        if (media.isVideo) {
                            galleryViewModel.selectVideo(media)
                            navController.navigate("player/${media.id}")
                        } else {
                            navController.navigate("photo/${media.id}")
                        }
                    },
                    onMiniPlayerClick = {
                        galleryViewModel.currentVideo.value?.id?.let { id ->
                            navController.navigate("player/$id")
                        }
                    },
                    onOpenTrash = { navController.navigate("trash") },
                )
            }

            // Player — rises from the bottom, and on back-press slides back down.
            composable(
                route     = "player/{videoId}",
                arguments = listOf(navArgument("videoId") { type = NavType.LongType }),
                enterTransition     = {
                    // Zoom-morph: the player blooms up from 86% with a soft
                    // overshoot while rising slightly — reads as the content
                    // morphing open rather than a screen sliding in.
                    scaleIn(
                        initialScale  = 0.86f,
                        animationSpec = Motion.expressive(),
                    ) + slideInVertically(
                        initialOffsetY = { it / 10 },
                        animationSpec  = Motion.standard(),
                    ) + fadeIn(Motion.standard())
                },
                popExitTransition   = {
                    slideOutVertically(
                        targetOffsetY = { it / 5 },
                        animationSpec = Motion.standard(),
                    ) + fadeOut(Motion.snappy())
                },
            ) { backStackEntry ->
                val videoId = backStackEntry.arguments?.getLong("videoId") ?: return@composable
                PlayerScreen(
                    videoId                 = videoId,
                    galleryViewModel        = galleryViewModel,
                    animatedVisibilityScope = this,
                    sharedTransitionScope   = this@SharedTransitionLayout,
                    onBack                  = { navController.popBackStack() },
                    onTrim                  = { id -> navController.navigate("trim/$id") },
                )
            }

            composable(
                route     = "trim/{videoId}",
                arguments = listOf(navArgument("videoId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val trimId = backStackEntry.arguments?.getLong("videoId") ?: return@composable
                VideoTrimScreen(
                    videoId = trimId,
                    onBack  = { navController.popBackStack() },
                    onSaved = {
                        galleryViewModel.reload()
                        navController.popBackStack()
                    },
                )
            }

            // Photo viewer — transitions are intentionally minimal fades:
            // the grid-card → fullscreen shared-element morph carries the
            // entire entrance and exit.
            composable(
                route     = "photo/{photoId}",
                arguments = listOf(navArgument("photoId") { type = NavType.LongType }),
                enterTransition   = { fadeIn(Motion.standard()) },
                popExitTransition = { fadeOut(Motion.snappy()) },
            ) { backStackEntry ->
                val photoId = backStackEntry.arguments?.getLong("photoId") ?: return@composable
                PhotoViewerScreen(
                    photoId                 = photoId,
                    galleryViewModel        = galleryViewModel,
                    animatedVisibilityScope = this,
                    sharedTransitionScope   = this@SharedTransitionLayout,
                    onBack                  = { navController.popBackStack() },
                    onEdit                  = { id -> navController.navigate("edit/$id") },
                )
            }

            // Photo editor — scales up out of the viewer.
            composable(
                route     = "edit/{photoId}",
                arguments = listOf(navArgument("photoId") { type = NavType.LongType }),
                enterTransition   = {
                    scaleIn(
                        initialScale  = 0.94f,
                        animationSpec = Motion.expressive(),
                    ) + fadeIn(Motion.standard())
                },
                popExitTransition = {
                    scaleOut(
                        targetScale   = 0.96f,
                        animationSpec = Motion.standard(),
                    ) + fadeOut(Motion.snappy())
                },
            ) { backStackEntry ->
                val editId = backStackEntry.arguments?.getLong("photoId") ?: return@composable
                PhotoEditorScreen(
                    photoId = editId,
                    onBack  = { navController.popBackStack() },
                    onSaved = {
                        galleryViewModel.reload()
                        navController.popBackStack()
                    },
                )
            }

            // Recently Deleted — rises from the bottom like a sheet.
            composable(
                route = "trash",
                enterTransition   = {
                    slideInVertically(
                        initialOffsetY = { it / 6 },
                        animationSpec  = Motion.expressive(),
                    ) + fadeIn(Motion.standard())
                },
                popExitTransition = {
                    slideOutVertically(
                        targetOffsetY = { it / 5 },
                        animationSpec = Motion.standard(),
                    ) + fadeOut(Motion.snappy())
                },
            ) {
                TrashScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

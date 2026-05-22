@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.darsma.glassgallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
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
import com.darsma.glassgallery.ui.player.PlayerScreen
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
                    fadeIn(spring(stiffness = 300f))
                },
                exitTransition      = {
                    scaleOut(targetScale = 0.92f, animationSpec = spring(stiffness = 300f)) +
                        fadeOut(spring(stiffness = 400f))
                },
                popEnterTransition  = {
                    scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)) +
                        fadeIn(spring(stiffness = 300f))
                },
                popExitTransition   = {
                    fadeOut(spring(stiffness = 400f))
                },
            ) {
                GalleryScreen(
                    viewModel               = galleryViewModel,
                    animatedVisibilityScope = this,
                    sharedTransitionScope   = this@SharedTransitionLayout,
                    onVideoClick = { video: Video ->
                        galleryViewModel.selectVideo(video)
                        navController.navigate("player/${video.id}")
                    },
                    onMiniPlayerClick = {
                        galleryViewModel.currentVideo.value?.id?.let { id ->
                            navController.navigate("player/$id")
                        }
                    },
                )
            }

            // Player — rises from the bottom, and on back-press slides back down.
            composable(
                route     = "player/{videoId}",
                arguments = listOf(navArgument("videoId") { type = NavType.LongType }),
                enterTransition     = {
                    slideInVertically(
                        initialOffsetY = { it / 6 },
                        animationSpec  = spring(dampingRatio = 0.85f, stiffness = 300f),
                    ) + fadeIn(spring(stiffness = 300f))
                },
                popExitTransition   = {
                    slideOutVertically(
                        targetOffsetY = { it / 5 },
                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 280f),
                    ) + fadeOut(spring(stiffness = 400f))
                },
            ) { backStackEntry ->
                val videoId = backStackEntry.arguments?.getLong("videoId") ?: return@composable
                PlayerScreen(
                    videoId                 = videoId,
                    galleryViewModel        = galleryViewModel,
                    animatedVisibilityScope = this,
                    sharedTransitionScope   = this@SharedTransitionLayout,
                    onBack                  = { navController.popBackStack() },
                )
            }
        }
    }
}

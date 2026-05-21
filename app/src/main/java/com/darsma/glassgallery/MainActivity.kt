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
    val navController = rememberNavController()
    val galleryViewModel: GalleryViewModel = viewModel()

    SharedTransitionLayout {
        NavHost(
            navController    = navController,
            startDestination = "gallery",
            // Gentle cross-fade; the shared element carries the visual motion.
            enterTransition = { fadeIn(spring(stiffness = 200f)) },
            exitTransition  = { fadeOut(spring(stiffness = 200f)) },
            popEnterTransition = { fadeIn(spring(stiffness = 200f)) },
            popExitTransition  = { fadeOut(spring(stiffness = 200f)) },
        ) {
            composable("gallery") {
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
                    }
                )
            }
            composable(
                route     = "player/{videoId}",
                arguments = listOf(navArgument("videoId") { type = NavType.LongType })
            ) { backStackEntry ->
                val videoId = backStackEntry.arguments?.getLong("videoId") ?: return@composable
                PlayerScreen(
                    videoId                 = videoId,
                    animatedVisibilityScope = this,
                    sharedTransitionScope   = this@SharedTransitionLayout,
                    onBack                  = { navController.popBackStack() }
                )
            }
        }
    }
}

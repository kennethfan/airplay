package com.airplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.airplay.ui.screen.HomeScreen
import com.airplay.ui.screen.PlayerScreen
import com.airplay.ui.theme.AirPlayTheme
import com.airplay.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        enableEdgeToEdge()
        setContent {
            AirPlayTheme {
                AirPlayContent(viewModel)
            }
        }
    }
}

@Composable
fun AirPlayContent(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onCast = { video, device ->
                    viewModel.castVideo(video, device)
                    navController.navigate("player")
                },
                onCastMultiple = { videos, device ->
                    viewModel.castVideos(videos, device)
                    navController.navigate("player")
                }
            )
        }

        composable("player") {
            val currentVideo = viewModel.currentQueueVideo
            if (currentVideo != null) {
                PlayerScreen(
                    viewModel = viewModel,
                    video = currentVideo,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

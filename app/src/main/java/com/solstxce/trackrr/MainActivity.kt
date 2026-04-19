package com.solstxce.trackrr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.solstxce.trackrr.ui.screen.RomDetailScreen
import com.solstxce.trackrr.ui.screen.RomListScreen
import com.solstxce.trackrr.ui.theme.TrackrrTheme
import com.solstxce.trackrr.viewmodel.RomViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrackrrApp()
        }
    }
}

@Composable
fun TrackrrApp() {
    val navController = rememberNavController()
    val romViewModel: RomViewModel = viewModel()

    TrackrrTheme {
        NavHost(navController = navController, startDestination = "list") {
            composable("list") {
                RomListScreen(
                    viewModel = romViewModel,
                    onReleaseClick = { release ->
                        navController.navigate("detail/${release.id}")
                    }
                )
            }
            composable("detail/{releaseId}") { backStackEntry ->
                val releaseId = backStackEntry.arguments?.getString("releaseId")?.toLongOrNull()
                if (releaseId != null) {
                    RomDetailScreen(
                        releaseId = releaseId,
                        viewModel = romViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

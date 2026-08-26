package com.localphoto360.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.localphoto360.app.capture.CameraCaptureScreen
import com.localphoto360.app.capture.SphereCaptureScreen
import com.localphoto360.app.gallery.GalleryScreen
import com.localphoto360.app.ui.theme.LocalPhoto360Theme
import com.localphoto360.app.viewer.SphereViewerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalPhoto360Theme {
                LocalPhoto360Nav(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun LocalPhoto360Nav(modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = "gallery",
        modifier = modifier,
    ) {
        composable("gallery") {
            GalleryScreen(
                onOpenPhoto = { id -> nav.navigate("viewer/$id") },
                onOpenCamera = { nav.navigate("camera") },
            )
        }
        composable("camera") {
            CameraCaptureScreen(
                onBack = { nav.popBackStack() },
                onOpenSphereCapture = { nav.navigate("sphere-capture") },
                onCaptured = { id ->
                    nav.popBackStack()
                    nav.navigate("viewer/$id")
                },
            )
        }
        composable("sphere-capture") {
            SphereCaptureScreen(
                onBack = { nav.popBackStack() },
                onFinished = { id ->
                    nav.popBackStack("gallery", inclusive = false)
                    nav.navigate("viewer/$id")
                },
            )
        }
        composable(
            route = "viewer/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            SphereViewerScreen(
                photoId = id,
                onBack = { nav.popBackStack() },
                onDeleted = { nav.popBackStack() },
            )
        }
    }
}

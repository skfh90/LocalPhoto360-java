package com.localphoto360.app.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.PanoramaPhotosphere
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.localphoto360.app.photoRepository
import com.localphoto360.app.ui.theme.Gold
import com.localphoto360.app.ui.theme.Night
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CameraCaptureScreen(
    onBack: () -> Unit,
    onOpenSphereCapture: () -> Unit,
    onCaptured: (String) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val scope = rememberCoroutineScope()
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night),
    ) {
        if (!granted) {
            PermissionPane(
                onRequest = { launcher.launch(Manifest.permission.CAMERA) },
                onBack = onBack,
            )
            return
        }

        CameraPreview(modifier = Modifier.fillMaxSize(), imageCapture = imageCapture)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = onOpenSphereCapture) {
                Icon(Icons.Outlined.PanoramaPhotosphere, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("360 capture")
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Still photo",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Snap a frame, or switch to 360 capture to paint a photosphere by turning in place.",
                color = Color(0xCCFFFFFF),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
            }
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .border(4.dp, Gold, CircleShape)
                    .background(if (capturing) Color(0x66FFFFFF) else Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (capturing) {
                    CircularProgressIndicator(color = Night, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                } else {
                    Box(
                        Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(enabled = !capturing) {
                                capturing = true
                                error = null
                                scope.launch {
                                    runCatching {
                                        val bitmap = imageCapture.awaitBitmap(context)
                                        val photo = withContext(Dispatchers.IO) {
                                            context.photoRepository.saveBitmap(
                                                bitmap = bitmap,
                                                displayName = "Camera photo",
                                                photosphere = false,
                                            )
                                        }
                                        photo.id
                                    }.onSuccess(onCaptured)
                                        .onFailure { error = it.message ?: "Capture failed." }
                                    capturing = false
                                }
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPane(onRequest: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Cameraswitch, contentDescription = null, tint = Gold, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Camera access is required", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            "LocalPhoto360 uses the camera to take stills and to capture overlapping frames for a 360 photosphere. Photos stay on this device.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xCCFFFFFF),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Night)) {
            Text("Allow camera")
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onBack) { Text("Not now") }
    }
}

package com.localphoto360.app.viewer

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.ExploreOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.localphoto360.app.data.SpherePhoto
import com.localphoto360.app.photoRepository
import com.localphoto360.app.ui.theme.Gold
import com.localphoto360.app.ui.theme.Night
import com.localphoto360.app.util.OrientationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun SphereViewerScreen(
    photoId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val repo = context.photoRepository
    var photo by remember(photoId) { mutableStateOf<SpherePhoto?>(null) }
    var bitmap by remember(photoId) { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var gyro by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var glView by remember { mutableStateOf<SphereGLView?>(null) }
    val tracker = remember { OrientationTracker(context) }
    val matrix = remember { FloatArray(9) }

    LaunchedEffect(photoId) {
        runCatching {
            val item = repo.get(photoId) ?: error("That photo is no longer on this device.")
            photo = item
            bitmap = withContext(Dispatchers.IO) { repo.decode(item) }
        }.onFailure { error = it.message ?: "Could not open the 360 viewer." }
    }

    DisposableEffect(gyro) {
        if (gyro) tracker.start() else tracker.stop()
        onDispose { tracker.stop() }
    }

    LaunchedEffect(gyro, glView) {
        val view = glView ?: return@LaunchedEffect
        if (!gyro) return@LaunchedEffect
        while (isActive && gyro) {
            tracker.copyRotationMatrix(matrix)
            val yaw = Math.toDegrees(OrientationTracker.yawFromMatrix(matrix).toDouble()).toFloat()
            val pitch = Math.toDegrees(OrientationTracker.pitchFromMatrix(matrix).toDouble()).toFloat()
            view.setOrientation(yaw, pitch)
            delay(16)
        }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        if (bitmap != null) {
            AndroidView(
                factory = { ctx ->
                    SphereGLView(ctx).also { view ->
                        glView = view
                        bitmap?.let(view::setBitmap)
                        view.onResume()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            DisposableEffect(photoId) {
                onDispose { glView?.onPause() }
            }
        }

        if (bitmap == null && error == null) {
            CircularProgressIndicator(color = Gold, modifier = Modifier.align(Alignment.Center))
        }

        error?.let {
            Text(
                it,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(photo?.displayName ?: "360 viewer", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (gyro) "Gyroscope look" else "Drag to look  ·  pinch to zoom",
                    color = Color(0xCCFFFFFF),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row {
                IconButton(
                    onClick = { gyro = !gyro },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (gyro) Gold else Color.White),
                    modifier = Modifier.clip(CircleShape).background(Color(0x66000000)),
                ) {
                    Icon(
                        if (gyro) Icons.Outlined.Explore else Icons.Outlined.ExploreOff,
                        contentDescription = if (gyro) "Disable gyroscope" else "Enable gyroscope",
                    )
                }
                photo?.let { item ->
                    IconButton(
                        onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, repo.shareUri(item))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Share 360 photo"))
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share")
                    }
                    if (!item.isSample) {
                        IconButton(
                            onClick = { confirmDelete = true },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }

        if (photo?.isPhotosphere == false) {
            Text(
                "This is a regular photo mapped onto a sphere. Capture a 360 or import an equirectangular image for a true photosphere.",
                color = Color(0xDDFFFFFF),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(20.dp)
                    .background(Color(0x99000000), MaterialTheme.shapes.medium)
                    .padding(12.dp),
            )
        }
    }

    if (confirmDelete && photo != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this photo?") },
            text = { Text("It will be removed from LocalPhoto360 on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repo.delete(photo!!.id)
                        confirmDelete = false
                        onDeleted()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            },
        )
    }
}

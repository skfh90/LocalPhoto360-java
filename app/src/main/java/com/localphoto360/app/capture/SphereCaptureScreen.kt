package com.localphoto360.app.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.localphoto360.app.photoRepository
import com.localphoto360.app.ui.theme.Gold
import com.localphoto360.app.ui.theme.Night
import com.localphoto360.app.util.OrientationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SphereCaptureScreen(
    onBack: () -> Unit,
    onFinished: (String) -> Unit,
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
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    val session = remember { PhotosphereSession() }
    val tracker = remember { OrientationTracker(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var hfov by remember { mutableFloatStateOf(65f) }
    var yawDeg by remember { mutableFloatStateOf(0f) }
    var pitchDeg by remember { mutableFloatStateOf(0f) }
    var capturing by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var stitching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var capturedTick by remember { mutableStateOf(0) }
    val matrix = remember { FloatArray(9) }
    val scope = rememberCoroutineScope()

    DisposableEffect(tracker) {
        tracker.start()
        onDispose { tracker.stop() }
    }

    LaunchedEffect(tracker) {
        while (isActive) {
            tracker.copyRotationMatrix(matrix)
            yawDeg = PhotosphereSession.yawDegFromMatrix(matrix)
            pitchDeg = PhotosphereSession.pitchDegFromMatrix(matrix)
            delay(33)
        }
    }

    val yawRad = Math.toRadians(yawDeg.toDouble()).toFloat()
    val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
    val target = session.nearestOpen(yawRad, pitchRad)
    val aligned = target != null && session.isAligned(target, yawRad, pitchRad)

    LaunchedEffect(aligned, capturing, stitching, capturedTick, cameraReady) {
        val ready = target ?: return@LaunchedEffect
        if (!cameraReady || !aligned || capturing || stitching) return@LaunchedEffect
        delay(400)
        if (!ready.captured && imageCapture.camera != null) {
                capturing = true
                runCatching {
                    val frame = imageCapture.awaitBitmap(context)
                    val snap = FloatArray(9)
                    tracker.copyRotationMatrix(snap)
                    withContext(Dispatchers.Default) {
                        session.capture(ready, frame, snap, hfov)
                    }
                }.onFailure { error = it.message ?: "Could not capture this view." }
                capturing = false
                if (ready.captured) capturedTick++
            }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        if (!granted) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Camera permission is needed to capture a 360 photo.", color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            }
            return
        }

        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            imageCapture = imageCapture,
            onReady = { cameraReady = it },
            onHorizontalFov = { hfov = it },
        )

        CaptureHud(
            yawDeg = yawDeg,
            pitchDeg = pitchDeg,
            target = target,
            session = session,
            aligned = aligned,
            capturing = capturing,
        )

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
            Column(Modifier.weight(1f)) {
                Text("360 capture", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${session.capturedCount} of ${session.totalCount} views  ·  ${(session.composer.coverageRatio() * 100).toInt()}% coverage",
                    color = Color(0xCCFFFFFF),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                progress = { session.capturedCount / session.totalCount.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                color = Gold,
                trackColor = Color(0x55FFFFFF),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    !cameraReady -> "Starting camera…"
                    stitching -> "Stitching your photosphere…"
                    capturing -> "Hold still…"
                    aligned -> "Locked on target"
                    target != null -> guidanceText(session, yawDeg, pitchDeg, target)
                    else -> "All views captured. Stitch when you are ready."
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onBack, enabled = !stitching) { Text("Cancel") }
                Button(
                    enabled = session.capturedCount >= 4 && !stitching,
                    onClick = {
                        stitching = true
                        scope.launch {
                            runCatching {
                                val bitmap = withContext(Dispatchers.Default) { session.composer.toBitmap() }
                                val photo = withContext(Dispatchers.IO) {
                                    context.photoRepository.saveBitmap(
                                        bitmap = bitmap,
                                        displayName = "Photosphere",
                                        photosphere = true,
                                    )
                                }
                                photo.id
                            }.onSuccess(onFinished)
                                .onFailure {
                                    error = it.message ?: "Could not stitch the 360 photo."
                                    stitching = false
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Night),
                ) {
                    if (stitching) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Night, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(if (session.capturedCount >= session.totalCount) "Stitch 360" else "Stitch early")
                    }
                }
            }
        }
    }
}

private fun guidanceText(
    session: PhotosphereSession,
    yawDeg: Float,
    pitchDeg: Float,
    target: CaptureTarget,
): String {
    val yawOff = session.headingOffsetDeg(yawDeg, target.yawDeg)
    val pitchOff = session.pitchOffsetDeg(pitchDeg, target.pitchDeg)
    val turn = when {
        yawOff > 12f -> "Turn right"
        yawOff < -12f -> "Turn left"
        else -> "Hold heading"
    }
    val tilt = when {
        pitchOff > 10f -> "tilt up"
        pitchOff < -10f -> "tilt down"
        else -> "keep level"
    }
    return "$turn and $tilt to the next glowing target."
}

@Composable
private fun CaptureHud(
    yawDeg: Float,
    pitchDeg: Float,
    target: CaptureTarget?,
    session: PhotosphereSession,
    aligned: Boolean,
    capturing: Boolean,
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseAmt by pulse.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAmt",
    )
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawCircle(Color.White.copy(alpha = 0.85f), radius = 22.dp.toPx(), center = Offset(cx, cy), style = Stroke(3.dp.toPx()))
        drawLine(Gold, Offset(cx - 36.dp.toPx(), cy), Offset(cx - 18.dp.toPx(), cy), 3.dp.toPx(), StrokeCap.Round)
        drawLine(Gold, Offset(cx + 18.dp.toPx(), cy), Offset(cx + 36.dp.toPx(), cy), 3.dp.toPx(), StrokeCap.Round)
        drawLine(Gold, Offset(cx, cy - 36.dp.toPx()), Offset(cx, cy - 18.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
        drawLine(Gold, Offset(cx, cy + 18.dp.toPx()), Offset(cx, cy + 36.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)

        if (target != null) {
            val yawOff = session.headingOffsetDeg(yawDeg, target.yawDeg)
            val pitchOff = session.pitchOffsetDeg(pitchDeg, target.pitchDeg)
            val marker = Offset(
                cx + (yawOff / 90f) * size.width * 0.42f,
                cy - (pitchOff / 60f) * size.height * 0.32f,
            )
            val radius = if (aligned) 28.dp.toPx() * pulseAmt else 18.dp.toPx()
            drawCircle(
                color = if (aligned) Gold else Color(0xAAF4C95D),
                radius = radius,
                center = marker,
                style = Stroke(4.dp.toPx()),
            )
            if (aligned || capturing) {
                drawCircle(Gold.copy(alpha = 0.35f), radius = radius * 0.55f, center = marker)
            }
        }

        val stripTop = size.height * 0.18f
        val stripHeight = 18.dp.toPx()
        session.targets.forEach { item ->
            val x = ((item.yawDeg / 360f) * size.width)
            val y = stripTop + ((40f - item.pitchDeg) / 80f) * 48.dp.toPx()
            drawCircle(
                color = if (item.captured) Gold else Color.White.copy(alpha = 0.35f),
                radius = stripHeight / 3f,
                center = Offset(x, y),
            )
        }
        val compassX = (yawDeg / 360f) * size.width
        drawLine(Color.White, Offset(compassX, stripTop - 8.dp.toPx()), Offset(compassX, stripTop + 56.dp.toPx()), 2.dp.toPx())
    }
}

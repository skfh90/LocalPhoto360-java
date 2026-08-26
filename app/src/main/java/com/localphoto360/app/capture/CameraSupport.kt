package com.localphoto360.app.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.os.SystemClock
import android.util.SizeF
import android.view.ViewGroup
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.atan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

private val cameraBindLock = Any()
private var cameraBindEpoch = 0

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    imageCapture: ImageCapture,
    onReady: (Boolean) -> Unit = {},
    onHorizontalFov: (Float) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner, imageCapture, previewView) {
        var cancelled = false
        var boundEpoch = -1
        var boundPreview: Preview? = null
        val executor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)

        fun releaseIfCurrent(provider: ProcessCameraProvider, preview: Preview?) {
            synchronized(cameraBindLock) {
                if (boundEpoch != cameraBindEpoch) return
                runCatching {
                    if (preview != null) provider.unbind(preview, imageCapture) else provider.unbind(imageCapture)
                }
            }
        }

        future.addListener(
            {
                val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
                val startBind = Runnable {
                    var ready = false
                    var fov = 65f
                    synchronized(cameraBindLock) {
                        if (cancelled) return@Runnable
                        val epoch = ++cameraBindEpoch
                        boundEpoch = epoch
                        provider.unbindAll()
                        val preview = Preview.Builder().build().also { useCase ->
                            useCase.surfaceProvider = previewView.surfaceProvider
                        }
                        boundPreview = preview
                        val selector = when {
                            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                                CameraSelector.DEFAULT_BACK_CAMERA
                            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            else -> return@Runnable
                        }
                        val camera = runCatching {
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                imageCapture,
                            )
                        }.getOrNull()
                        if (camera == null || cancelled || boundEpoch != cameraBindEpoch) {
                            releaseIfCurrent(provider, preview)
                            return@Runnable
                        }
                        previewView.display?.rotation?.let { imageCapture.targetRotation = it }
                        fov = cameraHorizontalFov(camera.cameraInfo) ?: 65f
                        ready = true
                    }
                    if (ready) {
                        onHorizontalFov(fov)
                        onReady(true)
                    } else {
                        onReady(false)
                    }
                }
                if (previewView.isAttachedToWindow) startBind.run() else previewView.post(startBind)
            },
            executor,
        )

        onDispose {
            cancelled = true
            onReady(false)
            val provider = if (future.isDone) runCatching { future.get() }.getOrNull() else null
            if (provider != null) {
                releaseIfCurrent(provider, boundPreview)
            } else {
                future.addListener(
                    {
                        val ready = runCatching { future.get() }.getOrNull() ?: return@addListener
                        releaseIfCurrent(ready, boundPreview)
                    },
                    executor,
                )
            }
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

fun ImageCapture.takeBitmap(
    context: Context,
    onResult: (Result<Bitmap>) -> Unit,
) {
    takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = runCatching { image.toRotatedBitmap() }
                image.close()
                onResult(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                onResult(Result.failure(exception))
            }
        },
    )
}

fun ImageProxy.toRotatedBitmap(): Bitmap {
    val bitmap = toBitmap()
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

fun cameraHorizontalFov(cameraInfo: androidx.camera.core.CameraInfo): Float? {
    return runCatching {
        val info = Camera2CameraInfo.from(cameraInfo)
        val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.firstOrNull() ?: return null
        val sensor: SizeF = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            ?: return null
        Math.toDegrees(2.0 * atan((sensor.width / (2f * focal)).toDouble())).toFloat()
    }.getOrNull()
}

suspend fun ImageCapture.awaitBitmap(context: Context): Bitmap {
    awaitBound()
    return suspendCancellableCoroutine { continuation ->
        takeBitmap(context) { result ->
            if (!continuation.isActive) return@takeBitmap
            result.fold(
                onSuccess = { continuation.resume(it) },
                onFailure = { continuation.resumeWithException(it) },
            )
        }
    }
}

suspend fun ImageCapture.awaitBound(timeoutMs: Long = 4_000) {
    val start = SystemClock.elapsedRealtime()
    while (camera == null) {
        if (SystemClock.elapsedRealtime() - start > timeoutMs) {
            error("Camera is still starting. Wait for the live preview, then try again.")
        }
        delay(50)
    }
}

fun Throwable.captureErrorMessage(fallback: String): String? {
    if (this is CancellationException || cause is CancellationException) return null
    val text = message.orEmpty()
    if (text.contains("coroutine scope left", ignoreCase = true)) return null
    if (text.contains("StandaloneCoroutine was cancelled", ignoreCase = true)) return null
    if (text.contains("not bound to a valid camera", ignoreCase = true)) {
        return "Camera is still starting. Wait for the live preview, then try again."
    }
    return text.ifBlank { fallback }
}

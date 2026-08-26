package com.localphoto360.app.capture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.camera2.CameraCharacteristics;
import android.util.SizeF;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

public final class CameraBinder {
    public interface ReadyCallback {
        void onReady(boolean ready, float horizontalFov);
    }

    public interface BitmapCallback {
        void onResult(Bitmap bitmap, Exception error);
    }

    private static final Object LOCK = new Object();
    private static int epoch;

    private CameraBinder() {
    }

    public static int bind(
            Context context,
            LifecycleOwner lifecycleOwner,
            PreviewView previewView,
            ImageCapture imageCapture,
            ReadyCallback callback
    ) {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            ProcessCameraProvider provider;
            try {
                provider = future.get();
            } catch (Exception e) {
                callback.onReady(false, 65f);
                return;
            }
            previewView.post(() -> {
                synchronized (LOCK) {
                    int current = ++epoch;
                    provider.unbindAll();
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());
                    try {
                        CameraSelector selector = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                                ? CameraSelector.DEFAULT_BACK_CAMERA
                                : CameraSelector.DEFAULT_FRONT_CAMERA;
                        Camera camera = provider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                imageCapture
                        );
                        if (previewView.getDisplay() != null) {
                            imageCapture.setTargetRotation(previewView.getDisplay().getRotation());
                        }
                        callback.onReady(true, horizontalFov(camera, 65f));
                    } catch (Exception e) {
                        if (current == epoch) callback.onReady(false, 65f);
                    }
                }
            });
        }, ContextCompat.getMainExecutor(context));
        synchronized (LOCK) {
            return epoch + 1;
        }
    }

    public static void unbind(Context context, int bindEpoch) {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            synchronized (LOCK) {
                if (bindEpoch != epoch) return;
                try {
                    future.get().unbindAll();
                } catch (Exception ignored) {
                }
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public static void takeBitmap(ImageCapture imageCapture, Context context, BitmapCallback callback) {
        if (imageCapture.getCamera() == null) {
            callback.onResult(null, new IllegalStateException(
                    "Camera is still starting. Wait for the live preview, then try again."));
            return;
        }
        imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        try {
                            callback.onResult(rotate(image), null);
                        } catch (Exception e) {
                            callback.onResult(null, e);
                        } finally {
                            image.close();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        callback.onResult(null, exception);
                    }
                }
        );
    }

    private static Bitmap rotate(ImageProxy image) {
        Bitmap bitmap = image.toBitmap();
        int degrees = image.getImageInfo().getRotationDegrees();
        if (degrees == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private static float horizontalFov(Camera camera, float fallback) {
        try {
            Camera2CameraInfo info = Camera2CameraInfo.from(camera.getCameraInfo());
            float[] focals = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            SizeF sensor = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            if (focals == null || focals.length == 0 || sensor == null) return fallback;
            return (float) Math.toDegrees(2.0 * Math.atan(sensor.getWidth() / (2f * focals[0])));
        } catch (Exception e) {
            return fallback;
        }
    }
}

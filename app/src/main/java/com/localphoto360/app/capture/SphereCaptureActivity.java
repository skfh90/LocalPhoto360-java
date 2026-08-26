package com.localphoto360.app.capture;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageCapture;
import androidx.core.content.ContextCompat;

import com.localphoto360.app.LocalPhoto360App;
import com.localphoto360.app.R;
import com.localphoto360.app.databinding.ActivitySphereCaptureBinding;
import com.localphoto360.app.util.EquirectangularMath;
import com.localphoto360.app.util.OrientationTracker;
import com.localphoto360.app.viewer.ViewerActivity;

public class SphereCaptureActivity extends AppCompatActivity {
    private ActivitySphereCaptureBinding binding;
    private final PhotosphereSession session = new PhotosphereSession();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float[] matrix = new float[9];
    private OrientationTracker tracker;
    private ImageCapture imageCapture;
    private int bindEpoch;
    private boolean cameraReady;
    private boolean capturing;
    private boolean stitching;
    private float hfov = 65f;
    private float sensorYaw;
    private float sensorPitch;
    private float panYaw;
    private float panPitch;
    private int autoTargetIndex = -1;
    private final boolean emulator = Emulator.isProbablyEmulator();

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
            });

    private final Runnable autoCaptureRunnable = () -> {
        PhotosphereSession.CaptureTarget target = currentTarget();
        if (emulator || capturing || stitching || !cameraReady || target == null || target.captured) return;
        if (!aligned()) return;
        captureCurrent();
    };

    private final Runnable orientationTick = new Runnable() {
        @Override
        public void run() {
            tracker.copyRotationMatrix(matrix);
            sensorYaw = PhotosphereSession.yawDegFromMatrix(matrix);
            sensorPitch = PhotosphereSession.pitchDegFromMatrix(matrix);
            refreshHud();
            maybeScheduleAutoCapture();
            handler.postDelayed(this, 33);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySphereCaptureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        tracker = new OrientationTracker(this);
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();
        binding.helpText.setVisibility(emulator ? View.VISIBLE : View.GONE);
        binding.backButton.setOnClickListener(v -> finish());
        binding.cancelButton.setOnClickListener(v -> finish());
        binding.leftButton.setOnClickListener(v -> panYaw -= 45f);
        binding.rightButton.setOnClickListener(v -> panYaw += 45f);
        binding.tiltUpButton.setOnClickListener(v -> panPitch = Math.min(80f, panPitch + 20f));
        binding.tiltDownButton.setOnClickListener(v -> panPitch = Math.max(-80f, panPitch - 20f));
        binding.nextTargetButton.setOnClickListener(v -> aimAtNext());
        binding.captureViewButton.setOnClickListener(v -> captureCurrent());
        binding.stitchButton.setOnClickListener(v -> stitch());
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        bindEpoch = CameraBinder.bind(this, this, binding.previewView, imageCapture, (ready, fov) -> {
            cameraReady = ready;
            hfov = fov;
            refreshHud();
        });
    }

    private float yawDeg() {
        return EquirectangularMath.wrapDegrees(sensorYaw + panYaw);
    }

    private float pitchDeg() {
        return EquirectangularMath.clamp(sensorPitch + panPitch, -80f, 80f);
    }

    private PhotosphereSession.CaptureTarget currentTarget() {
        return session.nearestOpen(
                (float) Math.toRadians(yawDeg()),
                (float) Math.toRadians(pitchDeg())
        );
    }

    private boolean aligned() {
        PhotosphereSession.CaptureTarget target = currentTarget();
        return target != null && session.isAligned(
                target,
                (float) Math.toRadians(yawDeg()),
                (float) Math.toRadians(pitchDeg())
        );
    }

    private void aimAtNext() {
        PhotosphereSession.CaptureTarget target = currentTarget();
        if (target == null) return;
        panYaw += session.headingOffsetDeg(yawDeg(), target.yawDeg);
        panPitch += session.pitchOffsetDeg(pitchDeg(), target.pitchDeg);
        refreshHud();
    }

    private void captureCurrent() {
        PhotosphereSession.CaptureTarget target = currentTarget();
        if (!cameraReady || capturing || stitching || target == null || target.captured) return;
        capturing = true;
        refreshHud();
        CameraBinder.takeBitmap(imageCapture, this, (bitmap, error) -> {
            if (error != null || bitmap == null) {
                capturing = false;
                binding.statusText.setText(error != null ? error.getMessage() : getString(R.string.starting_camera));
                refreshHud();
                return;
            }
            LocalPhoto360App.from(this).io().execute(() -> {
                float[] snap = EquirectangularMath.rotationMatrixFromYawPitch(
                        (float) Math.toRadians(yawDeg()),
                        (float) Math.toRadians(pitchDeg())
                );
                session.capture(target, bitmap, snap, hfov);
                runOnUiThread(() -> {
                    capturing = false;
                    refreshHud();
                });
            });
        });
    }

    private void stitch() {
        if (session.capturedCount() < 4 || stitching) return;
        stitching = true;
        refreshHud();
        LocalPhoto360App.from(this).io().execute(() -> {
            try {
                String id = LocalPhoto360App.from(this).photos()
                        .saveBitmap(session.composer.toBitmap(), "Photosphere", true).id;
                runOnUiThread(() -> {
                    startActivity(ViewerActivity.intent(this, id));
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    stitching = false;
                    binding.statusText.setText(e.getMessage());
                    refreshHud();
                });
            }
        });
    }

    private void refreshHud() {
        PhotosphereSession.CaptureTarget target = currentTarget();
        boolean locked = aligned();
        binding.hudView.update(session, target, yawDeg(), pitchDeg(), locked, capturing);
        binding.coverageText.setText(getString(
                R.string.coverage_format,
                session.capturedCount(),
                session.totalCount(),
                (int) (session.composer.coverageRatio() * 100)
        ));
        binding.coverageBar.setProgress((int) (100f * session.capturedCount() / session.totalCount()));
        binding.stitchButton.setEnabled(session.capturedCount() >= 4 && !stitching);
        binding.stitchButton.setText(session.capturedCount() >= session.totalCount()
                ? R.string.stitch_360 : R.string.stitch_early);
        binding.captureViewButton.setEnabled(cameraReady && !capturing && !stitching && target != null);
        if (!cameraReady) {
            binding.statusText.setText(R.string.starting_camera);
        } else if (stitching) {
            binding.statusText.setText(R.string.stitching);
        } else if (capturing) {
            binding.statusText.setText(R.string.hold_still);
        } else if (target == null) {
            binding.statusText.setText(R.string.all_views);
        } else if (locked) {
            binding.statusText.setText(emulator ? R.string.locked_on_target_tap : R.string.locked_on_target);
        } else {
            float yawOff = session.headingOffsetDeg(yawDeg(), target.yawDeg);
            float pitchOff = session.pitchOffsetDeg(pitchDeg(), target.pitchDeg);
            String turn = yawOff > 12f ? "Turn right" : yawOff < -12f ? "Turn left" : "Hold heading";
            String tilt = pitchOff > 10f ? "tilt up" : pitchOff < -10f ? "tilt down" : "keep level";
            binding.statusText.setText(turn + " and " + tilt + " to the next glowing target.");
        }
    }

    private void maybeScheduleAutoCapture() {
        if (emulator) return;
        PhotosphereSession.CaptureTarget target = currentTarget();
        boolean ready = cameraReady
                && !capturing
                && !stitching
                && target != null
                && !target.captured
                && aligned();
        if (!ready) {
            handler.removeCallbacks(autoCaptureRunnable);
            autoTargetIndex = -1;
            return;
        }
        if (autoTargetIndex == target.index) return;
        autoTargetIndex = target.index;
        handler.removeCallbacks(autoCaptureRunnable);
        handler.postDelayed(autoCaptureRunnable, 900);
    }

    @Override
    protected void onResume() {
        super.onResume();
        tracker.start();
        handler.post(orientationTick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(orientationTick);
        handler.removeCallbacks(autoCaptureRunnable);
        autoTargetIndex = -1;
        tracker.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        CameraBinder.unbind(this, bindEpoch);
        super.onDestroy();
    }
}

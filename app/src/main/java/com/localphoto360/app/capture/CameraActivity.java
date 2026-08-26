package com.localphoto360.app.capture;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageCapture;
import androidx.core.content.ContextCompat;

import com.localphoto360.app.LocalPhoto360App;
import com.localphoto360.app.R;
import com.localphoto360.app.databinding.ActivityCameraBinding;
import com.localphoto360.app.viewer.ViewerActivity;

public class CameraActivity extends AppCompatActivity {
    private ActivityCameraBinding binding;
    private ImageCapture imageCapture;
    private int bindEpoch;
    private boolean cameraReady;
    private boolean capturing;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) bindCamera();
                else Toast.makeText(this, R.string.camera_required, Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCameraBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();
        binding.backButton.setOnClickListener(v -> finish());
        binding.sphereButton.setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, SphereCaptureActivity.class));
            finish();
        });
        binding.shutterButton.setOnClickListener(v -> capture());
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            bindCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void bindCamera() {
        binding.statusText.setText(R.string.starting_camera);
        bindEpoch = CameraBinder.bind(
                this,
                this,
                binding.previewView,
                imageCapture,
                (ready, fov) -> {
                    cameraReady = ready;
                    binding.statusText.setText(ready ? "" : getString(R.string.starting_camera));
                }
        );
    }

    private void capture() {
        if (!cameraReady || capturing) return;
        capturing = true;
        binding.captureProgress.setVisibility(View.VISIBLE);
        CameraBinder.takeBitmap(imageCapture, this, (bitmap, error) -> {
            if (error != null || bitmap == null) {
                capturing = false;
                binding.captureProgress.setVisibility(View.GONE);
                binding.statusText.setText(error != null ? error.getMessage() : getString(R.string.starting_camera));
                return;
            }
            LocalPhoto360App.from(this).io().execute(() -> {
                try {
                    String id = LocalPhoto360App.from(this).photos()
                            .saveBitmap(bitmap, "Camera photo", false).id;
                    runOnUiThread(() -> {
                        startActivity(ViewerActivity.intent(this, id));
                        finish();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        capturing = false;
                        binding.captureProgress.setVisibility(View.GONE);
                        binding.statusText.setText(e.getMessage());
                    });
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        CameraBinder.unbind(this, bindEpoch);
        super.onDestroy();
    }
}

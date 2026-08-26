package com.localphoto360.app.viewer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.localphoto360.app.LocalPhoto360App;
import com.localphoto360.app.R;
import com.localphoto360.app.data.SpherePhoto;
import com.localphoto360.app.databinding.ActivityViewerBinding;
import com.localphoto360.app.util.OrientationTracker;

public class ViewerActivity extends AppCompatActivity {
    public static final String EXTRA_PHOTO_ID = "photo_id";

    private ActivityViewerBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float[] matrix = new float[9];
    private OrientationTracker tracker;
    private SpherePhoto photo;
    private boolean gyro;

    public static Intent intent(Context context, String photoId) {
        return new Intent(context, ViewerActivity.class).putExtra(EXTRA_PHOTO_ID, photoId);
    }

    private final Runnable gyroTick = new Runnable() {
        @Override
        public void run() {
            if (!gyro) return;
            tracker.copyRotationMatrix(matrix);
            float yaw = (float) Math.toDegrees(OrientationTracker.yawFromMatrix(matrix));
            float pitch = (float) Math.toDegrees(OrientationTracker.pitchFromMatrix(matrix));
            binding.sphereView.setOrientation(yaw, pitch);
            handler.postDelayed(this, 16);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        tracker = new OrientationTracker(this);
        String id = getIntent().getStringExtra(EXTRA_PHOTO_ID);
        photo = LocalPhoto360App.from(this).photos().get(id);
        if (photo == null) {
            finish();
            return;
        }
        binding.titleText.setText(photo.displayName);
        binding.regularHint.setVisibility(photo.photosphere ? View.GONE : View.VISIBLE);
        binding.deleteButton.setVisibility(photo.sample ? View.GONE : View.VISIBLE);
        binding.backButton.setOnClickListener(v -> finish());
        binding.shareButton.setOnClickListener(v -> share());
        binding.deleteButton.setOnClickListener(v -> confirmDelete());
        binding.gyroButton.setOnClickListener(v -> toggleGyro());
        LocalPhoto360App.from(this).io().execute(() -> {
            try {
                Bitmap bitmap = LocalPhoto360App.from(this).photos().decode(photo, 4096);
                runOnUiThread(() -> {
                    binding.loading.setVisibility(View.GONE);
                    binding.sphereView.setBitmap(bitmap);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.loading.setVisibility(View.GONE);
                    binding.hintText.setText(e.getMessage());
                });
            }
        });
    }

    private void toggleGyro() {
        gyro = !gyro;
        binding.hintText.setText(gyro ? R.string.gyro_on : R.string.gyro_off);
        binding.gyroButton.setColorFilter(gyro ? 0xFFF4C95D : 0xFFFFFFFF);
        handler.removeCallbacks(gyroTick);
        if (gyro) {
            tracker.start();
            handler.post(gyroTick);
        } else {
            tracker.stop();
        }
    }

    private void share() {
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/jpeg");
            send.putExtra(Intent.EXTRA_STREAM, LocalPhoto360App.from(this).photos().shareUri(photo));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.share)));
        } catch (Exception e) {
            binding.hintText.setText(e.getMessage());
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(R.string.delete_body)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    LocalPhoto360App.from(this).photos().delete(photo.id);
                    finish();
                })
                .setNegativeButton(R.string.keep, null)
                .show();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(gyroTick);
        tracker.stop();
        binding.sphereView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.sphereView.onResume();
        if (gyro) {
            tracker.start();
            handler.post(gyroTick);
        }
    }
}

package com.localphoto360.app.gallery;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.localphoto360.app.LocalPhoto360App;
import com.localphoto360.app.R;
import com.localphoto360.app.capture.CameraActivity;
import com.localphoto360.app.data.PhotoRepository;
import com.localphoto360.app.data.SpherePhoto;
import com.localphoto360.app.databinding.ActivityGalleryBinding;
import com.localphoto360.app.viewer.ViewerActivity;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class GalleryActivity extends AppCompatActivity {
    private ActivityGalleryBinding binding;
    private PhotoAdapter adapter;
    private PhotoRepository repo;

    private final ActivityResultLauncher<String> importPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImported);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGalleryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repo = LocalPhoto360App.from(this).photos();
        adapter = new PhotoAdapter(photo ->
                startActivity(ViewerActivity.intent(this, photo.id)));
        binding.photoGrid.setLayoutManager(new GridLayoutManager(this, 2));
        binding.photoGrid.setAdapter(adapter);
        binding.captureFab.setOnClickListener(v ->
                startActivity(new Intent(this, CameraActivity.class)));
        binding.importButton.setOnClickListener(v -> importPicker.launch("image/*"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        LocalPhoto360App.from(this).io().execute(() -> {
            List<SpherePhoto> photos = repo.list();
            runOnUiThread(() -> adapter.submitList(photos));
        });
    }

    private void onImported(Uri uri) {
        if (uri == null) return;
        LocalPhoto360App.from(this).io().execute(() -> {
            try {
                repo.importFrom(uri);
                runOnUiThread(this::refresh);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    static final class PhotoAdapter extends ListAdapter<SpherePhoto, PhotoAdapter.Holder> {
        interface Listener {
            void onClick(SpherePhoto photo);
        }

        private final Listener listener;

        PhotoAdapter(Listener listener) {
            super(new DiffUtil.ItemCallback<SpherePhoto>() {
                @Override
                public boolean areItemsTheSame(@NonNull SpherePhoto a, @NonNull SpherePhoto b) {
                    return Objects.equals(a.id, b.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull SpherePhoto a, @NonNull SpherePhoto b) {
                    return a.createdAt == b.createdAt && Objects.equals(a.displayName, b.displayName);
                }
            });
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(getItem(position), listener);
        }

        static final class Holder extends RecyclerView.ViewHolder {
            private final ImageView thumb;
            private final TextView badge;
            private final TextView title;
            private final TextView subtitle;
            private String boundId;

            Holder(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.thumb);
                badge = itemView.findViewById(R.id.badge);
                title = itemView.findViewById(R.id.title);
                subtitle = itemView.findViewById(R.id.subtitle);
            }

            void bind(SpherePhoto photo, Listener listener) {
                boundId = photo.id;
                title.setText(photo.displayName);
                badge.setText(photo.photosphere ? R.string.badge_360 : R.string.badge_photo);
                if (photo.sample) {
                    subtitle.setText(R.string.sample_subtitle);
                } else {
                    subtitle.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(new Date(photo.createdAt)));
                }
                thumb.setImageBitmap(null);
                itemView.setOnClickListener(v -> listener.onClick(photo));
                PhotoRepository repo = LocalPhoto360App.from(itemView.getContext()).photos();
                LocalPhoto360App.from(itemView.getContext()).io().execute(() -> {
                    try {
                        Bitmap bmp = repo.decode(photo, 640);
                        itemView.post(() -> {
                            if (photo.id.equals(boundId)) thumb.setImageBitmap(bmp);
                        });
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }
}

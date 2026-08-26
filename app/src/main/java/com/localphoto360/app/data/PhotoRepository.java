package com.localphoto360.app.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PhotoRepository {
    public static final String SAMPLE_ID = "sample";
    public static final String SAMPLE_ASSET = "sample_sphere.jpg";

    private final Context context;

    public PhotoRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    private File photosDir() {
        File dir = new File(context.getFilesDir(), "photos");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    public List<SpherePhoto> list() {
        List<SpherePhoto> saved = new ArrayList<>();
        File[] files = photosDir().listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.getName().toLowerCase(Locale.US).endsWith(".jpg")) continue;
                SpherePhoto photo = readPhoto(file);
                if (photo != null) saved.add(photo);
            }
        }
        Collections.sort(saved, (a, b) -> Long.compare(b.createdAt, a.createdAt));
        List<SpherePhoto> all = new ArrayList<>();
        all.add(samplePhoto());
        all.addAll(saved);
        return all;
    }

    public SpherePhoto get(String id) {
        if (SAMPLE_ID.equals(id)) return samplePhoto();
        File file = new File(photosDir(), id + ".jpg");
        if (!file.exists()) return null;
        return readPhoto(file);
    }

    public SpherePhoto saveBitmap(Bitmap bitmap, String displayName, boolean photosphere) throws Exception {
        String id = UUID.randomUUID().toString();
        File file = new File(photosDir(), id + ".jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
        }
        JSONObject meta = new JSONObject()
                .put("id", id)
                .put("displayName", displayName)
                .put("createdAt", System.currentTimeMillis())
                .put("photosphere", photosphere)
                .put("width", bitmap.getWidth())
                .put("height", bitmap.getHeight());
        writeText(new File(photosDir(), id + ".json"), meta.toString());
        return readPhoto(file);
    }

    public SpherePhoto importFrom(Uri uri) throws Exception {
        String id = UUID.randomUUID().toString();
        File file = new File(photosDir(), id + ".jpg");
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(file)) {
            if (input == null) throw new IllegalStateException("Could not open the selected image.");
            copy(input, output);
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        boolean looksLikeSphere = bounds.outWidth > 0
                && bounds.outHeight > 0
                && bounds.outWidth >= bounds.outHeight * 1.6;
        JSONObject meta = new JSONObject()
                .put("id", id)
                .put("displayName", "Imported 360")
                .put("createdAt", System.currentTimeMillis())
                .put("photosphere", looksLikeSphere)
                .put("width", bounds.outWidth)
                .put("height", bounds.outHeight);
        writeText(new File(photosDir(), id + ".json"), meta.toString());
        return readPhoto(file);
    }

    public void delete(String id) {
        if (SAMPLE_ID.equals(id)) return;
        //noinspection ResultOfMethodCallIgnored
        new File(photosDir(), id + ".jpg").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(photosDir(), id + ".json").delete();
    }

    public Uri shareUri(SpherePhoto photo) throws Exception {
        if (photo.sample) {
            File cache = new File(context.getCacheDir(), "shared");
            //noinspection ResultOfMethodCallIgnored
            cache.mkdirs();
            File copy = new File(cache, "sample_sphere.jpg");
            try (InputStream input = context.getAssets().open(SAMPLE_ASSET);
                 FileOutputStream output = new FileOutputStream(copy)) {
                copy(input, output);
            }
            return FileProvider.getUriForFile(context, context.getPackageName() + ".files", copy);
        }
        return FileProvider.getUriForFile(context, context.getPackageName() + ".files", new File(photo.filePath));
    }

    public Bitmap decode(SpherePhoto photo, int maxEdge) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        if (photo.sample) {
            try (InputStream in = context.getAssets().open(SAMPLE_ASSET)) {
                BitmapFactory.decodeStream(in, null, options);
            }
        } else {
            BitmapFactory.decodeFile(photo.filePath, options);
        }
        int sample = 1;
        int longest = Math.max(options.outWidth, options.outHeight);
        while (longest / sample > maxEdge) sample *= 2;
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sample;
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
        decode.inScaled = false;
        Bitmap bitmap;
        if (photo.sample) {
            try (InputStream in = context.getAssets().open(SAMPLE_ASSET)) {
                bitmap = BitmapFactory.decodeStream(in, null, decode);
            }
        } else {
            bitmap = BitmapFactory.decodeFile(photo.filePath, decode);
        }
        if (bitmap == null) throw new IllegalStateException("Could not decode " + photo.displayName);
        return bitmap;
    }

    private SpherePhoto samplePhoto() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream in = context.getAssets().open(SAMPLE_ASSET)) {
            BitmapFactory.decodeStream(in, null, options);
        } catch (Exception ignored) {
        }
        return new SpherePhoto(
                SAMPLE_ID,
                "Sample photosphere",
                0L,
                true,
                true,
                Uri.parse("file:///android_asset/" + SAMPLE_ASSET),
                null,
                options.outWidth,
                options.outHeight
        );
    }

    private SpherePhoto readPhoto(File file) {
        String id = file.getName();
        int dot = id.lastIndexOf('.');
        if (dot > 0) id = id.substring(0, dot);
        File metaFile = new File(photosDir(), id + ".json");
        JSONObject meta = new JSONObject();
        try {
            if (metaFile.exists()) meta = new JSONObject(readText(metaFile));
        } catch (Exception ignored) {
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        return new SpherePhoto(
                meta.optString("id", id),
                meta.optString("displayName", "Photo"),
                meta.optLong("createdAt", file.lastModified()),
                meta.optBoolean("photosphere", true),
                false,
                Uri.fromFile(file),
                file.getAbsolutePath(),
                meta.optInt("width", bounds.outWidth),
                meta.optInt("height", bounds.outHeight)
        );
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
    }

    private static void writeText(File file, String text) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readText(File file) throws Exception {
        byte[] bytes;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            bytes = Files.readAllBytes(file.toPath());
        } else {
            bytes = readAllBytesCompat(file);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] readAllBytesCompat(File file) throws Exception {
        try (InputStream in = new java.io.FileInputStream(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            copy(in, out);
            return out.toByteArray();
        }
    }
}

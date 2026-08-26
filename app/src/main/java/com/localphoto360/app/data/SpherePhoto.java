package com.localphoto360.app.data;

import android.net.Uri;

public class SpherePhoto {
    public final String id;
    public final String displayName;
    public final long createdAt;
    public final boolean photosphere;
    public final boolean sample;
    public final Uri uri;
    public final String filePath;
    public final int width;
    public final int height;

    public SpherePhoto(
            String id,
            String displayName,
            long createdAt,
            boolean photosphere,
            boolean sample,
            Uri uri,
            String filePath,
            int width,
            int height
    ) {
        this.id = id;
        this.displayName = displayName;
        this.createdAt = createdAt;
        this.photosphere = photosphere;
        this.sample = sample;
        this.uri = uri;
        this.filePath = filePath;
        this.width = width;
        this.height = height;
    }
}

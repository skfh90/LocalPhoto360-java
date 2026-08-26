package com.localphoto360.app.capture;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.localphoto360.app.util.EquirectangularMath;

public class EquirectangularComposer {
    public final int width;
    public final int height;
    private final int[] pixels;
    private final byte[] coverage;
    private int frameCount;

    public EquirectangularComposer() {
        this(2048, 1024);
    }

    public EquirectangularComposer(int width, int height) {
        this.width = width;
        this.height = height;
        pixels = new int[width * height];
        coverage = new byte[width * height];
        int fill = Color.rgb(12, 10, 8);
        for (int i = 0; i < pixels.length; i++) pixels[i] = fill;
    }

    public void projectFrame(Bitmap frame, float[] rotationMatrix, float horizontalFovDegrees) {
        Bitmap scaled = scaleForProjection(frame);
        float hfov = (float) Math.toRadians(horizontalFovDegrees);
        float vfov = hfov * scaled.getHeight() / (float) scaled.getWidth();
        float tanH = (float) Math.tan(hfov / 2f);
        float tanV = (float) Math.tan(vfov / 2f);
        float rightX = rotationMatrix[0];
        float upX = rotationMatrix[1];
        float lookX = rotationMatrix[2];
        float rightY = rotationMatrix[3];
        float upY = rotationMatrix[4];
        float lookY = rotationMatrix[5];
        float rightZ = rotationMatrix[6];
        float upZ = rotationMatrix[7];
        float lookZ = rotationMatrix[8];
        for (int y = 0; y < scaled.getHeight(); y++) {
            float ny = 1f - (y / (scaled.getHeight() - 1f)) * 2f;
            for (int x = 0; x < scaled.getWidth(); x++) {
                float nx = (x / (scaled.getWidth() - 1f)) * 2f - 1f;
                float dirX = nx * tanH * rightX + ny * tanV * upX + lookX;
                float dirY = nx * tanH * rightY + ny * tanV * upY + lookY;
                float dirZ = nx * tanH * rightZ + ny * tanV * upZ + lookZ;
                float[] uv = EquirectangularMath.lookToUv(dirX, dirY, dirZ);
                int px = (int) EquirectangularMath.clamp(uv[0] * (width - 1), 0, width - 1);
                int py = (int) EquirectangularMath.clamp(uv[1] * (height - 1), 0, height - 1);
                int index = py * width + px;
                if (coverage[index] == 0) {
                    pixels[index] = scaled.getPixel(x, y);
                    coverage[index] = 1;
                }
            }
        }
        if (scaled != frame) scaled.recycle();
        frameCount++;
        fillSmallHoles();
    }

    public Bitmap toBitmap() {
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    public float coverageRatio() {
        int filled = 0;
        for (byte flag : coverage) if (flag != 0) filled++;
        return filled / (float) coverage.length;
    }

    private void fillSmallHoles() {
        int[] copy = pixels.clone();
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int i = y * width + x;
                if (coverage[i] != 0) continue;
                int count = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                int[] neighbors = {i - 1, i + 1, i - width, i + width};
                for (int n : neighbors) {
                    if (coverage[n] == 0) continue;
                    int c = copy[n];
                    r += Color.red(c);
                    g += Color.green(c);
                    b += Color.blue(c);
                    count++;
                }
                if (count >= 3) {
                    pixels[i] = Color.rgb(r / count, g / count, b / count);
                    coverage[i] = 1;
                }
            }
        }
    }

    private Bitmap scaleForProjection(Bitmap frame) {
        int maxWidth = 480;
        if (frame.getWidth() <= maxWidth) return frame;
        int h = Math.max(1, (int) (frame.getHeight() * (maxWidth / (float) frame.getWidth())));
        return Bitmap.createScaledBitmap(frame, maxWidth, h, true);
    }
}

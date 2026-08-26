package com.localphoto360.app.util;

public final class EquirectangularMath {
    public static final float TAU = (float) (Math.PI * 2.0);

    private EquirectangularMath() {
    }

    public static float[] lookToUv(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-6f) length = 1e-6f;
        float nx = x / length;
        float ny = y / length;
        float nz = z / length;
        float lon = (float) Math.atan2(nx, nz);
        float lat = (float) Math.asin(clamp(ny, -1f, 1f));
        float u = (lon + (float) Math.PI) / TAU;
        float v = ((float) Math.PI / 2f - lat) / (float) Math.PI;
        u = u - (float) Math.floor(u);
        return new float[]{u, clamp(v, 0f, 1f)};
    }

    public static float[] uvToLook(float u, float v) {
        float lon = u * TAU - (float) Math.PI;
        float lat = (float) Math.PI / 2f - v * (float) Math.PI;
        float cosLat = (float) Math.cos(lat);
        return new float[]{
                (float) Math.sin(lon) * cosLat,
                (float) Math.sin(lat),
                (float) Math.cos(lon) * cosLat
        };
    }

    public static float angularDistanceRad(float yawA, float pitchA, float yawB, float pitchB) {
        float[] lookA = yawPitchToLook(yawA, pitchA);
        float[] lookB = yawPitchToLook(yawB, pitchB);
        float dot = clamp(lookA[0] * lookB[0] + lookA[1] * lookB[1] + lookA[2] * lookB[2], -1f, 1f);
        return (float) Math.acos(dot);
    }

    public static float[] yawPitchToLook(float yaw, float pitch) {
        float cosPitch = (float) Math.cos(pitch);
        return new float[]{
                (float) Math.sin(yaw) * cosPitch,
                (float) Math.sin(pitch),
                (float) Math.cos(yaw) * cosPitch
        };
    }

    public static float wrapDegrees(float value) {
        float deg = value % 360f;
        if (deg < 0f) deg += 360f;
        return deg;
    }

    public static float[] rotationMatrixFromYawPitch(float yawRad, float pitchRad) {
        float[] look = yawPitchToLook(yawRad, pitchRad);
        float lookX = look[0];
        float lookY = look[1];
        float lookZ = look[2];
        float rightX = lookZ;
        float rightY = 0f;
        float rightZ = -lookX;
        float rightLen = (float) Math.sqrt(rightX * rightX + rightZ * rightZ);
        if (rightLen < 1e-6f) rightLen = 1e-6f;
        rightX /= rightLen;
        rightZ /= rightLen;
        float upX = rightY * lookZ - rightZ * lookY;
        float upY = rightZ * lookX - rightX * lookZ;
        float upZ = rightX * lookY - rightY * lookX;
        float upLen = (float) Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        if (upLen < 1e-6f) upLen = 1e-6f;
        return new float[]{
                rightX, upX / upLen, lookX,
                rightY, upY / upLen, lookY,
                rightZ, upZ / upLen, lookZ
        };
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

package com.localphoto360.app.capture;

import android.graphics.Bitmap;

import com.localphoto360.app.util.EquirectangularMath;
import com.localphoto360.app.util.OrientationTracker;

import java.util.ArrayList;
import java.util.List;

public class PhotosphereSession {
    private static final float ALIGN_RAD = (float) Math.toRadians(12.0);

    public static class CaptureTarget {
        public final int index;
        public final float yawDeg;
        public final float pitchDeg;
        public boolean captured;

        CaptureTarget(int index, float yawDeg, float pitchDeg) {
            this.index = index;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
        }
    }

    public final List<CaptureTarget> targets = buildTargets();
    public final EquirectangularComposer composer = new EquirectangularComposer();

    public int capturedCount() {
        int count = 0;
        for (CaptureTarget target : targets) if (target.captured) count++;
        return count;
    }

    public int totalCount() {
        return targets.size();
    }

    public CaptureTarget nearestOpen(float yawRad, float pitchRad) {
        CaptureTarget best = null;
        float bestDist = Float.MAX_VALUE;
        for (CaptureTarget target : targets) {
            if (target.captured) continue;
            float dist = EquirectangularMath.angularDistanceRad(
                    yawRad,
                    pitchRad,
                    (float) Math.toRadians(target.yawDeg),
                    (float) Math.toRadians(target.pitchDeg)
            );
            if (dist < bestDist) {
                bestDist = dist;
                best = target;
            }
        }
        return best;
    }

    public boolean isAligned(CaptureTarget target, float yawRad, float pitchRad) {
        float distance = EquirectangularMath.angularDistanceRad(
                yawRad,
                pitchRad,
                (float) Math.toRadians(target.yawDeg),
                (float) Math.toRadians(target.pitchDeg)
        );
        return distance < ALIGN_RAD;
    }

    public void capture(CaptureTarget target, Bitmap frame, float[] rotationMatrix, float hfovDeg) {
        if (target.captured) return;
        composer.projectFrame(frame, rotationMatrix, hfovDeg);
        target.captured = true;
    }

    public float headingOffsetDeg(float fromYawDeg, float toYawDeg) {
        float delta = toYawDeg - fromYawDeg;
        while (delta > 180f) delta -= 360f;
        while (delta < -180f) delta += 360f;
        return delta;
    }

    public float pitchOffsetDeg(float fromPitchDeg, float toPitchDeg) {
        return toPitchDeg - fromPitchDeg;
    }

    public static List<CaptureTarget> buildTargets() {
        List<CaptureTarget> list = new ArrayList<>();
        int index = 0;
        float[] pitches = {-40f, 0f, 40f};
        for (float pitch : pitches) {
            float step = Math.abs(pitch) > 30f ? 60f : 45f;
            for (float yaw = 0f; yaw < 359.9f; yaw += step) {
                list.add(new CaptureTarget(index++, yaw, pitch));
            }
        }
        return list;
    }

    public static float yawDegFromMatrix(float[] matrix) {
        return EquirectangularMath.wrapDegrees((float) Math.toDegrees(OrientationTracker.yawFromMatrix(matrix)));
    }

    public static float pitchDegFromMatrix(float[] matrix) {
        return (float) Math.toDegrees(OrientationTracker.pitchFromMatrix(matrix));
    }
}

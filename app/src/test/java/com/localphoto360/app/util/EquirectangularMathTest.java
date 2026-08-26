package com.localphoto360.app.util;

import org.junit.Assert;
import org.junit.Test;

public class EquirectangularMathTest {
    @Test
    public void lookNorthMapsToImageCenter() {
        float[] uv = EquirectangularMath.lookToUv(0f, 0f, 1f);
        Assert.assertEquals(0.5f, uv[0], 0.001f);
        Assert.assertEquals(0.5f, uv[1], 0.001f);
    }

    @Test
    public void lookEastMapsToThreeQuarters() {
        float[] uv = EquirectangularMath.lookToUv(1f, 0f, 0f);
        Assert.assertEquals(0.75f, uv[0], 0.001f);
    }

    @Test
    public void zenithAndNadir() {
        float[] zenith = EquirectangularMath.lookToUv(0f, 1f, 0f);
        float[] nadir = EquirectangularMath.lookToUv(0f, -1f, 0f);
        Assert.assertEquals(0.0f, zenith[1], 0.01f);
        Assert.assertEquals(1.0f, nadir[1], 0.01f);
    }

    @Test
    public void uvRoundTrip() {
        float[] look = EquirectangularMath.uvToLook(0.25f, 0.5f);
        float[] uv = EquirectangularMath.lookToUv(look[0], look[1], look[2]);
        Assert.assertEquals(0.25f, uv[0], 0.002f);
        Assert.assertEquals(0.5f, uv[1], 0.002f);
    }

    @Test
    public void oppositeLooksArePiApart() {
        float distance = EquirectangularMath.angularDistanceRad(0f, 0f, (float) Math.PI, 0f);
        Assert.assertTrue(Math.abs(distance - (float) Math.PI) < 0.01f);
    }

    @Test
    public void rotationMatrixMatchesYawPitch() {
        float yaw = (float) Math.toRadians(90.0);
        float pitch = (float) Math.toRadians(20.0);
        float[] matrix = EquirectangularMath.rotationMatrixFromYawPitch(yaw, pitch);
        Assert.assertEquals(yaw, Math.atan2(matrix[2], matrix[8]), 0.02);
        Assert.assertEquals(pitch, Math.asin(EquirectangularMath.clamp(matrix[5], -1f, 1f)), 0.02);
    }
}

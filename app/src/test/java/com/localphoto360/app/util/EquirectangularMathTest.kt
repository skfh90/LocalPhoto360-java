package com.localphoto360.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EquirectangularMathTest {

    @Test
    fun lookNorthMapsToImageCenter() {
        val (u, v) = EquirectangularMath.lookToUv(0f, 0f, 1f)
        assertEquals(0.5f, u, 0.001f)
        assertEquals(0.5f, v, 0.001f)
    }

    @Test
    fun lookEastMapsToThreeQuarters() {
        val (u, _) = EquirectangularMath.lookToUv(1f, 0f, 0f)
        assertEquals(0.75f, u, 0.001f)
    }

    @Test
    fun zenithAndNadir() {
        val zenith = EquirectangularMath.lookToUv(0f, 1f, 0f)
        val nadir = EquirectangularMath.lookToUv(0f, -1f, 0f)
        assertEquals(0.0f, zenith.second, 0.01f)
        assertEquals(1.0f, nadir.second, 0.01f)
    }

    @Test
    fun uvRoundTrip() {
        val look = EquirectangularMath.uvToLook(0.25f, 0.5f)
        val (u, v) = EquirectangularMath.lookToUv(look[0], look[1], look[2])
        assertEquals(0.25f, u, 0.002f)
        assertEquals(0.5f, v, 0.002f)
    }

    @Test
    fun oppositeLooksArePiApart() {
        val distance = EquirectangularMath.angularDistanceRad(0f, 0f, Math.PI.toFloat(), 0f)
        assertTrue(abs(distance - Math.PI.toFloat()) < 0.01f)
    }
}

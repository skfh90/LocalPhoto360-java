package com.localphoto360.app.util

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object EquirectangularMath {
    const val TAU = (PI * 2).toFloat()

    fun lookToUv(x: Float, y: Float, z: Float): Pair<Float, Float> {
        val length = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
        val nx = x / length
        val ny = y / length
        val nz = z / length
        val lon = atan2(nx, nz)
        val lat = asin(ny.coerceIn(-1f, 1f))
        val u = (lon + PI.toFloat()) / TAU
        val v = (PI.toFloat() / 2f - lat) / PI.toFloat()
        return u.mod(1f) to v.coerceIn(0f, 1f)
    }

    fun uvToLook(u: Float, v: Float, out: FloatArray = FloatArray(3)): FloatArray {
        val lon = u * TAU - PI.toFloat()
        val lat = PI.toFloat() / 2f - v * PI.toFloat()
        val cosLat = cos(lat)
        out[0] = sin(lon) * cosLat
        out[1] = sin(lat)
        out[2] = cos(lon) * cosLat
        return out
    }

    fun angularDistanceRad(
        yawA: Float,
        pitchA: Float,
        yawB: Float,
        pitchB: Float,
    ): Float {
        val lookA = yawPitchToLook(yawA, pitchA)
        val lookB = yawPitchToLook(yawB, pitchB)
        val dot = (lookA[0] * lookB[0] + lookA[1] * lookB[1] + lookA[2] * lookB[2])
            .coerceIn(-1f, 1f)
        return kotlin.math.acos(dot)
    }

    fun yawPitchToLook(yaw: Float, pitch: Float, out: FloatArray = FloatArray(3)): FloatArray {
        val cosPitch = cos(pitch)
        out[0] = sin(yaw) * cosPitch
        out[1] = sin(pitch)
        out[2] = cos(yaw) * cosPitch
        return out
    }

    fun wrapDegrees(value: Float): Float {
        var deg = value % 360f
        if (deg < 0f) deg += 360f
        return deg
    }

    fun rotationMatrixFromYawPitch(
        yawRad: Float,
        pitchRad: Float,
        out: FloatArray = FloatArray(9),
    ): FloatArray {
        val look = yawPitchToLook(yawRad, pitchRad)
        val lookX = look[0]
        val lookY = look[1]
        val lookZ = look[2]
        var rightX = lookZ
        var rightY = 0f
        var rightZ = -lookX
        val rightLen = sqrt(rightX * rightX + rightZ * rightZ).coerceAtLeast(1e-6f)
        rightX /= rightLen
        rightZ /= rightLen
        val upX = rightY * lookZ - rightZ * lookY
        val upY = rightZ * lookX - rightX * lookZ
        val upZ = rightX * lookY - rightY * lookX
        val upLen = sqrt(upX * upX + upY * upY + upZ * upZ).coerceAtLeast(1e-6f)
        out[0] = rightX
        out[1] = upX / upLen
        out[2] = lookX
        out[3] = rightY
        out[4] = upY / upLen
        out[5] = lookY
        out[6] = rightZ
        out[7] = upZ / upLen
        out[8] = lookZ
        return out
    }
}

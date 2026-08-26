package com.localphoto360.app.capture

import android.graphics.Bitmap
import com.localphoto360.app.util.EquirectangularMath
import com.localphoto360.app.util.OrientationTracker
import kotlin.math.abs

data class CaptureTarget(
    val index: Int,
    val yawDeg: Float,
    val pitchDeg: Float,
    var captured: Boolean = false,
)

class PhotosphereSession {
    val targets: List<CaptureTarget> = buildTargets()
    val composer = EquirectangularComposer()

    val capturedCount: Int get() = targets.count { it.captured }
    val totalCount: Int get() = targets.size

    fun nearestOpen(yawRad: Float, pitchRad: Float): CaptureTarget? {
        return targets
            .filter { !it.captured }
            .minByOrNull {
                EquirectangularMath.angularDistanceRad(
                    yawRad,
                    pitchRad,
                    Math.toRadians(it.yawDeg.toDouble()).toFloat(),
                    Math.toRadians(it.pitchDeg.toDouble()).toFloat(),
                )
            }
    }

    fun isAligned(target: CaptureTarget, yawRad: Float, pitchRad: Float): Boolean {
        val distance = EquirectangularMath.angularDistanceRad(
            yawRad,
            pitchRad,
            Math.toRadians(target.yawDeg.toDouble()).toFloat(),
            Math.toRadians(target.pitchDeg.toDouble()).toFloat(),
        )
        return distance < ALIGN_RAD
    }

    fun capture(target: CaptureTarget, frame: Bitmap, rotationMatrix: FloatArray, hfovDeg: Float) {
        if (target.captured) return
        composer.projectFrame(frame, rotationMatrix, hfovDeg)
        target.captured = true
    }

    fun headingOffsetDeg(fromYawDeg: Float, toYawDeg: Float): Float {
        var delta = toYawDeg - fromYawDeg
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return delta
    }

    fun pitchOffsetDeg(fromPitchDeg: Float, toPitchDeg: Float): Float = toPitchDeg - fromPitchDeg

    companion object {
        private val ALIGN_RAD = Math.toRadians(12.0).toFloat()

        fun buildTargets(): List<CaptureTarget> {
            val list = mutableListOf<CaptureTarget>()
            var index = 0
            val pitches = listOf(-40f, 0f, 40f)
            for (pitch in pitches) {
                val step = if (abs(pitch) > 30f) 60f else 45f
                var yaw = 0f
                while (yaw < 360f - 0.1f) {
                    list += CaptureTarget(index++, yaw, pitch)
                    yaw += step
                }
            }
            return list
        }

        fun yawDegFromMatrix(matrix: FloatArray): Float {
            return EquirectangularMath.wrapDegrees(
                Math.toDegrees(OrientationTracker.yawFromMatrix(matrix).toDouble()).toFloat(),
            )
        }

        fun pitchDegFromMatrix(matrix: FloatArray): Float {
            return Math.toDegrees(OrientationTracker.pitchFromMatrix(matrix).toDouble()).toFloat()
        }
    }
}

package com.localphoto360.app.capture

import android.graphics.Bitmap
import android.graphics.Color
import com.localphoto360.app.util.EquirectangularMath
import kotlin.math.tan

class EquirectangularComposer(
    val width: Int = 2048,
    val height: Int = 1024,
) {
    private val pixels = IntArray(width * height) { Color.rgb(12, 10, 8) }
    private val coverage = ByteArray(width * height)
    var frameCount: Int = 0
        private set

    fun projectFrame(
        frame: Bitmap,
        rotationMatrix: FloatArray,
        horizontalFovDegrees: Float,
    ) {
        val scaled = scaleForProjection(frame)
        val hfov = Math.toRadians(horizontalFovDegrees.toDouble()).toFloat()
        val vfov = hfov * scaled.height / scaled.width.toFloat()
        val tanH = tan(hfov / 2f)
        val tanV = tan(vfov / 2f)
        val rightX = rotationMatrix[0]
        val upX = rotationMatrix[1]
        val lookX = rotationMatrix[2]
        val rightY = rotationMatrix[3]
        val upY = rotationMatrix[4]
        val lookY = rotationMatrix[5]
        val rightZ = rotationMatrix[6]
        val upZ = rotationMatrix[7]
        val lookZ = rotationMatrix[8]

        val stepX = 1
        val stepY = 1
        for (y in 0 until scaled.height step stepY) {
            val ny = 1f - (y / (scaled.height - 1f)) * 2f
            for (x in 0 until scaled.width step stepX) {
                val nx = (x / (scaled.width - 1f)) * 2f - 1f
                val dirX = nx * tanH * rightX + ny * tanV * upX + lookX
                val dirY = nx * tanH * rightY + ny * tanV * upY + lookY
                val dirZ = nx * tanH * rightZ + ny * tanV * upZ + lookZ
                val (u, v) = EquirectangularMath.lookToUv(dirX, dirY, dirZ)
                val px = (u * (width - 1)).toInt().coerceIn(0, width - 1)
                val py = (v * (height - 1)).toInt().coerceIn(0, height - 1)
                val index = py * width + px
                if (coverage[index] == 0.toByte()) {
                    pixels[index] = scaled.getPixel(x, y)
                    coverage[index] = 1
                }
            }
        }
        if (scaled !== frame) scaled.recycle()
        frameCount++
        fillSmallHoles()
    }

    fun toBitmap(): Bitmap {
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun coverageRatio(): Float {
        var filled = 0
        for (flag in coverage) if (flag != 0.toByte()) filled++
        return filled / coverage.size.toFloat()
    }

    private fun fillSmallHoles() {
        val copy = pixels.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                if (coverage[i] != 0.toByte()) continue
                var count = 0
                var r = 0
                var g = 0
                var b = 0
                val neighbors = intArrayOf(i - 1, i + 1, i - width, i + width)
                for (n in neighbors) {
                    if (coverage[n] == 0.toByte()) continue
                    val c = copy[n]
                    r += Color.red(c)
                    g += Color.green(c)
                    b += Color.blue(c)
                    count++
                }
                if (count >= 3) {
                    pixels[i] = Color.rgb(r / count, g / count, b / count)
                    coverage[i] = 1
                }
            }
        }
    }

    private fun scaleForProjection(frame: Bitmap): Bitmap {
        val maxWidth = 480
        if (frame.width <= maxWidth) return frame
        val height = (frame.height * (maxWidth / frame.width.toFloat())).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(frame, maxWidth, height, true)
    }
}

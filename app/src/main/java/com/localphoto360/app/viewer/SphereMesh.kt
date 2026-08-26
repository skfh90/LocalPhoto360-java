package com.localphoto360.app.viewer

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SphereMesh(
    private val latitudes: Int = 64,
    private val longitudes: Int = 128,
) {
    val vertexCount: Int = (latitudes + 1) * (longitudes + 1)
    val indexCount: Int = latitudes * longitudes * 6

    val vertices: FloatBuffer
    val texCoords: FloatBuffer
    val indices: ShortBuffer

    init {
        val pos = FloatArray(vertexCount * 3)
        val uv = FloatArray(vertexCount * 2)
        var i = 0
        for (lat in 0..latitudes) {
            val theta = lat / latitudes.toFloat() * PI.toFloat()
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)
            val v = lat / latitudes.toFloat()
            for (lon in 0..longitudes) {
                val phi = lon / longitudes.toFloat() * (PI.toFloat() * 2f)
                val u = lon / longitudes.toFloat()
                // Invert winding by flipping X so we see the inward faces.
                pos[i * 3] = -sinTheta * sin(phi)
                pos[i * 3 + 1] = cosTheta
                pos[i * 3 + 2] = sinTheta * cos(phi)
                uv[i * 2] = u
                uv[i * 2 + 1] = v
                i++
            }
        }
        val idx = ShortArray(indexCount)
        var t = 0
        for (lat in 0 until latitudes) {
            for (lon in 0 until longitudes) {
                val a = (lat * (longitudes + 1) + lon).toShort()
                val b = (a + 1).toShort()
                val c = ((lat + 1) * (longitudes + 1) + lon).toShort()
                val d = (c + 1).toShort()
                idx[t++] = a
                idx[t++] = c
                idx[t++] = b
                idx[t++] = b
                idx[t++] = c
                idx[t++] = d
            }
        }
        vertices = buffer(pos)
        texCoords = buffer(uv)
        indices = shortBuffer(idx)
    }

    fun draw(positionHandle: Int, texHandle: Int) {
        vertices.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(positionHandle)
        texCoords.position(0)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texCoords)
        GLES20.glEnableVertexAttribArray(texHandle)
        indices.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indices)
    }

    private fun buffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    private fun shortBuffer(data: ShortArray): ShortBuffer {
        return ByteBuffer.allocateDirect(data.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(data)
                position(0)
            }
    }
}

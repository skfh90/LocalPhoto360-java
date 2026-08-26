package com.localphoto360.app.viewer;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class SphereMesh {
    private final int indexCount;
    private final FloatBuffer vertices;
    private final FloatBuffer texCoords;
    private final ShortBuffer indices;

    public SphereMesh() {
        this(64, 128);
    }

    public SphereMesh(int latitudes, int longitudes) {
        int vertexCount = (latitudes + 1) * (longitudes + 1);
        indexCount = latitudes * longitudes * 6;
        float[] pos = new float[vertexCount * 3];
        float[] uv = new float[vertexCount * 2];
        int i = 0;
        for (int lat = 0; lat <= latitudes; lat++) {
            float theta = lat / (float) latitudes * (float) Math.PI;
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);
            float v = lat / (float) latitudes;
            for (int lon = 0; lon <= longitudes; lon++) {
                float phi = lon / (float) longitudes * (float) (Math.PI * 2.0);
                float u = lon / (float) longitudes;
                pos[i * 3] = -sinTheta * (float) Math.sin(phi);
                pos[i * 3 + 1] = cosTheta;
                pos[i * 3 + 2] = sinTheta * (float) Math.cos(phi);
                uv[i * 2] = u;
                uv[i * 2 + 1] = v;
                i++;
            }
        }
        short[] idx = new short[indexCount];
        int t = 0;
        for (int lat = 0; lat < latitudes; lat++) {
            for (int lon = 0; lon < longitudes; lon++) {
                short a = (short) (lat * (longitudes + 1) + lon);
                short b = (short) (a + 1);
                short c = (short) ((lat + 1) * (longitudes + 1) + lon);
                short d = (short) (c + 1);
                idx[t++] = a;
                idx[t++] = c;
                idx[t++] = b;
                idx[t++] = b;
                idx[t++] = c;
                idx[t++] = d;
            }
        }
        vertices = buffer(pos);
        texCoords = buffer(uv);
        indices = shortBuffer(idx);
    }

    public void draw(int positionHandle, int texHandle) {
        vertices.position(0);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices);
        GLES20.glEnableVertexAttribArray(positionHandle);
        texCoords.position(0);
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texCoords);
        GLES20.glEnableVertexAttribArray(texHandle);
        indices.position(0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indices);
    }

    private static FloatBuffer buffer(float[] data) {
        FloatBuffer buf = ByteBuffer.allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buf.put(data);
        buf.position(0);
        return buf;
    }

    private static ShortBuffer shortBuffer(short[] data) {
        ShortBuffer buf = ByteBuffer.allocateDirect(data.length * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        buf.put(data);
        buf.position(0);
        return buf;
    }
}

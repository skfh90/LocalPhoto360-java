package com.localphoto360.app.viewer;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class SphereRenderer implements GLSurfaceView.Renderer {
    private static final String VERTEX =
            "uniform mat4 uMVP;"
                    + "attribute vec4 aPosition;"
                    + "attribute vec2 aTexCoord;"
                    + "varying vec2 vTexCoord;"
                    + "void main() {"
                    + "  gl_Position = uMVP * aPosition;"
                    + "  vTexCoord = aTexCoord;"
                    + "}";
    private static final String FRAGMENT =
            "precision mediump float;"
                    + "uniform sampler2D uTexture;"
                    + "varying vec2 vTexCoord;"
                    + "void main() {"
                    + "  gl_FragColor = texture2D(uTexture, vTexCoord);"
                    + "}";

    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] mvp = new float[16];
    private final SphereMesh mesh = new SphereMesh();
    volatile float yawDeg;
    volatile float pitchDeg;
    volatile float fieldOfView = 70f;
    private int program;
    private int mvpHandle;
    private int positionHandle;
    private int texCoordHandle;
    private int textureHandle;
    private int textureId;
    private Bitmap pendingBitmap;
    private boolean hasTexture;
    private float aspect = 1f;

    public void setBitmap(Bitmap bitmap) {
        pendingBitmap = bitmap;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.05f, 0.04f, 0.03f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        program = compile(VERTEX, FRAGMENT);
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP");
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord");
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture");
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        textureId = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        aspect = width / Math.max(0.01f, (float) height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        uploadIfNeeded();
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (!hasTexture) return;
        Matrix.perspectiveM(projection, 0, fieldOfView, aspect, 0.1f, 10f);
        float yaw = (float) Math.toRadians(yawDeg);
        float pitch = (float) Math.toRadians(pitchDeg);
        float cosPitch = (float) Math.cos(pitch);
        float lookX = (float) Math.sin(yaw) * cosPitch;
        float lookY = (float) Math.sin(pitch);
        float lookZ = (float) Math.cos(yaw) * cosPitch;
        Matrix.setLookAtM(view, 0, 0f, 0f, 0f, lookX, lookY, lookZ, 0f, 1f, 0f);
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0);
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(textureHandle, 0);
        mesh.draw(positionHandle, texCoordHandle);
    }

    private void uploadIfNeeded() {
        Bitmap bitmap = pendingBitmap;
        if (bitmap == null) return;
        pendingBitmap = null;
        int[] maxSize = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxSize, 0);
        Bitmap ready = bitmap;
        if (bitmap.getWidth() > maxSize[0] || bitmap.getHeight() > maxSize[0]) {
            float scale = maxSize[0] / (float) Math.max(bitmap.getWidth(), bitmap.getHeight());
            ready = Bitmap.createScaledBitmap(
                    bitmap,
                    Math.max(1, (int) (bitmap.getWidth() * scale)),
                    Math.max(1, (int) (bitmap.getHeight() * scale)),
                    true
            );
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, ready, 0);
        hasTexture = true;
        if (ready != bitmap) ready.recycle();
    }

    private int compile(String vertex, String fragment) {
        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, shader(GLES20.GL_VERTEX_SHADER, vertex));
        GLES20.glAttachShader(prog, shader(GLES20.GL_FRAGMENT_SHADER, fragment));
        GLES20.glLinkProgram(prog);
        return prog;
    }

    private int shader(int type, String source) {
        int id = GLES20.glCreateShader(type);
        GLES20.glShaderSource(id, source);
        GLES20.glCompileShader(id);
        return id;
    }
}

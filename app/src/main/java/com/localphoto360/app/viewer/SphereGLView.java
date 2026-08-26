package com.localphoto360.app.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.localphoto360.app.util.EquirectangularMath;

public class SphereGLView extends GLSurfaceView {
    private final SphereRenderer renderer = new SphereRenderer();
    private final ScaleGestureDetector scaleDetector;
    private float lastX;
    private float lastY;
    private boolean dragging;

    public SphereGLView(Context context) {
        this(context, null);
    }

    public SphereGLView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setPreserveEGLContextOnPause(true);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    public void setBitmap(Bitmap bitmap) {
        renderer.setBitmap(bitmap);
    }

    public void setOrientation(float yawDeg, float pitchDeg) {
        renderer.yawDeg = yawDeg;
        renderer.pitchDeg = EquirectangularMath.clamp(pitchDeg, -89f, 89f);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (scaleDetector.isInProgress()) {
            dragging = false;
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                dragging = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && event.getPointerCount() == 1) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    lastX = event.getX();
                    lastY = event.getY();
                    renderer.yawDeg += dx * 0.12f;
                    renderer.pitchDeg = EquirectangularMath.clamp(renderer.pitchDeg + dy * 0.12f, -89f, 89f);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                break;
            default:
                break;
        }
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float next = renderer.fieldOfView / detector.getScaleFactor();
            renderer.fieldOfView = EquirectangularMath.clamp(next, 30f, 100f);
            return Math.abs(detector.getScaleFactor() - 1f) > 0.001f;
        }
    }
}

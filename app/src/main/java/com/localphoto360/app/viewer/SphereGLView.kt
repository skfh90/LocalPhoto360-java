package com.localphoto360.app.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.abs

class SphereGLView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    private val renderer = SphereRenderer()
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    fun setBitmap(bitmap: Bitmap) {
        renderer.setBitmap(bitmap)
    }

    fun setOrientation(yawDeg: Float, pitchDeg: Float) {
        renderer.yawDeg = yawDeg
        renderer.pitchDeg = pitchDeg.coerceIn(-89f, 89f)
    }

    fun yaw(): Float = renderer.yawDeg
    fun pitch(): Float = renderer.pitchDeg
    fun addYaw(delta: Float) {
        renderer.yawDeg += delta
    }
    fun addPitch(delta: Float) {
        renderer.pitchDeg = (renderer.pitchDeg + delta).coerceIn(-89f, 89f)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) {
            dragging = false
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> if (dragging && event.pointerCount == 1) {
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y
                renderer.yawDeg += dx * 0.12f
                renderer.pitchDeg = (renderer.pitchDeg + dy * 0.12f).coerceIn(-89f, 89f)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val next = renderer.fieldOfView / detector.scaleFactor
            renderer.fieldOfView = next.coerceIn(30f, 100f)
            return abs(detector.scaleFactor - 1f) > 0.001f
        }
    }
}

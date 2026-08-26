package com.localphoto360.app.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.asin

class OrientationTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationVector = FloatArray(5)
    private val rotationMatrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    @Volatile var yawRad: Float = 0f
        private set
    @Volatile var pitchRad: Float = 0f
        private set
    @Volatile var rollRad: Float = 0f
        private set
    @Volatile var available: Boolean = rotationSensor != null
        private set

    private val matrixLock = Any()
    private val latestMatrix = FloatArray(9) { if (it % 4 == 0) 1f else 0f }

    fun copyRotationMatrix(out: FloatArray) {
        synchronized(matrixLock) {
            System.arraycopy(latestMatrix, 0, out, 0, 9)
        }
    }

    fun start() {
        val sensor = rotationSensor ?: return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val count = event.values.size.coerceAtMost(rotationVector.size)
        System.arraycopy(event.values, 0, rotationVector, 0, count)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
        // Treat the back camera as looking out the device's -Z axis.
        SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            remapped,
        )
        SensorManager.getOrientation(remapped, orientation)
        yawRad = orientation[0]
        pitchRad = orientation[1]
        rollRad = orientation[2]
        synchronized(matrixLock) {
            System.arraycopy(remapped, 0, latestMatrix, 0, 9)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun lookYawPitch(): Pair<Float, Float> {
        // Remapped matrix transforms device coords to world. Camera looks along device -Z,
        // which after remap is world +Y? AXIS_X / AXIS_Z remap: new Y is old Z (out of screen).
        // getOrientation already returns azimuth/pitch/roll of the remapped frame.
        return yawRad to pitchRad
    }

    companion object {
        fun yawFromMatrix(matrix: FloatArray): Float {
            // Camera look is +Z of remapped frame (out the back of the phone).
            val lookX = matrix[2]
            val lookZ = matrix[8]
            return atan2(lookX, lookZ)
        }

        fun pitchFromMatrix(matrix: FloatArray): Float {
            val lookY = matrix[5]
            return asin(lookY.coerceIn(-1f, 1f))
        }
    }
}

package com.localphoto360.app.util;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class OrientationTracker implements SensorEventListener {
    private final SensorManager sensorManager;
    private final Sensor rotationSensor;
    private final float[] rotationVector = new float[5];
    private final float[] rotationMatrix = new float[9];
    private final float[] remapped = new float[9];
    private final float[] orientation = new float[3];
    private final float[] latestMatrix = new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1};
    private final Object matrixLock = new Object();

    public OrientationTracker(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor game = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        rotationSensor = game != null ? game : sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void copyRotationMatrix(float[] out) {
        synchronized (matrixLock) {
            System.arraycopy(latestMatrix, 0, out, 0, 9);
        }
    }

    public void start() {
        if (rotationSensor == null) return;
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int count = Math.min(event.values.length, rotationVector.length);
        System.arraycopy(event.values, 0, rotationVector, 0, count);
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector);
        SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remapped
        );
        SensorManager.getOrientation(remapped, orientation);
        synchronized (matrixLock) {
            System.arraycopy(remapped, 0, latestMatrix, 0, 9);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public static float yawFromMatrix(float[] matrix) {
        return (float) Math.atan2(matrix[2], matrix[8]);
    }

    public static float pitchFromMatrix(float[] matrix) {
        return (float) Math.asin(EquirectangularMath.clamp(matrix[5], -1f, 1f));
    }
}

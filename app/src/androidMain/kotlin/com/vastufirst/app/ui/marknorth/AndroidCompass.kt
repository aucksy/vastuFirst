package com.vastufirst.app.ui.marknorth

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The Android half of the compass: sensors in, a [CompassState] out. Everything that could be wrong
 * about the ANSWER lives in the pure Compass.kt next door; this file only reads hardware.
 *
 * ⚠ NO PERMISSION IS REQUESTED, and that is deliberate rather than incidental. Motion sensors need
 * none. True (geographic) north would need the device's location to apply magnetic declination — a
 * runtime permission, a privacy-policy line and a manifest entry — to correct by **under two degrees
 * anywhere in India**, which is far inside the error of a hand holding a phone. So the app uses
 * magnetic north, says so on screen, and asks for nothing. `scripts/check-manifest.sh` keeps that
 * true: the plan reader's INTERNET is still the only permission in the app.
 *
 * The sensor is registered only while the user has the helper open and is torn down the moment they
 * leave it or the screen goes away — a compass left running is a battery drain nobody asked for.
 */
@Composable
fun rememberCompassState(live: Boolean): CompassState {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    // Prefer the fused rotation vector (gyro-stabilised, far steadier in the hand); fall back to the
    // geomagnetic-only one, then to raw accelerometer + magnetometer, which every phone with a
    // compass has. If none of those exist the phone genuinely has no compass and the UI says nothing
    // at all about it rather than offering a button that cannot work.
    val rotationSensor = remember(manager) {
        manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    }
    val accelSensor = remember(manager) { manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val magSensor = remember(manager) { manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    val supported = rotationSensor != null || (accelSensor != null && magSensor != null)

    // Holds ONLY what the sensor produces. `supported` and `live` are derived below rather than
    // stored, so nothing here ever writes state during composition.
    val reading = remember { mutableStateOf(CompassState()) }

    DisposableEffect(manager, live, rotationSensor, accelSensor, magSensor) {
        if (manager == null || !live || !supported) return@DisposableEffect onDispose { }

        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        var gravity: FloatArray? = null
        var geomagnetic: FloatArray? = null
        var smoothed: Float? = null

        fun publish(azimuthRad: Float, accuracy: Int) {
            // values[0] is the angle of the device's +Y axis (its top edge) from magnetic north.
            val raw = Math.toDegrees(azimuthRad.toDouble()).toFloat()
            smoothed = smoothHeading(smoothed, raw)
            reading.value = CompassState(
                headingDeg = smoothed,
                quality = when (accuracy) {
                    SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassQuality.UNRELIABLE
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassQuality.NEEDS_CALIBRATION
                    else -> CompassQuality.GOOD
                },
            )
        }

        var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotation, event.values)
                        SensorManager.getOrientation(rotation, orientation)
                        publish(orientation[0], accuracy)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        gravity = event.values.copyOf()
                        val g = gravity
                        val m = geomagnetic
                        if (g != null && m != null && SensorManager.getRotationMatrix(rotation, null, g, m)) {
                            SensorManager.getOrientation(rotation, orientation)
                            publish(orientation[0], accuracy)
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        geomagnetic = event.values.copyOf()
                        val g = gravity
                        val m = geomagnetic
                        if (g != null && m != null && SensorManager.getRotationMatrix(rotation, null, g, m)) {
                            SensorManager.getOrientation(rotation, orientation)
                            publish(orientation[0], accuracy)
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, newAccuracy: Int) {
                // Only the magnetic chain's accuracy tells us anything about the heading.
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD ||
                    sensor?.type == Sensor.TYPE_ROTATION_VECTOR ||
                    sensor?.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
                ) {
                    accuracy = newAccuracy
                }
            }
        }

        // SENSOR_DELAY_UI: about 60 ms, which is plenty for a heading a person is reading, and a
        // fraction of the battery of SENSOR_DELAY_GAME.
        if (rotationSensor != null) {
            manager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelSensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            magSensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        }
        onDispose { manager.unregisterListener(listener) }
    }

    return CompassState(
        supported = supported,
        live = live,
        // A stale heading from a previous session must never be shown as current, so it is dropped
        // the instant the helper is closed.
        headingDeg = if (live) reading.value.headingDeg else null,
        quality = reading.value.quality,
    )
}

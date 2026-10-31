package com.example.sensorlab

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var valuesText: TextView
    private lateinit var statusText: TextView

    private var lastShakeTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.rgb(20, 25, 35))
        }

        val title = TextView(this).apply {
            text = "Android Sensor Lab"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        valuesText = TextView(this).apply {
            text = "Esperando acelerómetro..."
            textSize = 22f
            setTextColor(Color.CYAN)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Sacude el celular para probar"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        layout.addView(title)
        layout.addView(valuesText)
        layout.addView(statusText)

        setContentView(layout)

        sensorManager = getSystemService(
            Context.SENSOR_SERVICE
        ) as SensorManager
    }

    override fun onResume() {
        super.onResume()

        val accelerometer = sensorManager.getDefaultSensor(
            Sensor.TYPE_ACCELEROMETER
        )

        if (accelerometer != null) {
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
            )
        } else {
            statusText.text = "Acelerómetro no disponible"
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        valuesText.text = """
            X: %.2f
            Y: %.2f
            Z: %.2f
        """.trimIndent().format(x, y, z)

        val magnitude = sqrt(x*x + y*y + z*z)
        val now = System.currentTimeMillis()

        if (magnitude > 18f && now - lastShakeTime > 1000) {
            lastShakeTime = now
            statusText.text = "¡Movimiento detectado!"
            vibratePhone()
        }
    }

    private fun vibratePhone() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val manager = getSystemService(
                Context.VIBRATOR_MANAGER_SERVICE
            ) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(
                Context.VIBRATOR_SERVICE
            ) as Vibrator
        }

        vibrator.vibrate(
            VibrationEffect.createOneShot(
                200,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {}
}

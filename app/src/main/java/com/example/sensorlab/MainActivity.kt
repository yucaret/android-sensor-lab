package com.example.sensorlab

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {
    // Colores de la interfaz V3
    private val backgroundColor = Color.rgb(15, 23, 38)
    private val cardColor = Color.rgb(30, 43, 63)
    private val primaryTextColor = Color.rgb(235, 241, 250)
    private val secondaryTextColor = Color.rgb(165, 183, 205)
    private val accentColor = Color.rgb(100, 225, 235)
    
    private lateinit var sensorManager: SensorManager
    private lateinit var valuesText: TextView
    private lateinit var orientationText: TextView
    private lateinit var anglesText: TextView
    private lateinit var commandText: TextView
    private lateinit var statusText: TextView
    private val gravity = FloatArray(3)
    private var ready = false
    private var lastTimestamp = 0L
    private var lastShake = 0L
    private var shakeUntil = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
            setBackgroundColor(Color.rgb(20, 25, 35))
        }
        fun addLine(size: Float, color: Int): TextView {
            return TextView(this).apply {
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                setPadding(0, 12, 0, 12)
                layout.addView(this)
            }
        }
        addLine(26f, Color.WHITE).text = "Android Sensor Lab V2"
        valuesText = addLine(19f, Color.CYAN)
        orientationText = addLine(20f, Color.WHITE)
        anglesText = addLine(18f, Color.LTGRAY)
        commandText = addLine(23f, Color.CYAN)
        statusText = addLine(15f, Color.WHITE)
        statusText.text = "Inclina o sacude el celular"
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        ready = false
        lastTimestamp = 0L
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            statusText.text = "Acelerómetro no disponible"
        } else {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastTimestamp == 0L) 0f else ((now - lastTimestamp) / 1000f).coerceIn(0f, 0.5f)
        lastTimestamp = now
        if (!ready) {
            gravity[0] = x
            gravity[1] = y
            gravity[2] = z
            ready = true
        } else {
            val alpha = dt / (0.3f + dt)
            gravity[0] += alpha * (x - gravity[0])
            gravity[1] += alpha * (y - gravity[1])
            gravity[2] += alpha * (z - gravity[2])
        }
        val gx = gravity[0]
        val gy = gravity[1]
        val gz = gravity[2]
        val pitch = Math.toDegrees(atan2(-gy.toDouble(), sqrt((gx * gx + gz * gz).toDouble())))
        val roll = Math.toDegrees(atan2(gx.toDouble(), gz.toDouble()))
        val orientation = when {
            abs(gz) >= abs(gx) && abs(gz) >= abs(gy) ->
                if (gz >= 0) "Horizontal: pantalla arriba" else "Horizontal: pantalla abajo"
            abs(gx) > abs(gy) -> "De costado"
            else -> "Vertical"
        }
        // Comandos de demostración. No se transmiten a ningún robot.
        val command = when {
            gz < 5f -> "STOP (fuera de posición)"
            abs(gx) < 3f && abs(gy) < 3f -> "STOP"
            abs(gx) >= abs(gy) -> if (gx > 0f) "DERECHA" else "IZQUIERDA"
            else -> if (gy < 0f) "AVANZAR" else "RETROCEDER"
        }
        valuesText.text = String.format(Locale.US, "X: %.2f   Y: %.2f   Z: %.2f m/s²", x, y, z)
        orientationText.text = orientation
        anglesText.text = String.format(Locale.US, "Inclinación: %.1f°\nRotación lateral: %.1f°", pitch, roll)
        commandText.text = "Comando simulado: $command"

        val dx = x - gx
        val dy = y - gy
        val dz = z - gz
        val movement = sqrt(dx * dx + dy * dy + dz * dz)
        if (movement > 11f && now - lastShake > 1000L) {
            lastShake = now
            shakeUntil = now + 1200L
            statusText.text = "¡Sacudida detectada!"
            vibratePhone()
        } else if (now > shakeUntil) {
            statusText.text = "Inclina o sacude el celular"
        }
    }

    private fun vibratePhone() {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

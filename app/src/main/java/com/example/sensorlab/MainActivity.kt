package com.example.sensorlab

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Button
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.widget.ScrollView
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
    private lateinit var xText: TextView
    private lateinit var yText: TextView
    private lateinit var zText: TextView
    
    private lateinit var orientationText: TextView
    private lateinit var pitchText: TextView
    private lateinit var rollText: TextView
    private lateinit var sensorStatus: TextView
    private lateinit var commandHint: TextView
    private lateinit var commandText: TextView
    private lateinit var statusText: TextView
    private val gravity = FloatArray(3)
    private var ready = false
    private var lastTimestamp = 0L
    private var lastShake = 0L
    private var shakeUntil = 0L

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun label(t: String, size: Float, color: Int): TextView =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(backgroundColor)
        }
        val scroll = ScrollView(this).apply { addView(root) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label("Android Sensor Lab", 18f, primaryTextColor),
            LinearLayout.LayoutParams(0, -2, 1f))
        sensorStatus = label("Iniciando", 12f, Color.GREEN)
        header.addView(sensorStatus)
        root.addView(header)

        fun addCard(card: LinearLayout) {
            root.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(12)
            })
        }
        val accelerometer = createCard()
        accelerometer.addView(label("ACELERÓMETRO · m/s²", 13f, secondaryTextColor))
        val axisRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        fun axis(name: String): TextView {
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            column.addView(label(name, 14f, secondaryTextColor))
            val value = label("0.00", 20f, accentColor)
            column.addView(value)
            axisRow.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
            return value
        }
        xText = axis("X")
        yText = axis("Y")
        zText = axis("Z")
        accelerometer.addView(axisRow)
        addCard(accelerometer)

        val position = createCard()
        position.addView(label("POSICIÓN DEL CELULAR", 13f, secondaryTextColor))
        position.addView(label("▯", 38f, accentColor))
        orientationText = label("Esperando sensor", 17f, primaryTextColor)
        position.addView(orientationText)
        val anglesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        pitchText = label("Inclinación: --", 12f, secondaryTextColor)
        rollText = label("Lateral: --", 12f, secondaryTextColor)
        anglesRow.addView(pitchText, LinearLayout.LayoutParams(0, -2, 1f))
        anglesRow.addView(rollText, LinearLayout.LayoutParams(0, -2, 1f))
        position.addView(anglesRow)
        addCard(position)

        val control = createCard()
        control.addView(label("CONTROL DEL ROBOT · SIMULACIÓN", 13f, secondaryTextColor))
        control.addView(label("↑", 30f, accentColor))
        commandText = label("STOP", 21f, accentColor)
        control.addView(commandText)
        commandHint = label("Esperando inclinación", 12f, secondaryTextColor)
        control.addView(commandHint)
        addCard(control)

        statusText = label("Sacude el celular para probar la vibración", 12f, secondaryTextColor)
        statusText.setPadding(0, dp(18), 0, dp(12))
        root.addView(statusText)
        val cameraButton = Button(this).apply {
            text = "VER CÁMARA"
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(21, 94, 117))
            setOnClickListener { startActivity(Intent(this@MainActivity, CameraActivity::class.java)) }
        }
        root.addView(cameraButton, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(12)
        })
        scroll.setBackgroundColor(backgroundColor)
        window.decorView.setBackgroundColor(backgroundColor)
        window.navigationBarColor = backgroundColor
        window.statusBarColor = backgroundColor
        setContentView(scroll)
    }

    private fun createCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(16), dp(12), dp(16))
        background = GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = dp(14).toFloat()
        }
    }

    override fun onResume() {
        super.onResume()
        ready = false
        lastTimestamp = 0L
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            sensorStatus.text = "Sin sensor"
            statusText.text = "Acelerómetro no disponible"
        } else {
            sensorStatus.text = "Sensores activos"
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
        xText.text = String.format(Locale.US, "%.2f", x)
        yText.text = String.format(Locale.US, "%.2f", y)
        zText.text = String.format(Locale.US, "%.2f", z)
        
        
        orientationText.text = orientation
        pitchText.text = String.format(Locale.US, "Inclinación: %.1f°", pitch)
        rollText.text = String.format(Locale.US, "Lateral: %.1f°", roll)
        commandText.text = command
        commandHint.text = if (command == "STOP") "Esperando inclinación" else "Movimiento simulado"

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

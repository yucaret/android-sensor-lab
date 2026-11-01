package com.example.sensorlab

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class CameraActivity : Activity() {
    private lateinit var cameraManager: CameraManager
    private lateinit var preview: TextureView
    private lateinit var status: TextView
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var front = false
    private var active = false
    private val permissionRequest = 40
    private val bg = Color.rgb(15, 23, 38)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(bg)
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val back = Button(this).apply {
            text = "VOLVER"
            setOnClickListener { finish() }
        }
        val switch = Button(this).apply {
            text = "CAMBIAR CÁMARA"
            setOnClickListener {
                front = !front
                stopCamera()
                if (preview.isAvailable) openCamera()
            }
        }
        top.addView(back, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(switch, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top)
        preview = TextureView(this).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    if (active) openCamera()
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    stopCamera()
                    return true
                }
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TextView(this).apply {
            text = "Cámara trasera"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(status)
        window.decorView.setBackgroundColor(bg)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        active = true
        if (preview.isAvailable) openCamera()
    }

    override fun onPause() {
        active = false
        stopCamera()
        super.onPause()
    }

    private fun stopCamera() {
        session?.close()
        session = null
        camera?.close()
        camera = null
    }

    private fun openCamera() {
        if (!active || camera != null) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), permissionRequest)
            return
        }
        try {
            val facing = if (front) CameraCharacteristics.LENS_FACING_FRONT
                         else CameraCharacteristics.LENS_FACING_BACK
            val id = cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) == facing
            }
            if (id == null) {
                status.text = "Cámara no disponible"
                return
            }
            status.text = if (front) "Cámara frontal" else "Cámara trasera"
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (!active) { device.close(); return }
                    camera = device
                    startPreview(device)
                }
                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    camera = null
                    status.text = "Cámara desconectada"
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    camera = null
                    status.text = "Error de cámara: $error"
                }
            }, null)
        } catch (e: Exception) {
            status.text = "No se pudo abrir la cámara"
        }
    }

    private fun startPreview(device: CameraDevice) {
        try {
            val texture = preview.surfaceTexture ?: return
            val sizes = cameraManager.getCameraCharacteristics(device.id)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)
            val size = sizes?.firstOrNull { it.width == 1280 && it.height == 720 }
                ?: sizes?.firstOrNull() ?: return
            texture.setDefaultBufferSize(size.width, size.height)
            val surface = Surface(texture)
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    if (!active || camera !== device) {
                        configured.close()
                        return
                    }
                    session = configured
                    try { configured.setRepeatingRequest(request.build(), null, null) }
                    catch (e: Exception) { status.text = "Error al iniciar video" }
                }
                override fun onConfigureFailed(configured: CameraCaptureSession) {
                    status.text = "No se pudo configurar la vista previa"
                }
            }, null)
        } catch (e: Exception) {
            status.text = "Error en la vista previa"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
            else status.text = "Permiso de cámara denegado"
        }
    }
}

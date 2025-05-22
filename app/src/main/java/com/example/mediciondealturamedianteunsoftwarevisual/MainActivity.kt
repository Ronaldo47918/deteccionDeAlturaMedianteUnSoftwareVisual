package com.example.mediciondealturamedianteunsoftwarevisual

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlin.math.tan

class MainActivity : ComponentActivity(), SensorEventListener {

    private val CAMERA_PERMISSION_CODE = 1001
    private lateinit var sensorManager: SensorManager
    private lateinit var rotationSensor: Sensor
    private var lastAngle: Float = 0f
    private var angle1: Float? = null // Ángulo hacia el punto más bajo
    private var angle2: Float? = null // Ángulo hacia el punto más alto
    private var distance: Float = 0f // Distancia introducida por el usuario

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            showCameraPreview()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)!!
    }

    private fun showCameraPreview() {
        setContent {
            CameraScreen(
                onCaptureAngle = { angle ->
                    // Si es el primer ángulo, lo guardamos como angle1
                    if (angle1 == null) {
                        angle1 = angle
                        Toast.makeText(this, "Ángulo 1 capturado: $angle°", Toast.LENGTH_SHORT).show()
                    } else {
                        // Si ya tenemos angle1, guardamos el segundo ángulo como angle2
                        angle2 = angle
                        Toast.makeText(this, "Ángulo 2 capturado: $angle°", Toast.LENGTH_SHORT).show()
                    }
                },
                onCalculateHeight = { distance ->
                    if (angle1 != null && angle2 != null) {
                        val height = calculateHeight(angle1!!, angle2!!, distance)
                        Toast.makeText(this, "Altura del objeto: $height metros", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Necesitas capturar ambos ángulos", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            lastAngle = Math.toDegrees(orientation[1].toDouble()).toFloat() // pitch
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Composable
    fun CameraScreen(onCaptureAngle: (Float) -> Unit, onCalculateHeight: (Float) -> Unit) {
        val context = LocalContext.current
        var inputDistance by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp) // Padding general para los márgenes
        ) {
            // Cuadro para ingresar la distancia
            TextField(
                value = inputDistance,
                onValueChange = { inputDistance = it },
                label = { Text("Distancia(m):") },
                modifier = Modifier
                    .fillMaxWidth()
            )

            // Vista de la cámara
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    startCamera(previewView, context)
                    previewView
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(0.2f)) // Ajuste de espacio para alinear los botones hacia abajo

            // Row para los botones: uno al lado del otro
            Row(
                horizontalArrangement = Arrangement.SpaceBetween, // Los botones se distribuyen en los extremos
                modifier = Modifier.fillMaxWidth()
            ) {
                // Botón para capturar el ángulo
                Button(
                    onClick = {
                        onCaptureAngle(lastAngle)
                    },
                    modifier = Modifier
                        .width(150.dp) // Ancho fijo para el botón
                ) {
                    Text("Capturar Ángulo")
                }

                // Botón para calcular la altura
                Button(
                    onClick = {
                        val dist = inputDistance.toFloatOrNull() ?: 0f
                        if (dist > 0) {
                            onCalculateHeight(dist)
                        } else {
                            Toast.makeText(context, "Por favor ingresa una distancia válida", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .width(150.dp) // Ancho fijo para el botón
                ) {
                    Text("Calcular Altura")
                }
            }
        }
    }

    // Función para calcular la altura del objeto
    private fun calculateHeight(angle1: Float, angle2: Float, distance: Float): Float {
        // Para evitar el comportamiento errático, calculamos con los ángulos tal como están (sin revertir negativos)
        // Convertimos a radianes
        val radian1 = Math.toRadians(angle1.toDouble())
        val radian2 = Math.toRadians(angle2.toDouble())

        // Usamos la fórmula trigonométrica para la altura
        val height = distance * (tan(radian2) - tan(radian1))

        return if (height < 0) (-height).toFloat() else height.toFloat() // Nos aseguramos que la altura sea positiva
    }

    private fun startCamera(previewView: PreviewView, context: Context) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    context as LifecycleOwner,
                    cameraSelector,
                    preview
                )
            } catch (e: Exception) {
                Log.e("CameraX", "Error al iniciar la cámara", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}

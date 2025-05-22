package com.example.mediciondealturamedianteunsoftwarevisual

import android.Manifest//importa el permiso para usar los permisos del sistema
import android.content.Context//Accede a recursos y servicios del sistema
import android.content.pm.PackageManager//revisa los permisos de la app y si hay paquetes instalados
//dan acceso a los sensores del sistema, yo use el giroscopio
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

import android.os.Bundle//es usado para pasar datos entre actividades o restaurar el estado de la app
import android.util.Log//muestra mensajes en conosla
import android.widget.Toast//muestra mensajes breves en pantalla, como una mini notificación
//permisos de la camara
import androidx.camera.core.CameraSelector//selecciona la camara
import androidx.camera.core.Preview//vista de la camara
import androidx.camera.lifecycle.ProcessCameraProvider//administra la camara
import androidx.camera.view.PreviewView//mmuestra la imagen de la cámara
//jetpack compose
import androidx.activity.ComponentActivity//Permite estructurar elementos visuales
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*//permite el uso de los siguites import
import androidx.compose.material.Button//permite utilizar botones
import androidx.compose.material.Text//permite poner texto en pantalla
import androidx.compose.material.TextField//permite tener campos en lo cuales se ingresa el texto
import androidx.compose.runtime.*//manejo de interfaz
import androidx.compose.ui.Modifier//ajusta diseños y comportamiento
import androidx.compose.ui.platform.LocalContext//obtiene contexto de Compose
import androidx.compose.ui.unit.dp//medida de tamaños
import androidx.compose.ui.viewinterop.AndroidView//inserta vistas de Android en Compose
//manejan permisos durante la ejecución
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import androidx.lifecycle.LifecycleOwner//vincula componenetes al ciclo de vida
import kotlin.math.tan//función para calcular la tangente

class MainActivity : ComponentActivity(), SensorEventListener {

    private val CAMERA_PERMISSION_CODE = 1001//permiso y acceso de camara
    private lateinit var sensorManager: SensorManager//manejo del sensor
    private lateinit var rotationSensor: Sensor//giroscopio
    private var lastAngle: Float = 0f
    private var angle1: Float? = null //angulo 1
    private var angle2: Float? = null //angulo 2
    private var distance: Float = 0f //distancia a la que se esta del objeto
//se encarga de generar todo al abrir la app
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//revisa que se tenga acceso a la camara
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            showCameraPreview()
        } else {//solicita el acceso a la camara
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
//inicializa el sensor de rotación
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)!!
    }

    private fun showCameraPreview() {
        setContent {
            CameraScreen(
                onCaptureAngle = { angle ->//al guardar un angulo
                    // Si es el primer ángulo, lo guardo como angle1
                    if (angle1 == null) {
                        angle1 = angle
                        Toast.makeText(this, "Ángulo 1 capturado: $angle°", Toast.LENGTH_SHORT).show()
                    } else {
                        //guardo el segundo ángulo como angle2
                        angle2 = angle
                        Toast.makeText(this, "Ángulo 2 capturado: $angle°", Toast.LENGTH_SHORT).show()
                    }
                },
                onCalculateHeight = { distance ->//calcula el alto
                    if (angle1 != null && angle2 != null) {
                        val height = calculateHeight(angle1!!, angle2!!, distance)
                        Toast.makeText(this, "Altura del objeto: $height metros", Toast.LENGTH_SHORT).show()
                    } else {//si no se tienen los 2 angulos muestra el mensaje
                        Toast.makeText(this, "Necesitas capturar ambos ángulos", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
//al estar dentro de la app reactiva el sensor del giroscopio
    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
    }
//al tener la app en segundo plano desactiva el sensor del giroscopio
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
//detecta los cambios en el sensor gel giroscopio
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)//rotación
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)//obtiene la matriz del vecotpr
            val orientation = FloatArray(3)//orientación
            SensorManager.getOrientation(rotationMatrix, orientation)//obtiene la rotación/orintanción del sensor
            lastAngle = Math.toDegrees(orientation[1].toDouble()).toFloat()//transforma el bouble que detecto en un float
        }
    }
//permite recibir información del snesor
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
//arma la interfaz
    @Composable
    fun CameraScreen(onCaptureAngle: (Float) -> Unit, onCalculateHeight: (Float) -> Unit) {
        val context = LocalContext.current
        var inputDistance by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp) // Padding(distancia) general para los márgenes
        ) {
            // Cuadro para ingresar la distancia a la que se esta del objeto
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

            Spacer(modifier = Modifier.weight(0.2f)) // Ajuste de espacio para alinear los botones abajp

            // Row para los botones: esto hace que esten uno al lado del otro
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
                        .width(150.dp) // Ancho del botón
                ) {
                    Text("Capturar angulo")
                }

                // Botón para calcular la altura
                Button(
                    onClick = {
                        val dist = inputDistance.toFloatOrNull() ?: 0f
                        if (dist > 0) {
                            onCalculateHeight(dist)
                        } else {
                            Toast.makeText(context, "distancia no valida, añada metros positivos", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .width(150.dp) // Ancho del boton
                ) {
                    Text("calcular altura")
                }
            }
        }
    }

    // Función para calcular la altura del objeto
    private fun calculateHeight(angle1: Float, angle2: Float, distance: Float): Float {
        // Convertimos a radianes
        val radian1 = Math.toRadians(angle1.toDouble())
        val radian2 = Math.toRadians(angle2.toDouble())

        // Uso la fórmula para la altura alto=distancia*(tan(angulo2)-tan(angulo1))
        val height = distance * (tan(radian2) - tan(radian1))

        return if (height < 0) (-height).toFloat() else height.toFloat() //conviete la altura a positiva
    }
//controla la actividad de la camara
    private fun startCamera(previewView: PreviewView, context: Context) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
//muestra la camara en tiempo real
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA//activa la camara trasera

            try {//esto permite a la camara gestionarse y liberar permisos en caso de que haya fallos
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    context as LifecycleOwner,
                    cameraSelector,
                    preview
                )
            } catch (e: Exception) {//en caso de fallo avisa que no se pudo usar la camara
                Log.e("CameraX", "Error al iniciar la cámara", e)
            }
        }, ContextCompat.getMainExecutor(context))//ejecuta la camara y el codigo cuando ya todo esta listo para uso
    }
}

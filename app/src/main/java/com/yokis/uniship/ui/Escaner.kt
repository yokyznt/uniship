package com.yokis.uniship.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun Escaner(leido: (String) -> Unit, volver: () -> Unit) {
    val ctx = LocalContext.current
    val dueno = LocalLifecycleOwner.current
    var permiso by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permiso = it }
    LaunchedEffect(Unit) { if (!permiso) pedir.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (permiso) {
            val hecho = remember { AtomicBoolean(false) }
            val hilo = remember { Executors.newSingleThreadExecutor() }
            val lector = remember { BarcodeScanning.getClient() }
            DisposableEffect(Unit) { onDispose { hilo.shutdown(); lector.close() } }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    val vista = PreviewView(c)
                    val futuro = ProcessCameraProvider.getInstance(c)
                    futuro.addListener({
                        val proveedor = futuro.get()
                        val vistaPrevia = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
                        val analisis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        analisis.setAnalyzer(hilo) { img ->
                            val media = img.image
                            if (media == null || hecho.get()) { img.close(); return@setAnalyzer }
                            lector.process(InputImage.fromMediaImage(media, img.imageInfo.rotationDegrees))
                                .addOnSuccessListener { codigos ->
                                    val valor = codigos.mapNotNull { it.rawValue?.trim() }.firstOrNull { it.length in 8..40 }
                                    if (valor != null && hecho.compareAndSet(false, true)) ContextCompat.getMainExecutor(c).execute { leido(valor) }
                                }
                                .addOnCompleteListener { img.close() }
                        }
                        proveedor.unbindAll()
                        proveedor.bindToLifecycle(dueno, CameraSelector.DEFAULT_BACK_CAMERA, vistaPrevia, analisis)
                    }, ContextCompat.getMainExecutor(c))
                    vista
                }
            )
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth(0.82f).height(140.dp)
                    .border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
            )
            Text(
                "Apunta al código de barras de la etiqueta",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(32.dp)
            )
        } else {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Para escanear necesito la cámara.", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { pedir.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 16.dp)) { Text("Permitir cámara") }
            }
        }
        IconButton(onClick = volver, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = Color.White)
        }
    }
}

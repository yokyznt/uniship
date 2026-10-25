package com.yokis.uniship.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yokis.uniship.R

private data class Paso(val titulo: String, val texto: String, val boton: String, val saltar: String?)

private val pasos = listOf(
    Paso(
        "Todos tus paquetes en un lugar",
        "UniShip junta tus envíos de Amazon, Mercado Libre, Temu, Shein, AliExpress y TikTok Shop, y los rastrea directo con la paquetería: J&T, iMile, Estafeta, DHL, FedEx, 99minutos, Paquetexpress, Correos de México y Redpack.\n\nCada 2 horas revisa si se movieron y te avisa por dónde van.",
        "Empezar", null
    ),
    Paso(
        "Avisos de movimientos",
        "Te mandamos una notificación cada vez que un paquete cambia de estado o de ciudad, por ejemplo: «Shein · En tránsito · Guadalajara, JAL».",
        "Permitir avisos", "Ahora no"
    ),
    Paso(
        "Detectar guías solas",
        "Si lo permites, UniShip lee las notificaciones de estas apps: Mercado Libre, Amazon, Temu, Shein, AliExpress, TikTok, DHL, FedEx y Gmail.\n\nDe ellas solo toma números de guía y avisos de envío para crear y actualizar tus paquetes. En Gmail solo usa los avisos que mencionan una tienda o paquetería y hablan de un envío.\n\nLas demás notificaciones se ignoran y nunca se guardan ni se envían a ningún lado. Puedes apagarlo cuando quieras en Ajustes.",
        "Permitir acceso", "Prefiero agregar guías a mano"
    ),
    Paso(
        "Revisar en segundo plano",
        "Para que las revisiones cada 2 horas no se pausen, quita la restricción de batería a UniShip. En teléfonos Nothing, Xiaomi o Samsung es casi obligatorio.\n\nEn la lista, busca UniShip y elige «No optimizar» o «Sin restricciones».",
        "Abrir ajustes de batería", "Ahora no"
    )
)

@Composable
fun Onboarding(verPrivacidad: () -> Unit, alTerminar: () -> Unit) {
    val ctx = LocalContext.current
    var i by rememberSaveable { mutableIntStateOf(0) }
    var esperando by remember { mutableStateOf(false) }
    var salio by remember { mutableStateOf(false) }
    val pedirAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { i = 2 }
    fun siguiente() { if (i < pasos.lastIndex) i++ else alTerminar() }
    LifecycleResumeEffect(Unit) {
        if (esperando && salio) {
            esperando = false
            salio = false
            siguiente()
        }
        onPauseOrDispose { if (esperando) salio = true }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(24.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            pasos.indices.forEach { n ->
                Box(
                    Modifier.height(4.dp).width(if (n == i) 28.dp else 12.dp)
                        .background(if (n <= i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                )
            }
        }
        AnimatedContent(targetState = i, modifier = Modifier.weight(1f), label = "paso") { n ->
            val p = pasos[n]
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 40.dp)) {
                if (n == 0) {
                    Image(painterResource(R.drawable.ic_marca), null, modifier = Modifier.size(88.dp))
                    Spacer(Modifier.height(24.dp))
                }
                Text(p.titulo, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(16.dp))
                Text(p.texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (n == 0 || n == 2) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = verPrivacidad, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Leer el aviso de privacidad completo", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        val p = pasos[i]
        Button(
            onClick = {
                when (i) {
                    0 -> siguiente()
                    1 -> if (Build.VERSION.SDK_INT >= 33) pedirAvisos.launch(Manifest.permission.POST_NOTIFICATIONS) else siguiente()
                    2 -> if (Permisos.lectorActivo(ctx)) siguiente() else { esperando = true; Permisos.abrirLector(ctx) }
                    3 -> if (Permisos.bateriaLibre(ctx)) siguiente() else { esperando = true; Permisos.abrirBateria(ctx) }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(p.boton) }
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            if (p.saltar != null) TextButton(onClick = { siguiente() }) { Text(p.saltar, color = MaterialTheme.colorScheme.secondary) }
        }
    }
}

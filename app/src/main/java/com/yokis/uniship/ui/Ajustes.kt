package com.yokis.uniship.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yokis.uniship.BuildConfig
import com.yokis.uniship.MainActivity
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.cuenta.Nube
import com.yokis.uniship.mercadolibre.MercadoLibre
import com.yokis.uniship.paqueterias.Fechas
import com.yokis.uniship.trabajo.Rastreador
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Ajustes(actividad: MainActivity, volver: () -> Unit, verPrivacidad: () -> Unit) {
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var lector by remember { mutableStateOf(false) }
    var bateria by remember { mutableStateOf(false) }
    var avisos by remember { mutableStateOf(false) }
    var usuario by remember { mutableStateOf(Nube.usuario) }
    var ocupado by remember { mutableStateOf(false) }
    var borrarNube by remember { mutableStateOf(false) }
    var borrarTodo by remember { mutableStateOf(false) }
    val ml by UniShipApp.prefs.mlConectado.collectAsState(initial = false)
    val respaldo by UniShipApp.prefs.ultimoRespaldo.collectAsState(initial = 0L)
    LifecycleResumeEffect(Unit) {
        lector = Permisos.lectorActivo(ctx)
        bateria = Permisos.bateriaLibre(ctx)
        avisos = Permisos.notificacionesActivas(ctx)
        onPauseOrDispose { }
    }
    fun aviso(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
    fun tarea(bloque: suspend () -> String) {
        if (ocupado) return
        ocupado = true
        alcance.launch {
            runCatching { bloque() }.onSuccess { aviso(it) }.onFailure { aviso(it.message ?: "Algo falló") }
            ocupado = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = volver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.secondary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Seccion("Permisos") {
                Fila("Lector de notificaciones", if (lector) "Activo" else "Apagado: no se detectan guías solas", if (lector) "Ajustar" else "Activar") { Permisos.abrirLector(ctx) }
                HorizontalDivider()
                Fila("Avisos de UniShip", if (avisos) "Activos" else "Apagados: no te llegarán movimientos", "Ajustar") {
                    ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }
                HorizontalDivider()
                Fila("Revisar en segundo plano", if (bateria) "Sin restricción de batería" else "Android puede pausar las revisiones", "Ajustar") { Permisos.abrirBateria(ctx) }
            }
            Seccion("Rastreo") {
                val guardado by UniShipApp.prefs.celular.collectAsState(initial = "")
                var cel by remember(guardado) { mutableStateOf(guardado) }
                Text("J&T Express pide los últimos 4 dígitos del celular que diste al comprar para mostrar el rastreo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = cel,
                    onValueChange = { v ->
                        val d = v.filter { it.isDigit() }.take(4)
                        cel = d
                        if (d.length == 4 || d.isEmpty()) alcance.launch { UniShipApp.prefs.guardarCelular(d) }
                    },
                    label = { Text("Últimos 4 dígitos de tu celular") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Seccion("Mercado Libre") {
                when {
                    !MercadoLibre.configurado() -> Text("Esta versión todavía no tiene la conexión con Mercado Libre configurada.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ml -> Fila("Cuenta conectada", "Tus compras y envíos se revisan cada 2 horas", "Desconectar") {
                        alcance.launch { MercadoLibre.desconectar(); aviso("Mercado Libre desconectado") }
                    }
                    else -> Fila("Conecta tu cuenta", "Ve tus envíos aunque la app de Mercado Libre no avise", "Conectar") {
                        alcance.launch { abrirWeb(ctx, MercadoLibre.urlAutorizacion()) }
                    }
                }
            }
            Seccion("Respaldo") {
                val u = usuario
                when {
                    !Nube.disponible(ctx) -> Text("El respaldo en la nube no está configurado en esta versión.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    u == null -> Fila("Inicia sesión con Google", "Guarda tus paquetes para recuperarlos en otro teléfono", "Entrar") {
                        tarea {
                            usuario = Nube.iniciar(actividad)
                            val n = Nube.restaurar()
                            if (n > 0) "Recuperé $n paquetes" else "Sesión iniciada"
                        }
                    }
                    else -> {
                        Text(u.email ?: "Cuenta de Google", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (respaldo > 0) "Último respaldo: ${Fechas.corta(respaldo)}" else "Todavía no hay respaldo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { tarea { Nube.subir(); "Respaldo guardado" } }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Text("Respaldar", color = MaterialTheme.colorScheme.secondary) }
                            OutlinedButton(onClick = { tarea { "Recuperé ${Nube.restaurar()} paquetes" } }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Text("Restaurar", color = MaterialTheme.colorScheme.secondary) }
                        }
                        Row {
                            TextButton(onClick = { borrarNube = true }) { Text("Borrar respaldo", color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = { alcance.launch { Nube.cerrar(ctx); usuario = null } }) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.secondary) }
                        }
                    }
                }
            }
            Seccion("Privacidad") {
                Fila("Aviso de privacidad", "Qué lee UniShip, qué guarda y con quién lo comparte", "Leer") { verPrivacidad() }
                HorizontalDivider()
                Fila("Borrar todos mis datos", "Paquetes, sesión de Mercado Libre, celular y respaldo en la nube", "Borrar") { borrarTodo = true }
            }
            Seccion("Acerca de") {
                Fila("Revisar ahora", "Consulta todas las paqueterías en este momento", "Revisar") {
                    Rastreador.ahora(ctx); aviso("Revisando tus paquetes")
                }
                Text("UniShip ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (borrarTodo) AlertDialog(
        onDismissRequest = { borrarTodo = false },
        title = { Text("¿Borrar todos tus datos?") },
        text = { Text("Se eliminan tus paquetes y sus movimientos, la sesión de Mercado Libre y tus 4 dígitos. Si iniciaste sesión, también se borra tu respaldo en la nube y se cierra la sesión. No se puede deshacer.") },
        confirmButton = {
            TextButton(onClick = {
                borrarTodo = false
                tarea {
                    if (Nube.usuario != null) { runCatching { Nube.borrar() }; Nube.cerrar(ctx); usuario = null }
                    UniShipApp.repo.borrarTodo()
                    UniShipApp.prefs.borrarTodo()
                    androidx.core.app.NotificationManagerCompat.from(ctx).cancelAll()
                    "Listo, se borró todo"
                }
            }) { Text("Borrar todo", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { borrarTodo = false }) { Text("Cancelar", color = MaterialTheme.colorScheme.secondary) } }
    )
    if (borrarNube) AlertDialog(
        onDismissRequest = { borrarNube = false },
        title = { Text("¿Borrar el respaldo?") },
        text = { Text("Se elimina la copia en la nube. Los paquetes de este teléfono no se tocan.") },
        confirmButton = { TextButton(onClick = { borrarNube = false; tarea { Nube.borrar(); "Respaldo borrado" } }) { Text("Borrar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { borrarNube = false }) { Text("Cancelar", color = MaterialTheme.colorScheme.secondary) } }
    )
}

fun abrirWeb(ctx: android.content.Context, uri: Uri) {
    val colores = CustomTabColorSchemeParams.Builder().setToolbarColor(Paleta.crema.toArgb()).build()
    runCatching { CustomTabsIntent.Builder().setDefaultColorSchemeParams(colores).build().launchUrl(ctx, uri) }
        .onFailure { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Column {
        Text(titulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { contenido() } }
    }
}

@Composable
private fun Fila(titulo: String, detalle: String, accion: String, alPulsar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = alPulsar) { Text(accion, color = MaterialTheme.colorScheme.secondary) }
    }
}

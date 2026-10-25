package com.yokis.uniship.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yokis.uniship.R
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.data.Estado
import com.yokis.uniship.data.Paquete
import com.yokis.uniship.paqueterias.Registro
import com.yokis.uniship.trabajo.Rastreador
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Lista(abrir: (Long) -> Unit, agregar: () -> Unit, ajustes: () -> Unit, verPrivacidad: () -> Unit) {
    val ctx = LocalContext.current
    val enCamino by UniShipApp.repo.paquetes.enCamino().collectAsState(initial = null)
    val terminados by UniShipApp.repo.paquetes.terminados().collectAsState(initial = emptyList())
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    var lector by remember { mutableStateOf(true) }
    var refrescando by remember { mutableStateOf(false) }
    val vista by UniShipApp.prefs.privacidadVista.collectAsState(initial = AvisoPrivacidad.VERSION)
    val alcance = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        lector = Permisos.lectorActivo(ctx)
        onPauseOrDispose { }
    }
    LaunchedEffect(refrescando) {
        if (refrescando) {
            Rastreador.ahora(ctx)
            delay(2500)
            refrescando = false
        }
    }
    if (vista < AvisoPrivacidad.VERSION) AlertDialog(
        onDismissRequest = {},
        title = { Text("Actualizamos el aviso de privacidad") },
        text = { Text("Cambió lo que UniShip lee o guarda. Revísalo antes de seguir.") },
        confirmButton = { TextButton(onClick = { alcance.launch { UniShipApp.prefs.aceptarPrivacidad() } }) { Text("Entendido") } },
        dismissButton = { TextButton(onClick = verPrivacidad) { Text("Leer") } }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = agregar,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Agregar guía", style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp)
            )
        }
    ) { pad ->
        val lista = if (pestana == 0) enCamino else terminados
        PullToRefreshBox(isRefreshing = refrescando, onRefresh = { refrescando = true }, modifier = Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item { Encabezado(enCamino.orEmpty(), ajustes) }
                item {
                    Pestanas(pestana, enCamino?.size ?: 0, terminados.size) { pestana = it }
                }
                if (pestana == 0) {
                    val caducada = com.yokis.uniship.tiendas.Tienda.visibles.firstOrNull { com.yokis.uniship.tiendas.Tiendas.caducada(it) }
                    val ninguna = com.yokis.uniship.tiendas.Tienda.visibles.none { com.yokis.uniship.tiendas.Tiendas.conectada(it) }
                    if (caducada != null) item {
                        Aviso(Icons.Outlined.ShoppingBag, "Vuelve a entrar a ${caducada.nombre}", "Tu sesión caducó y ya no puedo ver tus pedidos nuevos.", "Entrar") { com.yokis.uniship.tiendas.ConectarTienda.abrir(ctx, caducada) }
                    } else if (ninguna) item {
                        Aviso(Icons.Outlined.ShoppingBag, "Conecta tus tiendas", "Entra una vez a AliExpress o Amazon y tus pedidos aparecen solos, con su guía.", "Conectar") { ajustes() }
                    }
                    if (!lector) item {
                        Aviso(Icons.Outlined.NotificationsActive, "Detecta guías nuevas solas", "Con el lector de notificaciones, cada aviso de envío crea o actualiza tu paquete.", "Activar") { Permisos.abrirLector(ctx) }
                    }
                }
                when {
                    lista == null -> Unit
                    lista.isEmpty() -> item { Vacio(pestana == 0, agregar) }
                    else -> items(lista, key = { it.id }) { Tarjeta(it) { abrir(it.id) } }
                }
            }
        }
    }
}

@Composable
private fun Encabezado(enCamino: List<Paquete>, ajustes: () -> Unit) {
    Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_marca), null, Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text("UniShip", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            IconButton(onClick = ajustes) { Icon(Icons.Outlined.Settings, "Ajustes", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(18.dp))
        Text("Tus paquetes", style = MaterialTheme.typography.headlineLarge)
        val reparto = enCamino.count { it.estado == Estado.EN_REPARTO }
        val resumen = when {
            enCamino.isEmpty() -> "Nada en camino por ahora"
            reparto > 0 -> "${enCamino.size} en camino · $reparto ${if (reparto == 1) "llega hoy" else "llegan hoy"}"
            else -> "${enCamino.size} ${if (enCamino.size == 1) "paquete en camino" else "paquetes en camino"}"
        }
        Text(resumen, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Pestanas(sel: Int, enCamino: Int, entregados: Int, elegir: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Paleta.arena, RoundedCornerShape(16.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf("En camino" to enCamino, "Entregados" to entregados).forEachIndexed { i, (t, n) ->
            val activo = sel == i
            val fondo by animateColorAsState(if (activo) Paleta.blanco else Color.Transparent, label = "pestana")
            Box(
                Modifier.weight(1f).height(42.dp).background(fondo, RoundedCornerShape(12.dp)).clickable { elegir(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (n > 0) "$t  $n" else t,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                    color = if (activo) Paleta.naranjaTexto else Paleta.tintaSuave
                )
            }
        }
    }
}

@Composable
private fun Aviso(icono: ImageVector, titulo: String, texto: String, boton: String, accion: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Paleta.durazno), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(Paleta.naranjaSuave, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icono, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = accion,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(boton, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun Vacio(enCamino: Boolean, agregar: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.ic_marca), null, Modifier.size(84.dp))
        Spacer(Modifier.height(16.dp))
        Text(if (enCamino) "No hay paquetes en camino" else "Aún no se entrega ningún paquete", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            if (enCamino) "Pega una guía o escanea la etiqueta. Las nuevas llegan solas con el lector de notificaciones." else "Aquí quedan los paquetes entregados y los que archives.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (enCamino) TextButton(onClick = agregar) { Text("Agregar una guía", style = MaterialTheme.typography.labelLarge) }
    }
}

private val pasos = listOf("Preparando", "En camino", "En reparto", "Entregado")

@Composable
private fun Tarjeta(p: Paquete, clic: () -> Unit) {
    val c = colorDe(p.estado)
    Card(
        onClick = clic,
        colors = CardDefaults.cardColors(containerColor = Paleta.blanco),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val origen = listOfNotNull(p.tienda, Registro.porId(p.paqueteria)?.nombre).distinct().joinToString(" · ")
                Text(
                    origen.ifEmpty { "Paquete" },
                    style = MaterialTheme.typography.labelMedium,
                    color = Paleta.naranjaTexto,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(hace(p.actualizado), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(p.titulo, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val ultimo = listOfNotNull(p.ultimoEvento, p.ultimaUbicacion).distinct().joinToString(" · ")
            Spacer(Modifier.height(2.dp))
            Text(
                ultimo.ifEmpty { if (p.guia != null) "Esperando el primer movimiento" else "Se actualiza con los avisos de la tienda" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))
            Progreso(p.estado)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(c.fondo, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(p.estado.texto, style = MaterialTheme.typography.labelMedium, color = c.texto)
                }
                Spacer(Modifier.weight(1f))
                p.entregaEstimada?.let {
                    Text("Llega $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun Progreso(e: Estado) {
    val paso = pasoDe(e)
    val color = if (e == Estado.INCIDENCIA) Paleta.rojo else MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        pasos.indices.forEach { i ->
            Box(Modifier.weight(1f).height(6.dp).background(if (i <= paso && e != Estado.SIN_DATOS) color else Paleta.gris, RoundedCornerShape(3.dp)))
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth()) {
        pasos.forEachIndexed { i, t ->
            Text(
                t,
                style = MaterialTheme.typography.labelSmall,
                color = if (i == paso && e != Estado.SIN_DATOS) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

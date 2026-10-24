package com.yokis.uniship.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.data.Paquete
import com.yokis.uniship.paqueterias.Registro
import com.yokis.uniship.trabajo.Rastreador
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Lista(abrir: (Long) -> Unit, agregar: () -> Unit, ajustes: () -> Unit) {
    val ctx = LocalContext.current
    val enCamino by UniShipApp.repo.paquetes.enCamino().collectAsState(initial = null)
    val terminados by UniShipApp.repo.paquetes.terminados().collectAsState(initial = emptyList())
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    var lector by remember { mutableStateOf(true) }
    var refrescando by remember { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UniShip", style = MaterialTheme.typography.headlineSmall) },
                actions = { IconButton(onClick = ajustes) { Icon(Icons.Outlined.Settings, "Ajustes", tint = MaterialTheme.colorScheme.secondary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = agregar,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Agregar guía") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            SecondaryTabRow(selectedTabIndex = pestana, containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.secondary) {
                Tab(pestana == 0, onClick = { pestana = 0 }, text = { Text("En camino" + (enCamino?.size?.takeIf { it > 0 }?.let { " ($it)" } ?: "")) })
                Tab(pestana == 1, onClick = { pestana = 1 }, text = { Text("Entregados") })
            }
            val lista = if (pestana == 0) enCamino else terminados
            PullToRefreshBox(isRefreshing = refrescando, onRefresh = { refrescando = true }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!lector && pestana == 0) item { AvisoLector { Permisos.abrirLector(ctx) } }
                    when {
                        lista == null -> Unit
                        lista.isEmpty() -> item { Vacio(pestana == 0, agregar) }
                        else -> items(lista, key = { it.id }) { Tarjeta(it) { abrir(it.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvisoLector(activar: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Detecta tus guías solas", style = MaterialTheme.typography.titleSmall)
                Text("Activa el lector y UniShip toma las guías de Amazon, Mercado Libre, Temu, Shein y más.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = activar) { Text("Activar", color = MaterialTheme.colorScheme.secondary) }
        }
    }
}

@Composable
private fun Vacio(enCamino: Boolean, agregar: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 64.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (enCamino) "No hay paquetes en camino" else "Todavía no se entrega ningún paquete", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(6.dp))
        Text(
            if (enCamino) "Pega o escanea una guía. Si activaste el lector, las guías nuevas aparecen aquí solas."
            else "Aquí se guardan los paquetes entregados y los que archives.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (enCamino) TextButton(onClick = agregar) { Text("Agregar una guía", color = MaterialTheme.colorScheme.secondary) }
    }
}

@Composable
private fun Tarjeta(p: Paquete, clic: () -> Unit) {
    Card(
        onClick = clic,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Insignia(p.estado)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val origen = listOfNotNull(p.tienda, Registro.porId(p.paqueteria)?.nombre).distinct().joinToString(" · ")
                if (origen.isNotEmpty()) Text(origen, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(p.titulo, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val ultimo = listOfNotNull(p.ultimaUbicacion, p.ultimoEvento).distinct().joinToString(" · ")
                Text(
                    ultimo.ifEmpty { if (p.guia != null) "Esperando el primer movimiento" else "Sin guía: se actualiza con los avisos de la tienda" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Etiqueta(p.estado)
                    Spacer(Modifier.weight(1f))
                    Text(hace(p.actualizado), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

package com.yokis.uniship.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.data.Estado
import com.yokis.uniship.data.Evento
import com.yokis.uniship.data.Fuente
import com.yokis.uniship.data.Paquete
import com.yokis.uniship.paqueterias.Fechas
import com.yokis.uniship.paqueterias.Registro
import com.yokis.uniship.trabajo.Rastreador
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Detalle(id: Long, volver: () -> Unit) {
    val ctx = LocalContext.current
    val repo = UniShipApp.repo
    val paquete by repo.paquetes.observar(id).collectAsState(initial = null)
    val eventos by repo.eventos.dePaquete(id).collectAsState(initial = emptyList())
    val alcance = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var editar by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf(false) }
    val p = paquete

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = volver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.secondary) } },
                actions = {
                    if (p?.guia != null) {
                        if (cargando) CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        else IconButton(onClick = {
                            cargando = true
                            alcance.launch { runCatching { Rastreador.rastrear(ctx, p) }; cargando = false }
                        }) { Icon(Icons.Outlined.Refresh, "Actualizar", tint = MaterialTheme.colorScheme.secondary) }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Más opciones", tint = MaterialTheme.colorScheme.secondary) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val c = Registro.porId(p?.paqueteria)
                        if (p?.guia != null && c != null) DropdownMenuItem(text = { Text("Ver en ${c.nombre}") }, onClick = {
                            menu = false
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.urlPublica(p.guia)))) }
                        })
                        DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; editar = true })
                        if (p != null) DropdownMenuItem(text = { Text(if (p.archivado) "Desarchivar" else "Archivar") }, onClick = {
                            menu = false
                            alcance.launch { repo.paquetes.actualizar(p.copy(archivado = !p.archivado)) }
                        })
                        if (p != null && p.estado != Estado.ENTREGADO) DropdownMenuItem(text = { Text("Marcar como entregado") }, onClick = {
                            menu = false
                            alcance.launch { repo.paquetes.actualizar(p.copy(estado = Estado.ENTREGADO, actualizado = System.currentTimeMillis())) }
                        })
                        DropdownMenuItem(text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; borrar = true })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        if (p == null) return@Scaffold
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item { Encabezado(p) }
            item {
                Spacer(Modifier.height(20.dp))
                Text("Movimientos", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(12.dp))
                if (eventos.isEmpty()) Text(
                    when {
                        p.paqueteria == "jt" -> "J&T muestra el rastreo solo con los últimos 4 dígitos de tu celular. Ponlos en Ajustes > Rastreo y actualiza."
                        p.guia == null -> "Este pedido no tiene guía pública. Aquí aparecerán los avisos que mande la tienda."
                        p.estado == Estado.SIN_DATOS -> "La paquetería todavía no muestra información de esta guía. Revisa que el número y la paquetería sean correctos."
                        else -> "Todavía no hay movimientos. UniShip revisa cada 2 horas."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            itemsIndexed(eventos, key = { _, e -> e.id }) { i, e -> Paso(e, primero = i == 0, ultimo = i == eventos.lastIndex) }
        }
    }

    if (editar && p != null) Editar(p, cerrar = { editar = false }) { nuevo ->
        alcance.launch {
            if (runCatching { repo.paquetes.actualizar(nuevo) }.isFailure) {
                android.widget.Toast.makeText(ctx, "Esa guía ya está en otro paquete", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            editar = false
            if (nuevo.guia != null && (nuevo.paqueteria != p.paqueteria || nuevo.guia != p.guia)) Rastreador.ahora(ctx, nuevo.id)
        }
    }
    if (borrar && p != null) AlertDialog(
        onDismissRequest = { borrar = false },
        title = { Text("¿Eliminar este paquete?") },
        text = { Text("Se borra con todos sus movimientos. No se puede deshacer.") },
        confirmButton = { TextButton(onClick = { alcance.launch { repo.paquetes.borrar(p.id); volver() } }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { borrar = false }) { Text("Cancelar", color = MaterialTheme.colorScheme.secondary) } }
    )
}

@Composable
private fun Encabezado(p: Paquete) {
    val portapapeles = LocalClipboardManager.current
    Column {
        val origen = listOfNotNull(p.tienda, Registro.porId(p.paqueteria)?.nombre).distinct().joinToString(" · ")
        if (origen.isNotEmpty()) Text(origen, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(p.titulo, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Etiqueta(p.estado)
        if (p.guia != null) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Guía", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(p.guia, style = estiloMono, color = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { portapapeles.setText(AnnotatedString(p.guia)) }) { Icon(Icons.Outlined.ContentCopy, "Copiar guía", tint = MaterialTheme.colorScheme.secondary) }
            }
        }
    }
}

@Composable
private fun Paso(e: Evento, primero: Boolean, ultimo: Boolean) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(6.dp).background(if (primero) Paleta.crema else MaterialTheme.colorScheme.outline))
            Box(Modifier.size(if (primero) 14.dp else 10.dp).background(if (primero) MaterialTheme.colorScheme.primary else Paleta.cafeSuave, CircleShape))
            if (!ultimo) Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outline))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = 20.dp)) {
            Text(
                e.descripcion,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (primero) FontWeight.SemiBold else FontWeight.Normal,
                color = if (primero) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            val detalle = listOfNotNull(
                e.ubicacion,
                Fechas.corta(e.fecha),
                when (e.fuente) {
                    Fuente.APP -> "según la app"
                    Fuente.CORREO -> "según el correo"
                    Fuente.MERCADO_LIBRE -> "Mercado Libre"
                    else -> null
                }
            ).joinToString(" · ")
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Editar(p: Paquete, cerrar: () -> Unit, guardar: (Paquete) -> Unit) {
    var titulo by remember { mutableStateOf(p.titulo) }
    var guia by remember { mutableStateOf(p.guia.orEmpty()) }
    var paqueteria by remember { mutableStateOf(p.paqueteria) }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text("Editar paquete") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(titulo, { titulo = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(guia, { guia = it.uppercase().trim() }, label = { Text("Guía") }, singleLine = true, textStyle = estiloMono)
                Text("Paquetería", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Registro.todas.forEach { c ->
                        FilterChip(selected = paqueteria == c.id, onClick = { paqueteria = c.id }, label = { Text(c.nombre) })
                    }
                }
                HorizontalDivider()
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val g = guia.takeIf { it.isNotBlank() }?.let { Registro.normalizar(it) }
                guardar(p.copy(titulo = titulo.ifBlank { p.titulo }, guia = g, paqueteria = paqueteria, fallos = 0))
            }) { Text("Guardar", color = MaterialTheme.colorScheme.secondary) }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar", color = MaterialTheme.colorScheme.secondary) } }
    )
}

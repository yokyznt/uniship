package com.yokis.uniship.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.data.Origen
import com.yokis.uniship.paqueterias.Registro
import com.yokis.uniship.trabajo.Rastreador
import kotlinx.coroutines.launch

private val tiendas = listOf("Amazon", "Mercado Libre", "Temu", "Shein", "AliExpress", "TikTok Shop")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Agregar(escaneada: String?, escanear: () -> Unit, volver: () -> Unit, guardado: (Long) -> Unit) {
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var guia by rememberSaveable { mutableStateOf("") }
    var titulo by rememberSaveable { mutableStateOf("") }
    var tienda by rememberSaveable { mutableStateOf<String?>(null) }
    var elegida by rememberSaveable { mutableStateOf<String?>(null) }
    var porEscaneo by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(escaneada) {
        if (!escaneada.isNullOrBlank()) {
            guia = Registro.normalizar(escaneada)
            porEscaneo = true
        }
    }
    val limpia = Registro.normalizar(guia)
    val detectadas = remember(limpia) { if (limpia.length >= 6) Registro.detectar(limpia) else emptyList() }
    val paqueteria = elegida?.takeIf { id -> detectadas.any { it.id == id } || Registro.porId(id) != null } ?: detectadas.firstOrNull()?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agregar guía", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = volver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.secondary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = guia,
                onValueChange = { guia = it.uppercase(); error = null },
                label = { Text("Número de guía") },
                singleLine = true,
                textStyle = estiloMono,
                isError = error != null,
                supportingText = { Text(error ?: "Pégala o escanea el código de barras de la etiqueta") },
                trailingIcon = { IconButton(onClick = escanear) { Icon(Icons.Outlined.QrCodeScanner, "Escanear", tint = MaterialTheme.colorScheme.secondary) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (detectadas.isEmpty() && limpia.length >= 6) "No reconozco el formato. Elige la paquetería:" else "Paquetería",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val opciones = if (detectadas.isEmpty()) Registro.todas else detectadas + Registro.todas.filter { it !in detectadas }
                opciones.forEach { c ->
                    FilterChip(selected = paqueteria == c.id, onClick = { elegida = c.id }, label = { Text(c.nombre) })
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                label = { Text("Nombre (opcional)") },
                placeholder = { Text("Ej. Audífonos") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text("Tienda (opcional)", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tiendas.forEach { t -> FilterChip(selected = tienda == t, onClick = { tienda = if (tienda == t) null else t }, label = { Text(t) }) }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (limpia.length < 6) { error = "La guía es muy corta"; return@Button }
                    alcance.launch {
                        val p = UniShipApp.repo.agregarGuia(limpia, paqueteria, titulo, tienda, if (porEscaneo) Origen.ESCANEO else Origen.MANUAL)
                        Rastreador.ahora(ctx, p.id)
                        guardado(p.id)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Rastrear") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

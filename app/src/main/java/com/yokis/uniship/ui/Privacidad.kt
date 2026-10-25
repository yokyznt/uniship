package com.yokis.uniship.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object AvisoPrivacidad {
    const val VERSION = 4
    const val ACTUALIZADO = "25 de octubre de 2026"

    data class Apartado(val titulo: String, val parrafos: List<String> = emptyList(), val puntos: List<String> = emptyList())

    val apartados = listOf(
        Apartado(
            "En pocas palabras",
            listOf("UniShip guarda tus paquetes en tu teléfono. No vende tus datos, no muestra anuncios y no tiene rastreadores de publicidad. Solo manda a internet lo necesario para consultar el estado de tus envíos y, si tú lo activas, para guardar tu respaldo.")
        ),
        Apartado(
            "Lector de notificaciones",
            listOf("Es opcional y lo activas tú en los ajustes de Android. Si está activo, UniShip revisa únicamente las notificaciones de estas apps:"),
            listOf("Mercado Libre y Mercado Pago", "Amazon", "Temu", "Shein", "AliExpress", "TikTok", "DHL y FedEx", "Gmail, solo los avisos que mencionan una tienda o paquetería y hablan de un envío")
        ),
        Apartado(
            "Qué toma de esas notificaciones",
            listOf("Solo el número de guía y el texto del aviso de envío (por ejemplo «Tu pedido salió a entrega»), para crear el paquete y mostrarlo en su línea de tiempo. Las notificaciones de cualquier otra app, y las promociones de las tiendas, se ignoran: no se leen a fondo, no se guardan y no salen de tu teléfono.")
        ),
        Apartado(
            "Qué se guarda en tu teléfono",
            puntos = listOf(
                "Tus paquetes: número de guía, paquetería, tienda, nombre que les pongas y sus movimientos.",
                "Los últimos 4 dígitos de tu celular, solo si los escribes para consultar J&T Express.",
                "La sesión de Mercado Libre, solo si la conectas.",
                "Las sesiones de AliExpress y Amazon, solo si entras a ellas desde UniShip. Se quedan en tu teléfono como en un navegador y nunca se mandan a nuestro servidor.",
                "La base de datos queda fuera del respaldo automático de Android para que no se copie a la nube sin que lo decidas."
            )
        ),
        Apartado(
            "Qué se manda a internet",
            puntos = listOf(
                "El número de guía, a la página pública de rastreo de su paquetería (J&T, iMile, Estafeta, DHL, FedEx, 99minutos, Paquetexpress, Correos de México o Redpack). Es lo mismo que pasa cuando lo buscas en su sitio. Para J&T también se mandan los 4 dígitos de tu celular, porque así lo pide J&T.",
                "Si conectas Mercado Libre: UniShip consulta tus compras y envíos con la API oficial de Mercado Libre, usando el permiso que tú das. Nuestro servidor solo interviene para canjear el permiso y nunca guarda tus compras.",
                "Si conectas AliExpress o Amazon: UniShip consulta desde tu teléfono la misma página de pedidos que ves en su sitio, solo para leer el estado, la paquetería y la guía de tus envíos. No compra, no cancela y no cambia nada. Tu contraseña la recibe la tienda, no UniShip.",
                "Si inicias sesión con Google: se sube una copia de tus paquetes a nuestro servidor (Vercel y Upstash), ligada a tu cuenta, para que puedas recuperarla en otro teléfono. No guardamos tu correo ni tus contactos."
            )
        ),
        Apartado(
            "Cámara",
            listOf("Solo se usa cuando tocas «Escanear» para leer el código de barras de una etiqueta. La imagen se procesa en el teléfono y no se guarda ni se envía.")
        ),
        Apartado(
            "Con quién se comparte",
            listOf("Con nadie para publicidad ni para venta. Las únicas empresas que reciben datos son las paqueterías (el número de guía), Mercado Libre (si lo conectas), Google (si inicias sesión) y los proveedores del servidor de respaldo (Vercel y Upstash).")
        ),
        Apartado(
            "Cómo controlar y borrar tus datos",
            puntos = listOf(
                "Apaga el lector en Ajustes > Permisos.",
                "Desconecta Mercado Libre en Ajustes > Mercado Libre.",
                "Borra tu respaldo en la nube en Ajustes > Respaldo.",
                "Borra todo lo de este teléfono con Ajustes > Privacidad > Borrar todos mis datos, o desinstalando la app."
            )
        ),
        Apartado(
            "Menores de edad",
            listOf("UniShip no está dirigida a menores de 13 años y no recopila datos de ellos a sabiendas.")
        ),
        Apartado(
            "Cambios a este aviso",
            listOf("Si cambia lo que la app lee o guarda, actualizaremos este aviso y la fecha de arriba, y te lo mostraremos al abrir la app.")
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Privacidad(volver: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aviso de privacidad", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = volver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.secondary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Text("Última actualización: ${AvisoPrivacidad.ACTUALIZADO}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            items(AvisoPrivacidad.apartados) { a ->
                Column(Modifier.padding(top = 16.dp)) {
                    Text(a.titulo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                    a.parrafos.forEach {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    a.puntos.forEach {
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

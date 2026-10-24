package com.yokis.uniship.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yokis.uniship.data.Estado

fun iconoDe(e: Estado): ImageVector = when (e) {
    Estado.PENDIENTE -> Icons.Outlined.Inventory2
    Estado.EN_TRANSITO -> Icons.Outlined.LocalShipping
    Estado.EN_REPARTO -> Icons.Outlined.DeliveryDining
    Estado.ENTREGADO -> Icons.Outlined.CheckCircle
    Estado.INCIDENCIA -> Icons.Outlined.ReportProblem
    Estado.SIN_DATOS -> Icons.Outlined.HelpOutline
}

@Composable
fun Etiqueta(e: Estado, modifier: Modifier = Modifier) {
    val c = colorDe(e)
    Row(
        modifier.background(c.fondo, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(iconoDe(e), null, tint = c.texto, modifier = Modifier.size(14.dp))
        Text(e.texto, style = MaterialTheme.typography.labelMedium, color = c.texto)
    }
}

@Composable
fun Insignia(e: Estado, modifier: Modifier = Modifier) {
    val c = colorDe(e)
    Box(modifier.size(40.dp).background(c.fondo, CircleShape), contentAlignment = Alignment.Center) {
        Icon(iconoDe(e), null, tint = c.texto, modifier = Modifier.size(22.dp))
    }
}

fun hace(ms: Long): String {
    val d = (System.currentTimeMillis() - ms) / 60000
    return when {
        d < 1 -> "ahora"
        d < 60 -> "hace $d min"
        d < 60 * 24 -> "hace ${d / 60} h"
        d < 60 * 24 * 2 -> "ayer"
        d < 60 * 24 * 30 -> "hace ${d / (60 * 24)} días"
        else -> com.yokis.uniship.paqueterias.Fechas.corta(ms)
    }
}

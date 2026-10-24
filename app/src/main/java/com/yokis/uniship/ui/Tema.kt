package com.yokis.uniship.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yokis.uniship.data.Estado

object Paleta {
    val crema = Color(0xFFFFFBF5)
    val blanco = Color(0xFFFFFFFF)
    val arena = Color(0xFFF6EDE3)
    val naranja = Color(0xFFE07A2F)
    val naranjaSuave = Color(0xFFFBE3D0)
    val naranjaTexto = Color(0xFFA9501A)
    val cafe = Color(0xFF6B4226)
    val cafeSuave = Color(0xFFEFE2D6)
    val tinta = Color(0xFF3B2416)
    val tintaSuave = Color(0xFF7D604C)
    val borde = Color(0xFFEADBCB)
    val rojo = Color(0xFFB3261E)
    val rojoSuave = Color(0xFFF9DEDC)
    val verde = Color(0xFF3F6B2A)
    val verdeSuave = Color(0xFFE3EDD9)
}

private val esquema = lightColorScheme(
    primary = Paleta.naranja,
    onPrimary = Paleta.tinta,
    primaryContainer = Paleta.naranjaSuave,
    onPrimaryContainer = Paleta.tinta,
    secondary = Paleta.cafe,
    onSecondary = Paleta.blanco,
    secondaryContainer = Paleta.naranjaSuave,
    onSecondaryContainer = Paleta.tinta,
    tertiary = Paleta.cafe,
    background = Paleta.crema,
    onBackground = Paleta.tinta,
    surface = Paleta.crema,
    onSurface = Paleta.tinta,
    surfaceVariant = Paleta.arena,
    onSurfaceVariant = Paleta.tintaSuave,
    surfaceContainerLowest = Paleta.blanco,
    surfaceContainerLow = Paleta.blanco,
    surfaceContainer = Paleta.arena,
    surfaceContainerHigh = Paleta.arena,
    surfaceContainerHighest = Paleta.cafeSuave,
    outline = Paleta.borde,
    outlineVariant = Paleta.borde,
    error = Paleta.rojo,
    errorContainer = Paleta.rojoSuave
)

private val base = Typography()
private val tipografia = Typography(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, color = Paleta.cafe, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, color = Paleta.cafe),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, color = Paleta.cafe),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = Paleta.cafe),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall
)

private val formas = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun UniShipTema(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = esquema, typography = tipografia, shapes = formas, content = contenido)
}

data class ColorEstado(val fondo: Color, val texto: Color)

fun colorDe(e: Estado) = when (e) {
    Estado.EN_REPARTO -> ColorEstado(Paleta.naranjaSuave, Paleta.naranjaTexto)
    Estado.EN_TRANSITO -> ColorEstado(Paleta.cafeSuave, Paleta.cafe)
    Estado.ENTREGADO -> ColorEstado(Paleta.verdeSuave, Paleta.verde)
    Estado.INCIDENCIA -> ColorEstado(Paleta.rojoSuave, Paleta.rojo)
    Estado.PENDIENTE, Estado.SIN_DATOS -> ColorEstado(Paleta.arena, Paleta.tintaSuave)
}

val estiloMono = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 14.sp, letterSpacing = 0.5.sp)

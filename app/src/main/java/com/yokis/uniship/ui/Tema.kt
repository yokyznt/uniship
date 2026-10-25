package com.yokis.uniship.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yokis.uniship.R
import com.yokis.uniship.data.Estado

object Paleta {
    val fondo = Color(0xFFFFF8F1)
    val blanco = Color(0xFFFFFFFF)
    val arena = Color(0xFFFFF0E2)
    val naranja = Color(0xFFF26B12)
    val naranjaFuerte = Color(0xFFE05A00)
    val naranjaSuave = Color(0xFFFFE2C9)
    val naranjaTexto = Color(0xFFB04700)
    val durazno = Color(0xFFFFF4EA)
    val tinta = Color(0xFF2A2522)
    val tintaSuave = Color(0xFF6E6560)
    val borde = Color(0xFFF4E1D0)
    val gris = Color(0xFFF2EEEA)
    val rojo = Color(0xFFC62828)
    val rojoSuave = Color(0xFFFDE2E0)
    val verde = Color(0xFF2E7D32)
    val verdeSuave = Color(0xFFE2F3E1)
}

val Nunito = FontFamily(
    Font(R.font.nunito_400, FontWeight.Normal),
    Font(R.font.nunito_500, FontWeight.Medium),
    Font(R.font.nunito_600, FontWeight.SemiBold),
    Font(R.font.nunito_700, FontWeight.Bold),
    Font(R.font.nunito_800, FontWeight.ExtraBold)
)

private val esquema = lightColorScheme(
    primary = Paleta.naranjaFuerte,
    onPrimary = Paleta.blanco,
    primaryContainer = Paleta.naranjaSuave,
    onPrimaryContainer = Paleta.naranjaTexto,
    secondary = Paleta.naranjaTexto,
    onSecondary = Paleta.blanco,
    secondaryContainer = Paleta.naranjaSuave,
    onSecondaryContainer = Paleta.naranjaTexto,
    tertiary = Paleta.naranja,
    background = Paleta.fondo,
    onBackground = Paleta.tinta,
    surface = Paleta.fondo,
    onSurface = Paleta.tinta,
    surfaceVariant = Paleta.arena,
    onSurfaceVariant = Paleta.tintaSuave,
    surfaceContainerLowest = Paleta.blanco,
    surfaceContainerLow = Paleta.blanco,
    surfaceContainer = Paleta.blanco,
    surfaceContainerHigh = Paleta.blanco,
    surfaceContainerHighest = Paleta.arena,
    outline = Paleta.borde,
    outlineVariant = Paleta.borde,
    error = Paleta.rojo,
    errorContainer = Paleta.rojoSuave
)

private val base = Typography()
private fun TextStyle.n(peso: FontWeight, color: Color = Color.Unspecified) = copy(fontFamily = Nunito, fontWeight = peso, color = color)

private val tipografia = Typography(
    displaySmall = base.displaySmall.n(FontWeight.ExtraBold, Paleta.tinta),
    headlineLarge = base.headlineLarge.n(FontWeight.ExtraBold, Paleta.tinta).copy(letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.n(FontWeight.ExtraBold, Paleta.tinta),
    headlineSmall = base.headlineSmall.n(FontWeight.ExtraBold, Paleta.tinta),
    titleLarge = base.titleLarge.n(FontWeight.ExtraBold, Paleta.tinta),
    titleMedium = base.titleMedium.n(FontWeight.Bold),
    titleSmall = base.titleSmall.n(FontWeight.Bold, Paleta.naranjaTexto),
    bodyLarge = base.bodyLarge.n(FontWeight.Medium),
    bodyMedium = base.bodyMedium.n(FontWeight.Medium),
    bodySmall = base.bodySmall.n(FontWeight.Medium),
    labelLarge = base.labelLarge.n(FontWeight.Bold).copy(fontSize = 16.sp),
    labelMedium = base.labelMedium.n(FontWeight.Bold),
    labelSmall = base.labelSmall.n(FontWeight.Bold)
)

private val formas = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp)
)

@Composable
fun UniShipTema(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = esquema, typography = tipografia, shapes = formas, content = contenido)
}

data class ColorEstado(val fondo: Color, val texto: Color)

fun colorDe(e: Estado) = when (e) {
    Estado.EN_REPARTO -> ColorEstado(Paleta.naranja, Paleta.blanco)
    Estado.EN_TRANSITO -> ColorEstado(Paleta.naranjaSuave, Paleta.naranjaTexto)
    Estado.ENTREGADO -> ColorEstado(Paleta.verdeSuave, Paleta.verde)
    Estado.INCIDENCIA -> ColorEstado(Paleta.rojoSuave, Paleta.rojo)
    Estado.PENDIENTE, Estado.SIN_DATOS -> ColorEstado(Paleta.gris, Paleta.tintaSuave)
}

fun pasoDe(e: Estado) = when (e) {
    Estado.PENDIENTE, Estado.SIN_DATOS -> 0
    Estado.EN_TRANSITO, Estado.INCIDENCIA -> 1
    Estado.EN_REPARTO -> 2
    Estado.ENTREGADO -> 3
}

val estiloMono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, letterSpacing = 0.5.sp)

package com.yokis.uniship.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.yokis.uniship.MainActivity
import com.yokis.uniship.Notificador
import com.yokis.uniship.R
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.data.Estado
import com.yokis.uniship.data.Paquete
import com.yokis.uniship.ui.pasoDe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class Widget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val lista = UniShipApp.repo.paquetes.enCamino().first().take(4)
        provideContent { Contenido(context, lista) }
    }

    companion object {
        private val fondo = ColorProvider(Color(0xFFFFF8F1))
        private val tinta = ColorProvider(Color(0xFF2A2522))
        private val suave = ColorProvider(Color(0xFF6E6560))
        private val naranja = ColorProvider(Color(0xFFE05A00))
        private val gris = ColorProvider(Color(0xFFF2EEEA))
        private val blanco = ColorProvider(Color.White)

        suspend fun actualizar(ctx: Context) = Widget().updateAll(ctx)
        fun refrescar(ctx: Context) { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { runCatching { actualizar(ctx.applicationContext) } } }

        fun avance(e: Estado) = when (e) {
            Estado.ENTREGADO -> 1f
            Estado.SIN_DATOS -> 0.03f
            else -> (pasoDe(e) + 0.55f) / 4f
        }
    }

    @Composable
    private fun Contenido(ctx: Context, lista: List<Paquete>) {
        GlanceTheme {
            Column(GlanceModifier.fillMaxSize().background(fondo).cornerRadius(22.dp).padding(14.dp).clickable(actionStartActivity<MainActivity>())) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                    Image(ImageProvider(R.drawable.ic_marca), null, GlanceModifier.size(22.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    Text("UniShip", style = TextStyle(color = naranja, fontWeight = FontWeight.Bold, fontSize = 15.sp))
                    Spacer(GlanceModifier.defaultWeight())
                    Text(if (lista.isEmpty()) "" else "${lista.size} en camino", style = TextStyle(color = suave, fontSize = 12.sp))
                }
                Spacer(GlanceModifier.height(10.dp))
                if (lista.isEmpty()) Text("No tienes paquetes en camino", style = TextStyle(color = suave, fontSize = 13.sp))
                lista.forEach { p ->
                    Column(
                        GlanceModifier.fillMaxWidth().background(blanco).cornerRadius(16.dp).padding(horizontal = 12.dp, vertical = 9.dp)
                            .clickable(actionStartActivity<MainActivity>(actionParametersOf(ActionParameters.Key<Long>(Notificador.EXTRA_PAQUETE) to p.id)))
                    ) {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.titulo, maxLines = 1, style = TextStyle(color = tinta, fontWeight = FontWeight.Bold, fontSize = 13.sp), modifier = GlanceModifier.defaultWeight())
                            Text(p.estado.texto, style = TextStyle(color = naranja, fontWeight = FontWeight.Medium, fontSize = 11.sp))
                        }
                        Text(listOfNotNull(p.ultimaUbicacion, p.entregaEstimada?.let { "llega $it" }).joinToString(" · ").ifEmpty { p.ultimoEvento ?: "" }, maxLines = 1, style = TextStyle(color = suave, fontSize = 11.sp))
                        Spacer(GlanceModifier.height(6.dp))
                        LinearProgressIndicator(avance(p.estado), GlanceModifier.fillMaxWidth().height(5.dp), color = naranja, backgroundColor = gris)
                    }
                    Spacer(GlanceModifier.height(6.dp))
                }
            }
        }
    }
}

class ReceptorWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = Widget()
}

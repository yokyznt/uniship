package com.yokis.uniship.ui

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.data.Evento
import com.yokis.uniship.paqueterias.Clasificador
import com.yokis.uniship.paqueterias.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.URLEncoder

object Lugares {
    private val prefs get() = UniShipApp.instancia.getSharedPreferences("lugares", Context.MODE_PRIVATE)
    private var ultima = 0L

    private val alias = mapOf(
        "mexico center" to "Ciudad de México", "mexico.sc" to "Cuautitlán Izcalli", "mexico sc" to "Cuautitlán Izcalli",
        "cdmx" to "Ciudad de México", "mexico city" to "Ciudad de México"
    )

    fun limpiar(lugar: String): String? {
        var t = lugar.trim()
        alias[Clasificador.sinAcentos(t)]?.let { return it }
        t = t.replace(Regex("^[A-Z]{2,4}-"), "").replace(Regex("\\.[A-Za-z]{1,6}$"), "").replace(Regex("(?i)\\b(hub|cedis|center|centro de distribuci[oó]n|pdv|ds|sc)\\b"), "").trim(' ', ',', '.', '-')
        alias[Clasificador.sinAcentos(t)]?.let { return it }
        return t.takeIf { it.length >= 3 && it.any(Char::isLetter) }
    }

    suspend fun ubicar(lugar: String): GeoPoint? {
        val q = limpiar(lugar) ?: return null
        val llave = Clasificador.sinAcentos(q)
        prefs.getString(llave, null)?.let { v -> return if (v.isEmpty()) null else v.split(",").let { GeoPoint(it[0].toDouble(), it[1].toDouble()) } }
        val espera = 1100 - (System.currentTimeMillis() - ultima)
        if (espera > 0) delay(espera)
        ultima = System.currentTimeMillis()
        val p = withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=mx&accept-language=es&q=" + URLEncoder.encode(q, "UTF-8")
                val a = JSONArray(Http.get(url, mapOf("User-Agent" to "UniShip/1.3 (Android; com.yokis.uniship)")))
                a.optJSONObject(0)?.let { GeoPoint(it.getString("lat").toDouble(), it.getString("lon").toDouble()) }
            }.getOrElse { return@withContext null to false }.let { it to true }
        }
        if (p.second) prefs.edit().putString(llave, p.first?.let { "${it.latitude},${it.longitude}" } ?: "").apply()
        return p.first
    }
}

@Composable
fun Mapa(eventos: List<Evento>) {
    var puntos by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    val lugares = remember(eventos) { eventos.sortedBy { it.fecha }.mapNotNull { it.ubicacion }.fold(mutableListOf<String>()) { acc, l -> if (acc.lastOrNull() != l) acc += l; acc } }
    LaunchedEffect(lugares) {
        val r = mutableListOf<GeoPoint>()
        for (l in lugares) Lugares.ubicar(l)?.let { g -> if (r.lastOrNull()?.let { it.distanceToAsDouble(g) < 500 } != true) { r += g; puntos = r.toList() } }
    }
    if (puntos.isEmpty()) return
    val naranja = Paleta.naranjaFuerte.toArgb()
    Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(22.dp))) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(210.dp),
            factory = { c ->
                Configuration.getInstance().userAgentValue = c.packageName
                MapView(c).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    isTilesScaledToDpi = true
                }
            },
            update = { m ->
                m.overlays.clear()
                if (puntos.size > 1) m.overlays += Polyline(m).apply {
                    setPoints(puntos)
                    outlinePaint.color = naranja
                    outlinePaint.strokeWidth = 9f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                }
                puntos.forEachIndexed { i, g ->
                    m.overlays += Marker(m).apply {
                        position = g
                        setAnchor(Marker.ANCHOR_CENTER, if (i == puntos.lastIndex) Marker.ANCHOR_BOTTOM else Marker.ANCHOR_CENTER)
                        icon = androidx.core.content.ContextCompat.getDrawable(m.context, if (i == puntos.lastIndex) com.yokis.uniship.R.drawable.ic_pin else com.yokis.uniship.R.drawable.ic_punto)
                        title = lugares.getOrNull(i)
                    }
                }
                m.post {
                    if (puntos.size == 1) { m.controller.setZoom(11.0); m.controller.setCenter(puntos[0]) }
                    else m.zoomToBoundingBox(BoundingBox.fromGeoPoints(puntos).increaseByScale(1.4f), false)
                }
                m.invalidate()
            },
            onRelease = { it.onDetach() }
        )
    }
}

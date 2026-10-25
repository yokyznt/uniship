package com.yokis.uniship.ui

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.CenterFocusStrong
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

object Rutas {
    private val prefs get() = UniShipApp.instancia.getSharedPreferences("rutas", Context.MODE_PRIVATE)

    fun decodificar(p: String, precision: Double = 1e6): List<GeoPoint> {
        val r = mutableListOf<GeoPoint>()
        var i = 0
        var lat = 0L
        var lon = 0L
        while (i < p.length) {
            for (k in 0..1) {
                var resultado = 0L
                var corrimiento = 0
                var b: Int
                do {
                    b = p[i++].code - 63
                    resultado = resultado or ((b and 0x1f).toLong() shl corrimiento)
                    corrimiento += 5
                } while (b >= 0x20)
                val d = if (resultado and 1L != 0L) (resultado shr 1).inv() else resultado shr 1
                if (k == 0) lat += d else lon += d
            }
            r += GeoPoint(lat / precision, lon / precision)
        }
        return r
    }

    suspend fun trazar(puntos: List<GeoPoint>): List<GeoPoint> {
        if (puntos.size < 2) return puntos
        val coords = puntos.joinToString(";") { "%.5f,%.5f".format(java.util.Locale.US, it.longitude, it.latitude) }
        prefs.getString(coords, null)?.let { return decodificar(it) }
        val geo = withContext(Dispatchers.IO) {
            runCatching {
                val j = org.json.JSONObject(Http.get("https://router.project-osrm.org/route/v1/driving/$coords?overview=full&geometries=polyline6", mapOf("User-Agent" to "UniShip/1.3 (Android; com.yokis.uniship)")))
                j.optJSONArray("routes")?.optJSONObject(0)?.optString("geometry")?.ifBlank { null }
            }.getOrNull()
        } ?: return puntos
        prefs.edit().putString(coords, geo).apply()
        return decodificar(geo)
    }
}

@Composable
fun Mapa(eventos: List<Evento>) {
    var puntos by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var camino by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var grande by remember { mutableStateOf(false) }
    var mapa by remember { mutableStateOf<MapView?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun permitido() = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var conUbicacion by remember { mutableStateOf(permitido()) }
    var irAMi by remember { mutableStateOf(false) }
    val pedir = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
        conUbicacion = r.values.any { it }
        irAMi = conUbicacion
    }
    val yo = remember(mapa, conUbicacion) {
        val m = mapa
        if (m == null || !conUbicacion) null else org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay(org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider(m.context).apply {
            addLocationSource(android.location.LocationManager.NETWORK_PROVIDER)
        }, m).apply {
            val punto = androidx.core.content.ContextCompat.getDrawable(m.context, com.yokis.uniship.R.drawable.ic_yo)!!
            val bmp = android.graphics.Bitmap.createBitmap(punto.intrinsicWidth, punto.intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888)
            punto.setBounds(0, 0, bmp.width, bmp.height); punto.draw(android.graphics.Canvas(bmp))
            setPersonIcon(bmp); setDirectionIcon(bmp)
            setPersonAnchor(0.5f, 0.5f); setDirectionAnchor(0.5f, 0.5f)
            isDrawAccuracyEnabled = true
            enableMyLocation()
        }
    }
    androidx.compose.runtime.DisposableEffect(yo) { onDispose { yo?.disableMyLocation() } }
    LaunchedEffect(irAMi, yo) {
        if (irAMi && yo != null) {
            irAMi = false
            yo.runOnFirstFix { mapa?.post { yo.myLocation?.let { mapa?.controller?.animateTo(it, 13.0, 500L) } } }
        }
    }
    val lugares = remember(eventos) { eventos.sortedBy { it.fecha }.mapNotNull { it.ubicacion }.fold(mutableListOf<String>()) { acc, l -> if (acc.lastOrNull() != l) acc += l; acc } }
    LaunchedEffect(lugares) {
        val r = mutableListOf<GeoPoint>()
        for (l in lugares) Lugares.ubicar(l)?.let { g -> if (r.lastOrNull()?.let { it.distanceToAsDouble(g) < 500 } != true) { r += g; puntos = r.toList(); camino = r.toList() } }
        camino = Rutas.trazar(r)
    }
    if (puntos.isEmpty()) return
    val naranja = Paleta.naranjaFuerte.toArgb()
    val alto by androidx.compose.animation.core.animateDpAsState(if (grande) 460.dp else 260.dp, label = "alto")
    Box(Modifier.fillMaxWidth().height(alto).clip(RoundedCornerShape(22.dp))) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(alto),
            factory = { c ->
                Configuration.getInstance().userAgentValue = c.packageName
                MapView(c).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    isTilesScaledToDpi = true
                    minZoomLevel = 4.0
                    maxZoomLevel = 19.0
                    @Suppress("ClickableViewAccessibility")
                    setOnTouchListener { v, e ->
                        if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN || e.actionMasked == android.view.MotionEvent.ACTION_POINTER_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                        if (e.actionMasked == android.view.MotionEvent.ACTION_UP || e.actionMasked == android.view.MotionEvent.ACTION_CANCEL) v.parent?.requestDisallowInterceptTouchEvent(false)
                        false
                    }
                    mapa = this
                }
            },
            update = { m ->
                val nuevo = m.getTag(com.yokis.uniship.R.id.ruta_trazada) != camino
                m.overlays.clear()
                if (camino.size > 1) {
                    m.overlays += Polyline(m).apply {
                        setPoints(camino)
                        outlinePaint.color = android.graphics.Color.WHITE
                        outlinePaint.strokeWidth = 16f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                    }
                    m.overlays += Polyline(m).apply {
                        setPoints(camino)
                        outlinePaint.color = naranja
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                    }
                }
                puntos.forEachIndexed { i, g ->
                    m.overlays += Marker(m).apply {
                        position = g
                        setAnchor(Marker.ANCHOR_CENTER, if (i == puntos.lastIndex) Marker.ANCHOR_BOTTOM else Marker.ANCHOR_CENTER)
                        icon = androidx.core.content.ContextCompat.getDrawable(m.context, if (i == puntos.lastIndex) com.yokis.uniship.R.drawable.ic_pin else com.yokis.uniship.R.drawable.ic_punto)
                        title = lugares.getOrNull(i)
                    }
                }
                yo?.let { m.overlays += it }
                if (nuevo) {
                    m.setTag(com.yokis.uniship.R.id.ruta_trazada, camino)
                    m.post {
                        if (puntos.size == 1) { m.controller.setZoom(11.0); m.controller.setCenter(puntos[0]) }
                        else m.zoomToBoundingBox(BoundingBox.fromGeoPoints(camino).increaseByScale(1.3f), false)
                    }
                }
                m.invalidate()
            },
            onRelease = { it.onDetach() }
        )
        androidx.compose.foundation.layout.Column(
            Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(10.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            BotonMapa(if (grande) androidx.compose.material.icons.Icons.Outlined.CloseFullscreen else androidx.compose.material.icons.Icons.Outlined.OpenInFull, if (grande) "Achicar mapa" else "Agrandar mapa") { grande = !grande }
            BotonMapa(androidx.compose.material.icons.Icons.Outlined.Add, "Acercar") { mapa?.controller?.zoomIn() }
            BotonMapa(androidx.compose.material.icons.Icons.Outlined.Remove, "Alejar") { mapa?.controller?.zoomOut() }
            BotonMapa(androidx.compose.material.icons.Icons.Outlined.MyLocation, "Mi ubicación") {
                if (!conUbicacion) pedir.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION))
                else yo?.myLocation?.let { mapa?.controller?.animateTo(it, 13.0, 500L) } ?: run { irAMi = true }
            }
            BotonMapa(androidx.compose.material.icons.Icons.Outlined.CenterFocusStrong, "Ver toda la ruta") {
                mapa?.let { m -> if (camino.size > 1) m.zoomToBoundingBox(BoundingBox.fromGeoPoints(camino).increaseByScale(1.3f), true) else m.controller.animateTo(puntos[0], 11.0, 400L) }
            }
        }
    }
}

@Composable
private fun BotonMapa(icono: androidx.compose.ui.graphics.vector.ImageVector, desc: String, alPulsar: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = alPulsar,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Paleta.blanco,
        shadowElevation = 3.dp,
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Icon(icono, desc, tint = Paleta.naranjaTexto, modifier = Modifier.size(20.dp))
        }
    }
}

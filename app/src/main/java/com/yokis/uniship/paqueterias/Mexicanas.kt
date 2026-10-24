package com.yokis.uniship.paqueterias

import com.yokis.uniship.data.Estado
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.time.Year

private fun limpiar(t: String?) = t?.replace(Regex("\\s+"), " ")?.trim()?.ifBlank { null }

object Estafeta : Paqueteria {
    override val id = "estafeta"
    override val nombre = "Estafeta"
    override val patron = Regex("^(\\d{10}|(?=[0-9A-Z]*[A-Z])(?=[0-9A-Z]*\\d)[0-9A-Z]{22})$")
    override val fuerte get() = false
    override fun urlPublica(guia: String) = "https://cs.estafeta.com/es/Tracking/searchByGet?wayBillType=1&wayBill=$guia"

    fun guiaLarga(html: String): String? {
        val d = Jsoup.parse(html)
        if (d.selectFirst(".shipmentInfoDiv") == null) return null
        return d.select(".shipmentInfoSeparator").firstOrNull { it.selectFirst(".fontRoman")?.text()?.contains("guía", true) == true }
            ?.selectFirst(".fontBold")?.text()?.trim()?.ifBlank { null }
    }

    fun historial(html: String): List<Movimiento> {
        val d = Jsoup.parse(html)
        return d.select(".vertical_dotted_line").flatMap { bloque ->
            val dia = bloque.select(".historyEventBottom").map { it.text().trim() }.firstOrNull { it.matches(Regex("\\d{2}/\\d{2}/\\d{4}")) } ?: return@flatMap emptyList()
            bloque.select(".row.eventInfo").mapNotNull { fila ->
                val c = fila.children()
                if (c.size < 3) return@mapNotNull null
                val hora = c[0].text().replace("hrs.", "").trim()
                val fecha = Fechas.con("$dia $hora", "dd/MM/yyyy HH:mm") ?: Fechas.con(dia, "dd/MM/yyyy") ?: return@mapNotNull null
                val desc = limpiar(c[2].text()) ?: return@mapNotNull null
                Movimiento(fecha, desc, limpiar(c[1].text()))
            }
        }
    }

    override suspend fun rastrear(guia: String): List<Movimiento> = withContext(Dispatchers.IO) {
        val larga = if (guia.length == 22) guia else guiaLarga(Http.get(urlPublica(guia))) ?: return@withContext emptyList()
        historial(Http.postForm("https://cs.estafeta.com/es/Tracking/GetTrackingItemHistory", mapOf("waybill" to larga)))
    }
}

object Paquetexpress : Paqueteria {
    override val id = "paquetexpress"
    override val nombre = "Paquetexpress"
    override val patron = Regex("^([A-Z]{3}\\d{2}[A-Z0-9]{6,12}|\\d{11,12})$")
    override fun urlPublica(guia: String) = "https://www.paquetexpress.com.mx/rastreo/$guia"

    fun parsear(texto: String): List<Movimiento> {
        val crudo = texto.trim().removePrefix("Resultado(").removeSuffix(")")
        val a = runCatching { JSONArray(crudo) }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val e = a.getJSONObject(i)
            val fecha = e.optLong("fechahora").takeIf { it > 0 } ?: return@mapNotNull null
            val desc = limpiar(e.optString("status")) ?: limpiar(e.optString("eventoDescripcion").takeIf { it != "null" }) ?: return@mapNotNull null
            val evento = e.optString("eventoId")
            val ciudad = if (evento in setOf("HDC", "HTC", "HDR")) e.optString("ciudadDestino") else e.optString("ciudadEvento")
            val estado = when {
                evento == "HDC" || e.optString("eventoImagen") == "paso6.gif" -> Estado.ENTREGADO
                else -> Clasificador.estado(desc)
            }
            Movimiento(fecha, desc, limpiar(ciudad?.removeSuffix(", Mexico")), estado)
        }
    }

    override suspend fun rastrear(guia: String): List<Movimiento> = withContext(Dispatchers.IO) {
        parsear(Http.get("https://cc.paquetexpress.com.mx/ptxws/rest/api/v1/guia/historico/${URLEncoder.encode(guia, "UTF-8")}/@1@2@3@4@5?source=WEBPAGE"))
    }
}

object NoventaYNueve : Paqueteria {
    override val id = "99minutos"
    override val nombre = "99minutos"
    override val patron = Regex("^(\\d{10}|99[A-Z]{3}\\d{9}|MX99\\d{10})$")
    override val fuerte get() = false
    override fun urlPublica(guia: String) = "https://tracking.99minutos.com/search/$guia"
    private const val API = "https://tracking-prd.hub.99min.io/api/v1/tracking"

    private val estados = mapOf(
        "Created" to Estado.PENDIENTE, "Collected" to Estado.EN_TRANSITO, "Warehouse" to Estado.EN_TRANSITO,
        "Linehaul" to Estado.EN_TRANSITO, "ToDelivery" to Estado.EN_REPARTO, "Delivery" to Estado.ENTREGADO,
        "UnsuccessfulDelivery" to Estado.INCIDENCIA, "Return" to Estado.INCIDENCIA, "Cancelled" to Estado.INCIDENCIA
    )
    private val textos = mapOf(
        "Created" to "Envío creado", "Collected" to "Recolectado", "Warehouse" to "En almacén de 99minutos",
        "Linehaul" to "En tránsito entre ciudades", "ToDelivery" to "En ruta de entrega", "Delivery" to "Entregado",
        "UnsuccessfulDelivery" to "Intento de entrega fallido", "Return" to "En devolución", "Cancelled" to "Cancelado"
    )

    override suspend fun rastrear(guia: String): List<Movimiento> = withContext(Dispatchers.IO) {
        val h = mapOf("Accept" to "application/json", "x-action" to "search", "Origin" to "https://tracking.99minutos.com", "Referer" to "https://tracking.99minutos.com/")
        val r = JSONArray(Http.postJson("$API/concerns", JSONObject().put("concerns", JSONArray().put(guia)).put("token", "secure").toString(), h))
        val c = r.optJSONObject(0) ?: return@withContext emptyList()
        if (c.optString("status") != "success") return@withContext emptyList()
        val tid = c.optString("tracking_id").ifBlank { return@withContext emptyList() }
        val det = runCatching { JSONArray(Http.postJson("$API/$tid/detail", "{}", h)) }.getOrNull() ?: JSONArray()
        val movs = (0 until det.length()).mapNotNull { i ->
            val e = det.getJSONObject(i)
            val fecha = Fechas.iso(e.optString("created_at")) ?: return@mapNotNull null
            val st = e.optString("status")
            val comentario = limpiar(e.optJSONObject("metadata")?.optString("comment"))
            Movimiento(fecha, comentario ?: textos[st] ?: st, null, estados[st] ?: Clasificador.estado(comentario ?: st))
        }
        if (movs.isNotEmpty()) return@withContext movs
        val st = c.optString("status_web")
        if (st.isBlank()) emptyList() else listOf(Movimiento(System.currentTimeMillis(), textos[st] ?: st, null, estados[st] ?: Estado.EN_TRANSITO))
    }
}

object Redpack : Paqueteria {
    override val id = "redpack"
    override val nombre = "Redpack"
    override val patron = Regex("^\\d{8,9}$")
    override val fuerte get() = false
    override fun urlPublica(guia: String) = "https://www.redpack.com.mx/es/rastreo/?guias=$guia"

    private var nonce: String? = null
    private var nonceEn = 0L

    private fun obtenerNonce(forzar: Boolean): String {
        val n = nonce
        if (!forzar && n != null && System.currentTimeMillis() - nonceEn < 6 * 3600_000L) return n
        val html = Http.get("https://www.redpack.com.mx/es/rastreo/")
        val nuevo = Regex("rastreoRedpackVars\\s*=\\s*\\{\"url\":\"[^\"]*\",\"nonce\":\"([a-f0-9]+)\"").find(html)?.groupValues?.get(1) ?: throw SinDatos("Sin nonce")
        nonce = nuevo
        nonceEn = System.currentTimeMillis()
        return nuevo
    }

    private fun texto(o: Any?): String? = when (o) {
        is JSONObject -> limpiar(o.optString("descripcion").ifBlank { o.optString("descripcionSituacion") }.ifBlank { o.optString("nombre") })
        is String -> limpiar(o).takeIf { it != "null" }
        else -> null
    }

    fun parsear(cuerpo: String): List<Movimiento> {
        val r = JSONObject(cuerpo).optJSONObject("return") ?: return emptyList()
        if (r.optJSONObject("resultadoConsumoWS")?.optInt("estatus") == 36) return emptyList()
        val movs = mutableListOf<Movimiento>()
        val paquetes = r.optJSONArray("paquetes")
        if (paquetes != null) for (i in 0 until paquetes.length()) {
            val p = paquetes.optJSONObject(i) ?: continue
            val ev = p.optJSONArray("detalle") ?: p.optJSONArray("eventos") ?: p.optJSONArray("historial") ?: continue
            for (j in 0 until ev.length()) {
                val e = ev.optJSONObject(j) ?: continue
                val f = Fechas.iso(e.optString("fecha")) ?: Fechas.con(e.optString("fecha"), "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "dd/MM/yyyy") ?: continue
                val d = texto(e.opt("situacion")) ?: texto(e.opt("descripcion")) ?: texto(e.opt("evento")) ?: continue
                movs += Movimiento(f, d, texto(e.opt("ciudad")) ?: texto(e.opt("plaza")) ?: texto(e.opt("oficina")))
            }
        }
        if (movs.isEmpty()) {
            val d = texto(r.opt("situacion")) ?: return emptyList()
            val f = Fechas.iso(r.optString("fechaSituacion")) ?: Fechas.con(r.optString("fechaSituacion"), "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "dd/MM/yyyy") ?: System.currentTimeMillis()
            val recibio = limpiar(r.optString("personaRecibio")).takeIf { it != "null" }
            movs += Movimiento(f, if (recibio != null) "$d · recibió $recibio" else d, texto(r.optJSONObject("consignatario")?.opt("ciudad")),
                if (recibio != null) Estado.ENTREGADO else Clasificador.estado(d))
        }
        return movs
    }

    override suspend fun rastrear(guia: String): List<Movimiento> = withContext(Dispatchers.IO) {
        fun pedir(n: String) = Http.postForm("https://www.redpack.com.mx/wp-admin/admin-ajax.php", mapOf("action" to "notify_button_click_rastreo", "nonce" to n, "guias" to guia))
        var r = pedir(obtenerNonce(false))
        if (r.trim().startsWith("Busted")) r = pedir(obtenerNonce(true))
        runCatching { parsear(r) }.getOrDefault(emptyList())
    }
}

object CorreosMexico : Paqueteria {
    override val id = "correos"
    override val nombre = "Correos de México"
    override val patron = Regex("^[A-Z]{2}\\d{9}[A-Z]{2}$")
    override val fuerte = true
    override fun urlPublica(guia: String) = "https://www.correosdemexico.gob.mx/SSLServicios/SeguimientoEnvio/Seguimiento.aspx"
    private const val URL = "https://www.correosdemexico.gob.mx/SSLServicios/SeguimientoEnvio/Seguimiento.aspx"

    fun parsear(html: String): List<Movimiento> {
        val d = Jsoup.parse(html)
        val recibe = d.selectFirst("input#PersonaRecibe")?.attr("value")?.trim()?.ifBlank { null }
        return d.select("table#GDDatos tr:has(td)").mapNotNull { fila ->
            val c = fila.select("td")
            if (c.size < 4) return@mapNotNull null
            val fecha = Fechas.con("${c[0].text().trim()} ${c[1].text().trim()}", "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm") ?: return@mapNotNull null
            val evento = limpiar(c[3].text()) ?: return@mapNotNull null
            val entregado = evento.equals("Entrega", true) || evento.startsWith("Entregad", true)
            Movimiento(fecha, if (entregado && recibe != null) "Entregado · recibió $recibe" else evento, limpiar(c[2].text()), if (entregado) Estado.ENTREGADO else Clasificador.estado(evento))
        }
    }

    override suspend fun rastrear(guia: String): List<Movimiento> = withContext(Dispatchers.IO) {
        val h = mapOf("Referer" to URL, "Origin" to "https://www.correosdemexico.gob.mx")
        val año = Year.now().value
        for (periodo in listOf(año, año - 1, año - 2)) {
            val inicio = Jsoup.parse(Http.get(URL, h))
            val campos = inicio.select("input[type=hidden]").associate { it.attr("name") to it.attr("value") }.toMutableMap()
            campos["__EVENTTARGET"] = "Busqueda"
            campos["__EVENTARGUMENT"] = ""
            campos["Guia"] = guia
            campos["Periodo"] = periodo.toString()
            val movs = parsear(Http.postForm(URL, campos, h))
            if (movs.isNotEmpty()) return@withContext movs
        }
        emptyList()
    }
}

object Dhl : Paqueteria {
    override val id = "dhl"
    override val nombre = "DHL"
    override val patron = Regex("^(\\d{10}|JJD\\d{10,20}|JD\\d{18})$")
    override fun urlPublica(guia: String) = "https://www.dhl.com/mx-es/home/rastreo.html?tracking-id=$guia&submit=1"

    fun parsear(cuerpo: String): List<Movimiento> {
        val s = JSONObject(cuerpo).optJSONArray("shipments")?.optJSONObject(0) ?: return emptyList()
        val ev = s.optJSONArray("events") ?: return emptyList()
        return (0 until ev.length()).mapNotNull { i ->
            val e = ev.getJSONObject(i)
            val fecha = Fechas.iso(e.optString("timestamp")) ?: return@mapNotNull null
            val desc = limpiar(e.optString("description")) ?: limpiar(e.optString("status")) ?: return@mapNotNull null
            val lugar = e.optJSONObject("location")?.optJSONObject("address")?.optString("addressLocality")
            val estado = when (e.optString("statusCode")) {
                "delivered" -> Estado.ENTREGADO
                "failure" -> Estado.INCIDENCIA
                "pre-transit" -> Estado.PENDIENTE
                else -> Clasificador.estado(desc)
            }
            Movimiento(fecha, desc, limpiar(lugar), estado)
        }
    }

    override suspend fun rastrear(guia: String): List<Movimiento> {
        val cuerpo = Navegador.capturar(urlPublica(guia), "/utapi", { estado, t -> estado == 200 && t.contains("shipments") || estado == 404 }) ?: return emptyList()
        return runCatching { parsear(cuerpo) }.getOrDefault(emptyList())
    }
}

object FedEx : Paqueteria {
    override val id = "fedex"
    override val nombre = "FedEx"
    override val patron = Regex("^(\\d{12}|\\d{15}|\\d{20}|\\d{22})$")
    override fun urlPublica(guia: String) = "https://www.fedex.com/fedextrack/?trknbr=$guia&locale=es_MX"

    fun parsear(cuerpo: String): List<Movimiento> {
        val p = JSONObject(cuerpo).optJSONObject("output")?.optJSONArray("packages")?.optJSONObject(0) ?: return emptyList()
        if ((p.optJSONArray("errorList")?.length() ?: 0) > 0 && p.optJSONArray("scanEventList") == null) return emptyList()
        val ev = p.optJSONArray("scanEventList") ?: return emptyList()
        return (0 until ev.length()).mapNotNull { i ->
            val e = ev.getJSONObject(i)
            val fecha = Fechas.iso("${e.optString("date")}T${e.optString("time")}${e.optString("gmtOffset")}")
                ?: Fechas.iso("${e.optString("date")}T${e.optString("time")}") ?: return@mapNotNull null
            val desc = limpiar(e.optString("status")) ?: return@mapNotNull null
            val det = limpiar(e.optString("scanDetails"))
            val estado = when (e.optString("statusCD")) {
                "DL" -> Estado.ENTREGADO
                "OD" -> Estado.EN_REPARTO
                "DE", "CA", "RS" -> Estado.INCIDENCIA
                else -> Clasificador.estado("$desc ${det.orEmpty()}")
            }
            Movimiento(fecha, if (det != null && det != desc) "$desc · $det" else desc, limpiar(e.optString("scanLocation")), estado)
        }
    }

    override suspend fun rastrear(guia: String): List<Movimiento> {
        val cuerpo = Navegador.capturar(urlPublica(guia), "/track/v2/shipments", { estado, t -> estado == 200 && t.contains("packages") }) ?: return emptyList()
        return runCatching { parsear(cuerpo) }.getOrDefault(emptyList())
    }
}

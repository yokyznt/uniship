package com.yokis.uniship.tiendas

import android.content.Context
import com.yokis.uniship.BuildConfig
import com.yokis.uniship.Notificador
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.data.Fuente
import com.yokis.uniship.data.Origen
import com.yokis.uniship.data.Paquete
import com.yokis.uniship.paqueterias.Clasificador
import com.yokis.uniship.paqueterias.Movimiento
import com.yokis.uniship.paqueterias.Registro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

object Amazon {
    private const val SITIO = "https://www.amazon.com.mx"

    data class Pedido(val id: String, val titulo: String?, val estado: String?, val rastreo: String?)
    data class Rastreo(val guia: String?, val paqueteria: String?, val promesa: String?, val movimientos: List<Movimiento>)

    private val numero = Regex("\\b(\\d{3}-\\d{7}-\\d{7}|D\\d{2}-\\d{7}-\\d{7})\\b")

    private fun texto(d: org.jsoup.nodes.Element?, vararg sel: String) =
        sel.firstNotNullOfOrNull { s -> d?.selectFirst(s)?.text()?.trim()?.ifBlank { null } }

    fun pedidos(html: String): List<Pedido> {
        val d = Jsoup.parse(html, SITIO)
        val tarjetas = d.select("div.order-card, div.js-order-card, div.order, div.a-box-group.order").ifEmpty { d.select("[data-component=orderCard]") }
        val lista = tarjetas.mapNotNull { c ->
            val id = numero.find(c.text())?.value ?: return@mapNotNull null
            val enlace = c.select("a[href]").map { it.absUrl("href") }.firstOrNull { "ship-track" in it || "progress-tracker" in it }
            Pedido(
                id,
                texto(c, "div.yohtmlc-product-title", ".yohtmlc-item a.a-link-normal", "[data-component=itemTitle]", "a[href*=/dp/]:not(:has(img))", "a[href*=/gp/product/]:not(:has(img))"),
                texto(c, ".yohtmlc-shipment-status-primaryText", "span.delivery-box__primary-text", ".delivery-box__primary-text", "h4.od-status-message"),
                enlace
            )
        }
        if (lista.isNotEmpty()) return lista.distinctBy { it.id }
        return d.select("a.item-card__link[aria-label]").mapNotNull { a ->
            val url = a.absUrl("href")
            val id = Regex("orderId=([\\w-]+)").find(url)?.groupValues?.get(1) ?: return@mapNotNull null
            val etiqueta = a.attr("aria-label")
            Pedido(id, etiqueta.substringBefore(",").trim().ifBlank { null }, etiqueta.substringAfter(",", "").trim().ifBlank { null }, null)
        }.distinctBy { it.id }
    }

    private val meses = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4, "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
        "septiembre" to 9, "setiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12,
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6, "july" to 7, "august" to 8,
        "september" to 9, "october" to 10, "november" to 11, "december" to 12
    )

    fun fecha(dia: String, hora: String?, hoy: LocalDate = LocalDate.now()): Long? {
        val b = Clasificador.sinAcentos(dia)
        val mes = meses.entries.firstOrNull { Regex("\\b${it.key}\\b").containsMatchIn(b) }?.value ?: return null
        val num = Regex("\\b(\\d{1,2})\\b").find(b)?.groupValues?.get(1)?.toInt() ?: return null
        val anio = Regex("\\b(20\\d{2})\\b").find(b)?.groupValues?.get(1)?.toInt()
        var f = runCatching { LocalDate.of(anio ?: hoy.year, mes, num) }.getOrNull() ?: return null
        if (anio == null && f.isAfter(hoy.plusDays(2))) f = f.minusYears(1)
        val h = hora?.let { Regex("(\\d{1,2}):(\\d{2})\\s*([ap])?\\.?\\s*m?", RegexOption.IGNORE_CASE).find(it) }?.let { m ->
            var hh = m.groupValues[1].toInt()
            val ap = m.groupValues[3].lowercase()
            if (ap == "p" && hh < 12) hh += 12
            if (ap == "a" && hh == 12) hh = 0
            LocalTime.of(hh.coerceIn(0, 23), m.groupValues[2].toInt().coerceIn(0, 59))
        } ?: LocalTime.NOON
        return f.atTime(h).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun rastreo(html: String, hoy: LocalDate = LocalDate.now()): Rastreo {
        val d: Document = Jsoup.parse(html, SITIO)
        val guia = texto(d, ".pt-delivery-card-trackingId", ".tracking-event-trackingId-text")
            ?.replace(Regex("(?i).*(tracking id|id de rastreo|n[uú]mero de rastreo|n[uú]mero de seguimiento)\\s*:?"), "")?.trim()?.split(" ")?.firstOrNull()?.ifBlank { null }
        val paqueteria = texto(d, ".tracking-event-carrier-header h2", ".tracking-event-carrier-header")
            ?.replace(Regex("(?i)^(shipped with|delivered by|delivery (facilitated )?by|carrier:?|enviado con|entregado por|transportista:?|entrega (facilitada )?por)\\s+"), "")
            ?.substringBefore(" Tracking")?.substringBefore(" ID de")?.trim()?.ifBlank { null }
        val promesa = texto(d, ".pt-promise-main-slot", "#primaryStatus", ".pt-status-main-text")?.take(80)
        val movs = mutableListOf<Movimiento>()
        var dia: String? = null
        var n = 0
        for (e in d.select(".tracking-event-date, .tracking-event-time-right, .tracking-event-time")) {
            when {
                e.hasClass("tracking-event-date") -> dia = e.text()
                e.hasClass("tracking-event-time-right") -> {
                    val msg = texto(e, ".tracking-event-message") ?: continue
                    val lugar = texto(e, ".tracking-event-location")
                    val hora = e.parent()?.selectFirst(".tracking-event-time")?.text()
                    val f = dia?.let { fecha(it, hora, hoy) } ?: (System.currentTimeMillis() - 60_000L * n)
                    movs += Movimiento(f, msg, lugar)
                    n++
                }
            }
        }
        return Rastreo(guia, paqueteria, promesa, movs)
    }

    private fun bajar(ctx: Context, url: String): String {
        var u = url
        repeat(6) {
            val req = Request.Builder().url(u).header("User-Agent", Tiendas.agente(ctx)).header("Accept-Language", "es-MX,es;q=0.9").build()
            Tiendas.cliente.newCall(req).execute().use { r ->
                if (r.code in 300..399) {
                    val loc = r.header("Location") ?: throw SesionCaducada(Tienda.AMAZON)
                    u = u.toHttpUrl().resolve(loc).toString()
                    if ("/ap/signin" in u || "/gp/sign-in" in u) throw SesionCaducada(Tienda.AMAZON)
                    return@use
                }
                val cuerpo = r.body?.string().orEmpty()
                if ("form name=\"signIn\"" in cuerpo || "name='signIn'" in cuerpo) throw SesionCaducada(Tienda.AMAZON)
                if (BuildConfig.DEBUG) android.util.Log.d("UniShip", "amazon ${r.code} $u ${cuerpo.length}")
                return cuerpo
            }
        }
        throw SesionCaducada(Tienda.AMAZON)
    }

    suspend fun probar(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching { bajar(ctx, "$SITIO/your-orders/orders?timeFilter=last30"); true }.getOrDefault(false)
    }

    suspend fun sincronizar(ctx: Context): Int = withContext(Dispatchers.IO) {
        val repo = UniShipApp.repo
        val lista = pedidos(bajar(ctx, "$SITIO/your-orders/orders?timeFilter=months-3"))
        if (BuildConfig.DEBUG) android.util.Log.d("UniShip", "amazon pedidos: " + lista.joinToString { "${it.id} ${it.estado} ${it.rastreo != null}" })
        var guias = 0
        for ((i, p) in lista.withIndex()) {
            val clave = "amz:${p.id}"
            val existente = repo.paquetes.porPedido(clave)
            if (existente != null && !existente.activo) continue
            val est = Clasificador.sinAcentos(p.estado.orEmpty())
            val terminado = "entregado" in est || "cancelad" in est || "delivered" in est
            if (existente == null && (terminado || p.rastreo == null)) continue
            if (i > 0) delay(1200L + (0..1500).random())
            val r = p.rastreo?.let { runCatching { rastreo(bajar(ctx, it)) }.getOrElse { e -> if (e is SesionCaducada) throw e; null } }
            val guia = r?.guia?.let { Registro.normalizar(it) }
            var paq = existente
                ?: guia?.let { g -> repo.paquetes.porGuia(g)?.takeIf { it.pedidoExterno == null }?.copy(pedidoExterno = clave)?.also { repo.paquetes.actualizar(it) } }
                ?: Paquete(titulo = p.titulo ?: "Pedido de Amazon", tienda = "Amazon", origen = Origen.AMAZON, pedidoExterno = clave).let { it.copy(id = repo.paquetes.insertar(it)) }
            if (paq.guia == null && guia != null && repo.paquetes.porGuia(guia) == null) {
                paq = paq.copy(guia = guia, paqueteria = Registro.detectar(guia).firstOrNull()?.id)
                guias++
            }
            if (r?.promesa != null) paq = paq.copy(entregaEstimada = r.promesa)
            repo.paquetes.actualizar(paq)
            val movs = r?.movimientos?.takeIf { it.isNotEmpty() } ?: listOfNotNull(p.estado?.let { Movimiento(System.currentTimeMillis(), it) })
            val nuevos = repo.aplicar(paq, movs, Fuente.TIENDA)
            Notificador.movimientos(ctx, repo.paquetes.porId(paq.id) ?: paq, nuevos)
        }
        Tiendas.marcar(Tienda.AMAZON, true)
        guias
    }
}

package com.yokis.uniship.tiendas

import android.content.Context
import com.yokis.uniship.BuildConfig
import com.yokis.uniship.Notificador
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.data.Estado
import com.yokis.uniship.data.Fuente
import com.yokis.uniship.data.Origen
import com.yokis.uniship.data.Paquete
import com.yokis.uniship.paqueterias.Clasificador
import com.yokis.uniship.paqueterias.Movimiento
import com.yokis.uniship.paqueterias.Registro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.TimeZone

object AliExpress {
    private const val ACS = "https://acs.aliexpress.com"
    private const val SITIO = "https://www.aliexpress.com"
    private const val APP_KEY = "12574478"
    private const val LISTA = "mtop.aliexpress.trade.buyer.order.list"
    private const val RASTREO = "mtop.ae.ld.querydetail"

    class Bloqueo(msg: String) : Exception(msg)

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun zona(): String {
        val m = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
        val h = Math.abs(m) / 60
        val r = Math.abs(m) % 60
        return "GMT" + (if (m >= 0) "+" else "-") + h + (if (r > 0) ":" + r.toString().padStart(2, '0') else "")
    }

    private fun regional(): Pair<String, String> {
        val c = Tiendas.cookie(SITIO, "aep_usuc_f").orEmpty()
        val v = c.split("&").associate { it.substringBefore("=") to it.substringAfter("=", "") }
        return (v["region"]?.ifBlank { null } ?: "MX") to (v["b_locale"]?.ifBlank { null } ?: "es_MX")
    }

    fun firmar(token: String, t: String, datos: String) = md5("$token&$t&$APP_KEY&$datos")

    private fun llamar(ctx: Context, api: String, datos: JSONObject, post: Boolean = false): JSONObject {
        val (region, idioma) = regional()
        if (!datos.has("shipToCountry")) datos.put("shipToCountry", region)
        if (!datos.has("_lang")) datos.put("_lang", if (idioma.startsWith("es")) idioma else "es_MX")
        val texto = datos.toString()
        repeat(3) {
            val token = Tiendas.cookie(ACS, "_m_h5_tk")?.substringBefore("_").orEmpty()
            val t = System.currentTimeMillis().toString()
            val url = "$ACS/h5/$api/1.0/".toHttpUrl().newBuilder()
                .addQueryParameter("jsv", "2.5.1").addQueryParameter("appKey", APP_KEY).addQueryParameter("t", t)
                .addQueryParameter("sign", firmar(token, t, texto)).addQueryParameter("api", api).addQueryParameter("v", "1.0")
                .addQueryParameter("type", "originaljson").addQueryParameter("dataType", "json").addQueryParameter("timeout", "15000")
                .addQueryParameter("ecode", "1").addQueryParameter("needLogin", "true")
                .apply { if (!post) addQueryParameter("data", texto) }.build()
            val req = Request.Builder().url(url)
                .header("User-Agent", Tiendas.agente(ctx)).header("Referer", "$SITIO/").header("Origin", SITIO).header("Accept", "application/json")
                .apply { if (post) post(FormBody.Builder().add("data", texto).build()) }.build()
            val cuerpo = Tiendas.cliente.newCall(req).execute().use { r ->
                if (r.code in 300..399) throw SesionCaducada(Tienda.ALIEXPRESS)
                r.body?.string().orEmpty()
            }
            val j = runCatching { JSONObject(cuerpo) }.getOrElse { throw Bloqueo("respuesta no JSON") }
            val ret = j.optJSONArray("ret")?.optString(0).orEmpty()
            if (BuildConfig.DEBUG) android.util.Log.d("UniShip", "ae $api $ret ${cuerpo.length} ${cuerpo.take(300)}")
            when {
                ret.startsWith("SUCCESS") -> return j.optJSONObject("data") ?: JSONObject()
                ret.startsWith("FAIL_SYS_TOKEN") || ret.startsWith("FAIL_SYS_ILLEGAL_ACCESS") -> Unit
                ret.startsWith("FAIL_SYS_SESSION_EXPIRED") || ret.startsWith("NEED_LOGIN") || ret.startsWith("FAIL_SYS_SID_INVALID") -> throw SesionCaducada(Tienda.ALIEXPRESS)
                else -> throw Bloqueo(ret)
            }
        }
        throw Bloqueo("token rechazado")
    }

    data class Pedido(val id: String, val titulo: String?, val estado: String?, val tienda: String?)

    fun pedidos(datos: JSONObject): List<Pedido> {
        val d = datos.optJSONObject("data") ?: return emptyList()
        val estructura = (datos.optJSONObject("hierarchy") ?: datos.optJSONObject("page"))?.optJSONObject("structure")
        val orden = mutableListOf<String>()
        estructura?.keys()?.forEach { k -> estructura.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) orden += a.optString(i) } }
        val llaves = (orden.filter { d.has(it) } + d.keys().asSequence().filter { it !in orden }).distinct()
        return llaves.mapNotNull { k ->
            val c = d.optJSONObject(k) ?: return@mapNotNull null
            val tag = c.optString("tag").ifBlank { c.optString("compName") }
            if (tag != "pc_om_list_order") return@mapNotNull null
            val f = c.optJSONObject("fields") ?: return@mapNotNull null
            val id = f.optString("orderId").ifBlank { k.substringAfterLast("_") }.ifBlank { return@mapNotNull null }
            Pedido(
                id,
                f.optJSONArray("orderLines")?.optJSONObject(0)?.optString("itemTitle")?.ifBlank { null },
                f.optString("statusText").trim().ifBlank { null },
                f.optString("storeName").ifBlank { null }
            )
        }
    }

    data class Envio(val guia: String?, val paqueteria: String?, val eta: String?, val movimientos: List<Movimiento>)

    fun envios(modulo: JSONObject): List<Envio> {
        val lineas = modulo.optJSONArray("trackingDetailLineList") ?: return emptyList()
        return (0 until lineas.length()).mapNotNull { i ->
            val l = lineas.optJSONObject(i) ?: return@mapNotNull null
            val eventos = l.optJSONArray("detailList") ?: JSONArray()
            val movs = (0 until eventos.length()).mapNotNull { e ->
                val ev = eventos.optJSONObject(e) ?: return@mapNotNull null
                val desc = ev.optString("trackingDetailDesc").ifBlank { ev.optString("trackingName") }.ifBlank { return@mapNotNull null }
                val fecha = ev.optLong("time").takeIf { it > 0 } ?: return@mapNotNull null
                val codigo = ev.optString("trackingPrimaryCode").uppercase()
                val estado = when {
                    "SIGNED" in codigo || codigo.endsWith("DELIVERED") || "HO_SUCCESS" in codigo -> Estado.ENTREGADO
                    else -> Clasificador.estado(desc)
                }
                Movimiento(fecha, desc, null, estado)
            }
            Envio(
                l.optString("mailNo").ifBlank { null } ?: l.optString("originMailNo").ifBlank { null },
                l.optString("logisticsCarrierName").ifBlank { null },
                l.optJSONObject("etaInfo")?.let { eta ->
                    eta.optLong("endEtaTime").takeIf { it > 0 }?.let { fecha(it) } ?: eta.optString("etaTimeText").ifBlank { null }
                },
                movs
            )
        }
    }

    fun fecha(ms: Long): String = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM", java.util.Locale.forLanguageTag("es-MX")))

    private fun lista(ctx: Context, pestana: String) = pedidos(
        llamar(ctx, LISTA, JSONObject().put("statusTab", pestana).put("renderType", "init").put("clientPlatform", "pc").put("timeZone", zona()))
    )

    fun rastreo(ctx: Context, id: String) = envios(
        llamar(ctx, RASTREO, JSONObject().put("tradeOrderId", id).put("tradeOrderLineId", "").put("terminalType", "PC").put("needPageDisplayInfo", true).put("timeZone", zona()))
            .optJSONObject("module") ?: JSONObject()
    )

    suspend fun probar(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching { lista(ctx, "all"); true }.getOrDefault(false)
    }

    suspend fun sincronizar(ctx: Context): Int = withContext(Dispatchers.IO) {
        val repo = UniShipApp.repo
        if (BuildConfig.DEBUG) for (t in listOf("all", "shipped", "processing", "completed")) android.util.Log.d("UniShip", "ae pestaña $t: " + runCatching { lista(ctx, t).joinToString { it.id + " " + it.estado } }.getOrElse { it.toString() })
        val activos = (lista(ctx, "shipped").map { it to true } + lista(ctx, "processing").map { it to false }).distinctBy { it.first.id }
        val previos = repo.paquetes.todos().filter { it.activo && it.pedidoExterno?.startsWith("ae:") == true }
            .map { it.pedidoExterno!!.removePrefix("ae:").substringBefore(":") }.toSet()
        val ids = (activos.map { it.first.id } + previos).distinct()
        var guias = 0
        for ((i, id) in ids.withIndex()) {
            if (i > 0) delay(1500L + (0..1500).random())
            val pedido = activos.firstOrNull { it.first.id == id }?.first
            val enviado = activos.firstOrNull { it.first.id == id }?.second ?: true
            val lista = if (enviado) runCatching { rastreo(ctx, id) }.getOrElse { if (it is SesionCaducada) throw it; emptyList() } else emptyList()
            if (lista.isEmpty()) {
                if (repo.paquetes.porPedido("ae:$id") == null && pedido != null) {
                    repo.paquetes.insertar(Paquete(titulo = pedido.titulo ?: "Pedido de AliExpress", tienda = "AliExpress", origen = Origen.ALIEXPRESS, pedidoExterno = "ae:$id", ultimoEvento = pedido.estado))
                }
                continue
            }
            lista.forEachIndexed { n, e ->
                val clave = if (n == 0) "ae:$id" else "ae:$id:$n"
                val guia = e.guia?.let { Registro.normalizar(it) }
                var p = repo.paquetes.porPedido(clave)
                    ?: guia?.let { g -> repo.paquetes.porGuia(g)?.takeIf { it.pedidoExterno == null }?.let { it.copy(pedidoExterno = clave).also { c -> repo.paquetes.actualizar(c) } } }
                    ?: Paquete(titulo = pedido?.titulo ?: "Pedido de AliExpress", tienda = "AliExpress", origen = Origen.ALIEXPRESS, pedidoExterno = clave).let { it.copy(id = repo.paquetes.insertar(it)) }
                if (p.guia == null && guia != null && repo.paquetes.porGuia(guia) == null) {
                    p = p.copy(guia = guia, paqueteria = Registro.detectar(guia).firstOrNull()?.id)
                    guias++
                }
                if (e.eta != null && p.entregaEstimada != e.eta) p = p.copy(entregaEstimada = e.eta)
                repo.paquetes.actualizar(p)
                val nuevos = repo.aplicar(p, e.movimientos, Fuente.TIENDA)
                Notificador.movimientos(ctx, repo.paquetes.porId(p.id) ?: p, nuevos)
            }
        }
        Tiendas.marcar(Tienda.ALIEXPRESS, true)
        guias
    }
}

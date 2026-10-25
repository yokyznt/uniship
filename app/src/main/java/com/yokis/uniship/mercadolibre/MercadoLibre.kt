package com.yokis.uniship.mercadolibre

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.yokis.uniship.BuildConfig
import com.yokis.uniship.Notificador
import com.yokis.uniship.SesionMl
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.data.Estado
import com.yokis.uniship.data.Fuente
import com.yokis.uniship.data.Origen
import com.yokis.uniship.data.Paquete
import com.yokis.uniship.paqueterias.Fechas
import com.yokis.uniship.paqueterias.Http
import com.yokis.uniship.paqueterias.Movimiento
import com.yokis.uniship.paqueterias.Registro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

object MercadoLibre {
    private const val API = "https://api.mercadolibre.com"
    val redireccion get() = "${BuildConfig.SERVER_URL}/api/ml/callback"

    fun configurado() = BuildConfig.ML_CLIENT_ID.isNotBlank()

    private fun aleatorio(n: Int): String {
        val b = ByteArray(n).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    suspend fun urlAutorizacion(): Uri {
        val verificador = aleatorio(48)
        val estado = aleatorio(16)
        val reto = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verificador.toByteArray()), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        UniShipApp.prefs.guardarVerificador("$estado|$verificador")
        return Uri.parse("https://auth.mercadolibre.com.mx/authorization").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", BuildConfig.ML_CLIENT_ID)
            .appendQueryParameter("redirect_uri", redireccion)
            .appendQueryParameter("code_challenge", reto)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", estado)
            .build()
    }

    suspend fun completar(uri: Uri) {
        uri.getQueryParameter("error")?.let { error("Mercado Libre: $it") }
        val code = uri.getQueryParameter("code") ?: error("Sin código de autorización")
        val guardado = UniShipApp.prefs.verificador()?.split("|") ?: error("Vuelve a intentar conectar")
        if (guardado.size != 2 || guardado[0] != uri.getQueryParameter("state")) error("La respuesta no coincide, vuelve a intentar")
        val r = intercambiar(JSONObject().put("code", code).put("code_verifier", guardado[1]))
        UniShipApp.prefs.guardarVerificador(null)
        guardar(r)
    }

    private suspend fun intercambiar(cuerpo: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(Http.postJson("${BuildConfig.SERVER_URL}/api/ml/token", cuerpo.toString()))
    }

    private suspend fun guardar(r: JSONObject): SesionMl {
        val s = SesionMl(
            acceso = r.getString("access_token"),
            refresco = r.getString("refresh_token"),
            expira = System.currentTimeMillis() + r.optLong("expires_in", 21600) * 1000 - 120_000,
            usuario = r.optLong("user_id")
        )
        UniShipApp.prefs.guardarMl(s)
        return s
    }

    private suspend fun sesion(): SesionMl? {
        val s = UniShipApp.prefs.sesionMl() ?: return null
        if (System.currentTimeMillis() < s.expira && s.acceso.isNotBlank()) return s
        return runCatching { guardar(intercambiar(JSONObject().put("refresh_token", s.refresco))) }.getOrNull()
    }

    private fun get(ruta: String, token: String) =
        JSONObject(Http.get(API + ruta, mapOf("Authorization" to "Bearer $token", "x-format-new" to "true", "Accept" to "application/json")))

    fun estado(status: String?, substatus: String?): Estado = when (status) {
        "delivered" -> Estado.ENTREGADO
        "not_delivered", "cancelled" -> Estado.INCIDENCIA
        "shipped" -> if (substatus == "out_for_delivery") Estado.EN_REPARTO else Estado.EN_TRANSITO
        "ready_to_ship", "handling", "pending" -> Estado.PENDIENTE
        else -> Estado.EN_TRANSITO
    }

    fun descripcion(status: String?, substatus: String?): String {
        val s = when (substatus) {
            "out_for_delivery" -> "Salió a entrega"
            "waiting_for_withdrawal" -> "Listo para recoger en sucursal"
            "receiver_absent" -> "No había nadie para recibirlo"
            "returning_to_sender" -> "Regresando al vendedor"
            "printed" -> "El vendedor imprimió la etiqueta"
            "ready_to_print" -> "El vendedor está preparando tu paquete"
            "in_hub" -> "En centro de distribución"
            "soon_deliver" -> "Llega pronto"
            "delayed" -> "Tu envío viene con retraso"
            else -> null
        }
        return s ?: when (status) {
            "pending" -> "Pago pendiente o envío por confirmar"
            "handling" -> "El vendedor está preparando tu paquete"
            "ready_to_ship" -> "Listo para enviarse"
            "shipped" -> "En camino"
            "delivered" -> "Entregado"
            "not_delivered" -> "No se pudo entregar"
            "cancelled" -> "Envío cancelado"
            else -> status ?: "Actualización de Mercado Libre"
        }
    }

    suspend fun sincronizar(ctx: Context) = withContext(Dispatchers.IO) {
        val s = sesion() ?: return@withContext
        val repo = UniShipApp.repo
        val uid = if (s.usuario > 0) s.usuario else get("/users/me", s.acceso).getLong("id")
        val ordenes = get("/orders/search?buyer=$uid&sort=date_desc&limit=20", s.acceso).optJSONArray("results") ?: return@withContext
        val limite = System.currentTimeMillis() - 45L * 24 * 3600 * 1000
        for (i in 0 until ordenes.length()) {
            val o = ordenes.getJSONObject(i)
            val envio = o.optJSONObject("shipping")?.optLong("id")?.takeIf { it > 0 } ?: continue
            val creada = Fechas.iso(o.optString("date_created")) ?: 0L
            val pedido = "ml:${o.getLong("id")}"
            val existente = repo.paquetes.porPedido(pedido)
            if (existente == null && creada < limite) continue
            if (existente != null && !existente.activo) continue
            val sh = runCatching { get("/shipments/$envio", s.acceso) }.getOrNull() ?: continue
            val status = sh.optString("status").ifBlank { null }
            val sub = sh.optString("substatus").ifBlank { null }
            val guia = sh.optString("tracking_number").ifBlank { null }
            val titulo = o.optJSONArray("order_items")?.optJSONObject(0)?.optJSONObject("item")?.optString("title")?.ifBlank { null } ?: "Pedido de Mercado Libre"
            var p = existente ?: Paquete(titulo = titulo, tienda = "Mercado Libre", origen = Origen.MERCADO_LIBRE, pedidoExterno = pedido, creado = creada.takeIf { it > 0 } ?: System.currentTimeMillis())
                .let { it.copy(id = repo.paquetes.insertar(it)) }
            if (p.guia == null && guia != null) {
                val detectada = Registro.detectar(guia).firstOrNull()
                if (detectada != null && repo.paquetes.porGuia(Registro.normalizar(guia)) == null) {
                    p = p.copy(guia = Registro.normalizar(guia), paqueteria = detectada.id)
                    repo.paquetes.actualizar(p)
                }
            }
            val hist = sh.optJSONObject("status_history")
            val fecha = listOf("date_delivered", "date_not_delivered", "date_shipped", "date_ready_to_ship", "date_handling")
                .firstNotNullOfOrNull { k -> hist?.optString(k)?.let { Fechas.iso(it) } }
                ?: Fechas.iso(sh.optString("last_updated")) ?: System.currentTimeMillis()
            val ciudad = sh.optJSONObject("destination")?.optJSONObject("shipping_address")?.optJSONObject("city")?.optString("name")?.ifBlank { null }
                ?: sh.optJSONObject("receiver_address")?.optJSONObject("city")?.optString("name")?.ifBlank { null }
            val mov = Movimiento(fecha, descripcion(status, sub), ciudad, estado(status, sub))
            val nuevos = repo.aplicar(p, listOf(mov), Fuente.MERCADO_LIBRE)
            Notificador.movimientos(ctx, repo.paquetes.porId(p.id) ?: p, nuevos)
            if (p.guia != null) runCatching { com.yokis.uniship.trabajo.Rastreador.rastrear(ctx, repo.paquetes.porId(p.id) ?: p) }
        }
    }

    suspend fun desconectar() = UniShipApp.prefs.borrarMl()
}

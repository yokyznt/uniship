package com.yokis.uniship.paqueterias

import com.yokis.uniship.UniShipApp
import com.yokis.uniship.data.Estado
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

object JtExpress : Paqueteria {
    override val id = "jt"
    override val nombre = "J&T Express"
    override val patron = Regex("^JMX\\d{12}$")
    override val fuerte = true
    override fun urlPublica(guia: String) = "https://www.jtexpress.mx/"

    private val lugar = Regex("^【([^】]*)】\\s*")

    override suspend fun rastrear(guia: String): List<Movimiento> = withContext(Dispatchers.IO) {
        val cel = UniShipApp.prefs.ultimos4() ?: throw SinDatos("J&T pide los últimos 4 dígitos de tu celular")
        val url = "https://official.jtjms-mx.com/official/logisticsTracking/v3/getDetailByWaybillNo?waybillNo=${URLEncoder.encode(guia, "UTF-8")}&phoneVerify=$cel"
        val j = JSONObject(Http.get(url, mapOf("langType" to "ES", "timezone" to "GMT-0600", "Origin" to "https://www.jtexpress.mx", "Referer" to "https://www.jtexpress.mx/")))
        if (j.optInt("code") != 1) throw SinDatos(j.optString("msg"))
        val datos = j.optJSONArray("data")?.optJSONObject(0) ?: return@withContext emptyList()
        val det = datos.optJSONArray("details") ?: return@withContext emptyList()
        (0 until det.length()).mapNotNull { i ->
            val d = det.getJSONObject(i)
            val crudo = d.optString("customerTracking").ifBlank { d.optString("waybillTrackingContent") }.ifBlank { d.optString("scanTypeName") }
            val ciudad = lugar.find(crudo)?.groupValues?.get(1)?.ifBlank { null } ?: d.optString("scanNetworkCity").ifBlank { null }
            val texto = crudo.replace(lugar, "").replace(Regex("【\\s*】"), "").trim().ifBlank { d.optString("scanTypeName") }
            val fecha = Fechas.con(d.optString("scanTime"), "yyyy-MM-dd HH:mm:ss") ?: return@mapNotNull null
            val tipo = d.optString("scanTypeName")
            Movimiento(fecha, texto, ciudad, Clasificador.estado("$tipo $texto"))
        }
    }
}

object IMile : Paqueteria {
    override val id = "imile"
    override val nombre = "iMile"
    override val patron = Regex("^(6\\d{12}|IM[A-Z]{0,3}\\d{9,16})$")
    override fun urlPublica(guia: String) = "https://www.imile.com/MX-es/track?waybillNo=$guia"

    private const val LLAVE = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3dFPiKNZwt+HoBbPAG/t7kZC2k3pBX2eCl5LeyeW8woNuEV5bA5kB9Y9KKTOQng62ERGPLwi84CdIB8s265ljQUib//iO3jVrZesJueO5Xu+s80s3Z/89jgJleT1XawN1GubgkGXOoT1a7tvX8+aItkGgR//48ELqJVVUL+yGsBtXxFjNmOEWxBJNQuwAf9yWcCIl1enD60GjZjPWrsfw8QUqam7K5e45ealcPEYGenNePwuPpCq6twdD0YYYzKdRN0dZP1uTviFpNfph90c9YgQ8kgDkRMcpjVv6KZ+bg5JZ4sK6LkV4vwOjPijisthHBvUXhu3fyhMgvoDO/j5gwIDAQAB"
    private val lugar = Regex("【([^】]*)】")

    private fun firma(guia: String): String {
        val llave = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(java.util.Base64.getDecoder().decode(LLAVE)))
        val c = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.ENCRYPT_MODE, llave) }
        return java.util.Base64.getEncoder().encodeToString(c.doFinal(guia.toByteArray()))
    }

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun consultar(guia: String, idioma: String): List<Movimiento> {
        val url = "https://www.imile.com/saastms/mobileWeb/track/query?waybillNo=$guia&code=${md5(guia + "imileTrackQuery2024")}"
        val j = JSONObject(Http.get(url, mapOf("lang" to idioma, "sign" to firma(guia), "Referer" to "https://www.imile.com/MX-es/track")))
        val infos = j.optJSONObject("resultObject")?.optJSONArray("trackInfos") ?: return emptyList()
        return (0 until infos.length()).mapNotNull { i ->
            val t = infos.getJSONObject(i)
            val fecha = Fechas.con(t.optString("time"), "yyyy-MM-dd HH:mm:ss") ?: return@mapNotNull null
            val contenido = t.optString("content").substringBefore("\uD83D").substringBefore("If you have any questions").trim()
            val estado = when (t.optInt("trackStage")) {
                2060 -> Estado.ENTREGADO
                1003 -> Estado.EN_REPARTO
                1001, 1000 -> Estado.PENDIENTE
                else -> Clasificador.estado(t.optString("trackStageTx") + " " + contenido)
            }
            val estacion = t.optString("operateStationName").ifBlank { null } ?: lugar.find(contenido)?.groupValues?.get(1)
            Movimiento(fecha, contenido.replace(lugar) { " " + it.groupValues[1] + " " }.replace(Regex("\\s+"), " ").trim().ifBlank { t.optString("trackStageTx") }, estacion, estado)
        }
    }

    override suspend fun rastrear(guia: String): List<Movimiento> = withContext(Dispatchers.IO) { combinar(guia) }

    fun combinar(guia: String): List<Movimiento> {
        val es = consultar(guia, "es_MX")
        if (es.any { it.estado == Estado.ENTREGADO }) return es
        val en = runCatching { consultar(guia, "en_US") }.getOrDefault(emptyList())
        val minutos = es.map { it.fecha / 60000 }.toSet()
        return es + en.filter { it.fecha / 60000 !in minutos && it.estado == Estado.ENTREGADO }.map { it.copy(descripcion = "Paquete entregado") }
    }
}

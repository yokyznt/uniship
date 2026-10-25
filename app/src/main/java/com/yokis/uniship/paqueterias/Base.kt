package com.yokis.uniship.paqueterias

import com.yokis.uniship.data.Estado
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Movimiento(
    val fecha: Long,
    val descripcion: String,
    val ubicacion: String? = null,
    val estado: Estado = Clasificador.estado(descripcion)
)

class SinDatos(msg: String) : Exception(msg)

interface Paqueteria {
    val id: String
    val nombre: String
    val patron: Regex
    val fuerte: Boolean get() = false
    fun urlPublica(guia: String): String
    fun reconoce(guia: String) = patron.matches(guia)
    suspend fun rastrear(guia: String): List<Movimiento>
}

object Http {
    const val UA = "Mozilla/5.0 (Linux; Android 14; Nothing Phone) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
    private val json = "application/json; charset=utf-8".toMediaType()
    private val form = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()

    val cliente: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .connectionSpecs(listOf(okhttp3.ConnectionSpec.MODERN_TLS, okhttp3.ConnectionSpec.COMPATIBLE_TLS))
        .cookieJar(Galletas)
        .build()

    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        ejecutar(Request.Builder().url(url).get(), headers)

    fun postJson(url: String, cuerpo: String, headers: Map<String, String> = emptyMap()): String =
        ejecutar(Request.Builder().url(url).post(cuerpo.toRequestBody(json)), headers)

    fun postForm(url: String, campos: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val cuerpo = campos.entries.joinToString("&") { java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8") }
        return ejecutar(Request.Builder().url(url).post(cuerpo.toRequestBody(form)), headers)
    }

    private fun ejecutar(b: Request.Builder, headers: Map<String, String>): String {
        b.header("User-Agent", UA).header("Accept-Language", "es-MX,es;q=0.9")
        headers.forEach { (k, v) -> b.header(k, v) }
        cliente.newCall(b.build()).execute().use { r ->
            val texto = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw SinDatos("HTTP ${r.code}")
            return texto
        }
    }
}

object Galletas : okhttp3.CookieJar {
    private val guardadas = java.util.concurrent.ConcurrentHashMap<String, List<okhttp3.Cookie>>()
    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
        val previas = guardadas[url.host].orEmpty().filter { c -> cookies.none { it.name == c.name } }
        guardadas[url.host] = previas + cookies
    }
    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
        val ahora = System.currentTimeMillis()
        return guardadas[url.host].orEmpty().filter { it.expiresAt > ahora }
    }
}

object Clasificador {
    private fun base(t: String) = Normalizer.normalize(t.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private val reglas = listOf(
        Estado.INCIDENCIA to listOf("no entregado", "intento de entrega", "intento fallido", "devuel", "retorno", "excepcion", "incidencia", "rechaz", "direccion incorrecta", "no se pudo", "problema", "retenido", "exception", "returned", "failed", "cancelad"),
        Estado.ENTREGADO to listOf("entregado", "entregamos", "fue entregado", "se entrego", "delivered", "llego tu paquete", "ya llego", "ya tienes tu"),
        Estado.EN_REPARTO to listOf("en ruta de entrega", "en reparto", "salio a entrega", "salio para entrega", "en camino a tu domicilio", "llega hoy", "llegara hoy", "out for delivery", "con el repartidor", "en ruta a tu domicilio", "proximo a entregar"),
        Estado.EN_TRANSITO to listOf("transito", "en camino", "salio", "llego a", "arribo", "recolect", "recibido en", "centro de distribucion", "centro operativo", "hub", "despach", "enviado", "fue enviado", "en ruta", "aduana", "clasificado", "transfer", "in transit", "shipped", "picked up", "departed", "arrived", "procesado", "embarcado"),
        Estado.PENDIENTE to listOf("guia generada", "documentacion", "documentado", "manifiesto", "etiqueta", "preparando", "pendiente", "informacion recibida", "label created", "creado", "en preparacion", "registrado")
    )

    fun estado(texto: String): Estado {
        val t = base(texto)
        if (Regex("\\bno\\s+(fue\\s+)?entregad").containsMatchIn(t)) return Estado.INCIDENCIA
        if (t.contains("recibido por") && listOf("estafeta", "oficina", "sucursal", "centro", "almacen", "paqueteria", "dhl", "fedex", "redpack").none { t.contains(it) }) return Estado.ENTREGADO
        if (listOf("documentacion en linea", "incluida en manifiesto", "eliminada del manifiesto", "guia generada").any { t.contains(it) }) return Estado.PENDIENTE
        for ((estado, claves) in reglas) if (claves.any { t.contains(it) }) return estado
        return Estado.EN_TRANSITO
    }

    private fun gritado(t: String): Boolean {
        val letras = t.filter { it.isLetter() }
        return letras.length >= 4 && letras.count { it.isUpperCase() } >= letras.length * 0.8
    }

    fun bonito(t: String): String {
        val limpio = t.replace(Regex("\\s+"), " ").replace(Regex("\\s*,\\s*"), ", ").trim().trimEnd(',').trim()
        if (!gritado(limpio)) return limpio
        val minus = limpio.lowercase(Locale.forLanguageTag("es-MX"))
        return minus.replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-MX")) }
    }

    fun lugar(t: String): String {
        val limpio = t.replace(Regex("\\s+"), " ").trim()
        if (!gritado(limpio)) return limpio
        return limpio.lowercase(Locale.forLanguageTag("es-MX")).split(" ").joinToString(" ") { p ->
            if (p.length <= 5 && p.endsWith(".")) p.uppercase() else if (p in setOf("de", "del", "la", "el", "y")) p else p.replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-MX")) }
        }
    }

    fun esDeEnvio(texto: String): Boolean {
        val t = base(texto)
        return listOf("pedido", "paquete", "envio", "entrega", "guia", "rastreo", "repartidor", "llega", "transito", "enviado", "order", "package", "shipment", "delivery").any { t.contains(it) }
    }

    fun huella(m: Movimiento): String {
        val minuto = m.fecha / 60000
        val crudo = "$minuto|${base(m.descripcion).trim()}|${base(m.ubicacion.orEmpty()).trim()}"
        return MessageDigest.getInstance("SHA-1").digest(crudo.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
    }
}

object Fechas {
    val MX: ZoneId = ZoneId.of("America/Mexico_City")
    private val es = Locale.forLanguageTag("es-MX")

    fun iso(t: String?): Long? {
        if (t.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(t).atZone(MX).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.Instant.parse(t).toEpochMilli() }.getOrNull()
    }

    fun con(t: String?, vararg patrones: String): Long? {
        if (t.isNullOrBlank()) return null
        val limpio = t.trim().replace(Regex("\\s+"), " ").replace(".", "").let { it }
        for (p in patrones) {
            val f = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p.replace(".", "")).toFormatter(es)
            runCatching { return LocalDateTime.parse(limpio, f).atZone(MX).toInstant().toEpochMilli() }
            runCatching { return LocalDate.parse(limpio, f).atStartOfDay(MX).toInstant().toEpochMilli() }
        }
        return null
    }

    fun epoch(n: Long?): Long? = n?.let { if (it < 100_000_000_000L) it * 1000 else it }

    fun corta(ms: Long): String = DateTimeFormatter.ofPattern("d MMM, HH:mm", es).format(java.time.Instant.ofEpochMilli(ms).atZone(MX))
}

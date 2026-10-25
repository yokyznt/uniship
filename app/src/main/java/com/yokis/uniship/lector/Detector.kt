package com.yokis.uniship.lector

import android.content.Context
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.data.Fuente
import com.yokis.uniship.data.Origen
import com.yokis.uniship.data.Paquete
import com.yokis.uniship.paqueterias.Clasificador
import com.yokis.uniship.paqueterias.Movimiento
import com.yokis.uniship.paqueterias.Registro
import com.yokis.uniship.trabajo.Rastreador
import java.text.Normalizer
import java.util.Locale

object Fuentes {
    const val GMAIL = "com.google.android.gm"

    val apps get() = if (com.yokis.uniship.BuildConfig.DEBUG) lista + ("com.android.shell" to "Shein") else lista

    private val lista = mapOf(
        "com.mercadolibre" to "Mercado Libre",
        "com.mercadopago.wallet" to "Mercado Libre",
        "com.amazon.mShop.android.shopping" to "Amazon",
        "in.amazon.mShop.android.shopping" to "Amazon",
        "com.einnovation.temu" to "Temu",
        "com.zzkko" to "Shein",
        "com.alibaba.aliexpresshd" to "AliExpress",
        "com.zhiliaoapp.musically" to "TikTok Shop",
        "com.ss.android.ugc.trill" to "TikTok Shop",
        "com.dhl.exp.dhlmobile" to "DHL",
        "com.fedex.ida.android" to "FedEx",
        GMAIL to ""
    )

    private val tiendas = listOf(
        "mercado libre" to "Mercado Libre", "mercadolibre" to "Mercado Libre",
        "amazon" to "Amazon", "temu" to "Temu", "shein" to "Shein",
        "aliexpress" to "AliExpress", "tiktok" to "TikTok Shop",
        "dhl" to "DHL", "fedex" to "FedEx", "estafeta" to "Estafeta", "j&t" to "J&T Express",
        "jt express" to "J&T Express", "imile" to "iMile", "99minutos" to "99minutos",
        "paquetexpress" to "Paquetexpress", "redpack" to "Redpack", "correos de mexico" to "Correos de México"
    )

    private val promo = listOf("oferta", "descuento", "cupon", "rebaja", "% off", "promocion", "gratis", "black friday", "hot sale", "buen fin", "venta", "precio")

    fun base(t: String) = Normalizer.normalize(t.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    fun paqueteDe(tienda: String?): String? = tienda?.let { t -> lista.entries.firstOrNull { it.value == t } ?.key }

    fun tiendaEnTexto(t: String): String? {
        val b = base(t)
        return tiendas.firstOrNull { b.contains(it.first) }?.second
    }

    fun esPromo(t: String): Boolean {
        val b = base(t)
        return promo.any { b.contains(it) }
    }
}

object Detector {
    data class Lectura(val tienda: String, val texto: String, val fuente: String)

    fun interpretar(paquete: String, titulo: String, cuerpo: String): Lectura? {
        val tiendaApp = Fuentes.apps[paquete] ?: return null
        val texto = listOf(titulo, cuerpo).filter { it.isNotBlank() }.distinct().joinToString(": ").replace(Regex("\\s+"), " ").trim()
        if (texto.isBlank()) return null
        if (paquete == Fuentes.GMAIL) {
            val tienda = Fuentes.tiendaEnTexto(texto) ?: return null
            if (Fuentes.esPromo(texto) || !Clasificador.esDeEnvio(texto)) return null
            return Lectura(tienda, texto, Fuente.CORREO)
        }
        if (Fuentes.esPromo(texto) && Registro.extraer(texto).isEmpty()) return null
        return Lectura(tiendaApp, texto, Fuente.APP)
    }

    suspend fun procesar(ctx: Context, l: Lectura, cuando: Long) {
        val repo = UniShipApp.repo
        val guias = Registro.extraer(l.texto)
        val agregados = guias.map { (guia, p) ->
            repo.agregarGuia(guia, p.id, null, l.tienda, Origen.NOTIFICACION).also { Rastreador.ahora(ctx, it.id) }
        }
        if (!Clasificador.esDeEnvio(l.texto)) return
        val destino: Paquete = agregados.firstOrNull()
            ?: repo.paquetes.ultimoSinGuia(l.tienda)
            ?: Paquete(titulo = "Pedido de ${l.tienda}", tienda = l.tienda, origen = Origen.NOTIFICACION).let { it.copy(id = repo.paquetes.insertar(it)) }
        val descripcion = l.texto.take(240)
        val previo = repo.eventos.listar(destino.id).firstOrNull()
        if (previo != null && previo.descripcion == descripcion) return
        repo.aplicar(destino, listOf(Movimiento(cuando, descripcion)), l.fuente)
    }
}

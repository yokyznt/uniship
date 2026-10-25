package com.yokis.uniship.lector

import android.content.ClipboardManager
import android.content.Context
import com.yokis.uniship.UniShipApp
import com.yokis.uniship.data.Paquete
import com.yokis.uniship.paqueterias.Registro
import com.yokis.uniship.trabajo.Rastreador

object Compartido {
    private var ultimoPortapapeles: String? = null

    fun guias(texto: String): List<Pair<String, String>> {
        val directas = Registro.extraer(texto).map { it.first to it.second.id }
        if (directas.isNotEmpty()) return directas
        val sola = Registro.normalizar(texto.trim())
        if (texto.trim().length <= 40 && sola.any { it.isDigit() }) Registro.detectar(sola).firstOrNull()?.let { return listOf(sola to it.id) }
        return emptyList()
    }

    suspend fun agregar(ctx: Context, texto: String, origen: String): List<Paquete> {
        val repo = UniShipApp.repo
        val tienda = Fuentes.tiendaEnTexto(texto)
        return guias(texto).mapNotNull { (g, p) ->
            if (repo.paquetes.porGuia(g) != null) return@mapNotNull null
            val pendiente = tienda?.let { repo.paquetes.ultimoSinGuia(it) }
            val nuevo = if (pendiente != null) pendiente.copy(guia = g, paqueteria = p).also { repo.paquetes.actualizar(it) }
            else repo.agregarGuia(g, p, null, tienda, origen)
            Rastreador.ahora(ctx, nuevo.id)
            nuevo
        }
    }

    suspend fun revisarPortapapeles(ctx: Context): List<Paquete> {
        val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return emptyList()
        val texto = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() }.getOrNull()?.takeIf { it.isNotBlank() && it.length < 2000 } ?: return emptyList()
        if (texto == ultimoPortapapeles) return emptyList()
        ultimoPortapapeles = texto
        if (guias(texto).none { Registro.porId(it.second)?.fuerte == true }) return emptyList()
        return agregar(ctx, texto, com.yokis.uniship.data.Origen.PORTAPAPELES)
    }
}

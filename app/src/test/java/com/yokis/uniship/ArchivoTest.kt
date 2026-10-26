package com.yokis.uniship

import com.yokis.uniship.data.Archivo
import com.yokis.uniship.data.Estado
import com.yokis.uniship.data.Evento
import com.yokis.uniship.data.Paquete
import com.yokis.uniship.data.Respaldo
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchivoTest {
    @Test
    fun idaYVuelta() {
        val r = Respaldo(
            paquetes = listOf(Paquete(id = 3, guia = "IM08030053384313", paqueteria = "imile", tienda = "TikTok Shop", titulo = "Protector", estado = Estado.EN_TRANSITO)),
            eventos = listOf(Evento(paqueteId = 3, fecha = 1L, descripcion = "En camino", estado = Estado.EN_TRANSITO, fuente = "paqueteria", huella = "x"))
        )
        val de = Archivo.decodificar(Archivo.codificar(r))
        assertEquals(r.paquetes, de.paquetes)
        assertEquals(r.eventos, de.eventos)
    }
}

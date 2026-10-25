package com.yokis.uniship

import com.yokis.uniship.lector.Compartido
import org.junit.Assert.assertEquals
import org.junit.Test

class CompartidoTest {
    @Test
    fun guiaSolaCopiada() {
        assertEquals("JMX501055891038", Compartido.guias("JMX501055891038").single().first)
    }

    @Test
    fun guiaDentroDeTexto() {
        val g = Compartido.guias("Tu pedido de TikTok Shop va con J&T MX Envío estándar, número de guía: JMX501055891038")
        assertEquals("JMX501055891038", g.single().first)
    }

    @Test
    fun textoSinGuia() {
        assertEquals(0, Compartido.guias("Hola, ¿cómo estás?").size)
    }
}

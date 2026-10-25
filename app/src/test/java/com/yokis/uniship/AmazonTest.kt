package com.yokis.uniship

import com.yokis.uniship.tiendas.Amazon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AmazonTest {
    private fun recurso(n: String) = javaClass.classLoader!!.getResource("amazon/$n")!!.readText()
    private val hoy = LocalDate.of(2026, 1, 20)

    @Test
    fun listaDePedidos() {
        val p = Amazon.pedidos(recurso("order-history-2026-0.html"))
        assertTrue(p.size >= 5)
        assertTrue(p.all { Regex("\\d{3}-\\d{7}-\\d{7}|D\\d{2}-\\d{7}-\\d{7}").matches(it.id) })
        assertTrue(p.any { it.titulo != null })
    }

    @Test
    fun rastreoUps() {
        val r = Amazon.rastreo(recurso("progress-tracker-ups.html"), hoy)
        assertEquals("1Z999AA10123456784", r.guia)
        assertEquals("UPS", r.paqueteria)
        assertTrue(r.movimientos.size >= 3)
        assertEquals("Package delivered.", r.movimientos.first().descripcion)
        val f = java.time.Instant.ofEpochMilli(r.movimientos.first().fecha).atZone(ZoneId.systemDefault())
        assertEquals(1, f.monthValue); assertEquals(5, f.dayOfMonth); assertEquals(11, f.hour)
    }

    @Test
    fun rastreoAmazonLogistics() {
        val r = Amazon.rastreo(recurso("progress-tracker-amazon.html"), hoy)
        assertEquals("TBA000000000000", r.guia)
        assertEquals("Amazon", r.paqueteria)
        assertNotNull(r.promesa)
    }

    @Test
    fun fechasEnEspanol() {
        val f = Amazon.fecha("martes, 6 de enero", "4:03 p.m.", hoy)!!
        val z = java.time.Instant.ofEpochMilli(f).atZone(ZoneId.systemDefault())
        assertEquals(6, z.dayOfMonth); assertEquals(16, z.hour); assertEquals(2026, z.year)
        val dic = java.time.Instant.ofEpochMilli(Amazon.fecha("30 de diciembre", null, hoy)!!).atZone(ZoneId.systemDefault())
        assertEquals(2025, dic.year)
    }
}

package com.yokis.uniship

import com.yokis.uniship.data.Estado
import com.yokis.uniship.tiendas.AliExpress
import com.yokis.uniship.tiendas.Temu
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TiendasTest {
    private fun recurso(n: String) = javaClass.classLoader!!.getResource("aliexpress/$n")!!.readText()

    @Test
    fun listaDeAliExpress() {
        val p = AliExpress.pedidos(JSONObject(recurso("order-list-init.json")).getJSONObject("data"))
        assertTrue(p.size >= 5)
        assertTrue(p.all { it.id.all(Char::isDigit) })
        assertTrue(p.any { it.titulo != null })
    }

    @Test
    fun rastreoDeAliExpress() {
        val e = AliExpress.envios(JSONObject(recurso("logistics-querydetail.json")).getJSONObject("data").getJSONObject("module"))
        assertTrue(e.isNotEmpty())
        val primero = e.first()
        assertTrue(primero.guia != null)
        assertTrue(primero.movimientos.size > 10)
        assertTrue(primero.eta!!.contains(" de "))
        assertEquals(Estado.ENTREGADO, primero.movimientos.first().estado)
    }

    @Test
    fun firmaMtop() {
        assertEquals(32, AliExpress.firmar("abc", "1", "{}").length)
        assertEquals(AliExpress.firmar("abc", "1", "{}"), AliExpress.firmar("abc", "1", "{}"))
    }

    @Test
    fun guiasEnTemu() {
        val j = """{"result":{"order_list":[{"parent_order_sn":"PO-211-123","goods_name":"Funda","package":{"tracking_number":"JMX501055891038"}}]}}"""
        val h = Temu.buscar(j)
        assertEquals(1, h.size)
        assertEquals("PO-211-123", h[0].pedido)
        assertEquals("jt", h[0].paqueteria)
        assertEquals("Funda", h[0].titulo)
    }
}

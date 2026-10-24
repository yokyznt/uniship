package com.yokis.uniship

import com.yokis.uniship.data.Estado
import com.yokis.uniship.paqueterias.Clasificador
import com.yokis.uniship.paqueterias.CorreosMexico
import com.yokis.uniship.paqueterias.Estafeta
import com.yokis.uniship.paqueterias.Paquetexpress
import com.yokis.uniship.paqueterias.Registro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    private fun recurso(n: String) = javaClass.classLoader!!.getResource(n)!!.readText()

    @Test fun estafetaGuiaLarga() {
        assertEquals("6015491862604H80002045", Estafeta.guiaLarga(recurso("estafeta_resumen.html")))
    }

    @Test fun estafetaHistorial() {
        val m = Estafeta.historial(recurso("estafeta_historial.html"))
        assertTrue(m.size >= 3)
        assertEquals("REPORTE GENERADO POR EL CLIENTE", m.first().descripcion)
        assertEquals("MEXICO D.F.", m.first().ubicacion)
    }

    @Test fun correos() {
        val m = CorreosMexico.parsear(recurso("correos.html"))
        assertTrue(m.isNotEmpty())
        assertEquals(Estado.ENTREGADO, m.first().estado)
    }

    @Test fun paquetexpress() {
        val m = Paquetexpress.parsear("""Resultado([{"fecha":" 26 De Junio Del 2025","hora":"14:50","sucursal":"Gdl02","status":"Mercancía Entregada A Domicilio, recibió Javier Galindo","eventoId":"HDC","eventoDescripcion":"null","eventoImagen":"paso6.gif","ciudadDestino":"Guadalajara, Jalisco, Mexico","ciudadEvento":"Tlajomulco De Zuñiga, Jalisco, Mexico","fechahora":1750971052000}])""")
        assertEquals(1, m.size)
        assertEquals(Estado.ENTREGADO, m[0].estado)
        assertEquals("Guadalajara, Jalisco", m[0].ubicacion)
    }

    @Test fun clasificador() {
        assertEquals(Estado.ENTREGADO, Clasificador.estado("¡Envío entregado!"))
        assertEquals(Estado.INCIDENCIA, Clasificador.estado("No entregado: domicilio cerrado"))
        assertEquals(Estado.EN_REPARTO, Clasificador.estado("Tu paquete salió a entrega"))
        assertEquals(Estado.EN_TRANSITO, Clasificador.estado("Llegó a centro de distribución Querétaro"))
        assertEquals(Estado.PENDIENTE, Clasificador.estado("Guía generada"))
        assertEquals(Estado.EN_TRANSITO, Clasificador.estado("Recibido por estafeta"))
        assertEquals(Estado.ENTREGADO, Clasificador.estado("Recibido por: Juan Pérez"))
        assertEquals("Item rechazado por cliente, rechaza cliente", Clasificador.bonito("ITEM RECHAZADO POR CLIENTE  ,RECHAZA CLIENTE"))
        assertEquals("Mexico D.F.", Clasificador.lugar("MEXICO D.F."))
    }

    @Test fun deteccion() {
        assertEquals("jt", Registro.detectar("JMX101751112946").first().id)
        assertEquals("correos", Registro.detectar("MP558391618MX").first().id)
        assertEquals("paquetexpress", Registro.detectar("MEX14WW83900").first().id)
        assertEquals("estafeta", Registro.detectar("6015491862604H80002045").first().id)
        assertTrue(Registro.detectar("1234567890").any { it.id == "dhl" })
    }

    @Test fun extraer() {
        val r = Registro.extraer("Shein: Tu pedido fue enviado con J&T, guía JMX101751112946. Pedido GSH123")
        assertEquals(listOf("JMX101751112946"), r.map { it.first })
        assertTrue(Registro.extraer("Tu pedido 1234567890 está listo").isEmpty())
        assertEquals("1234567890", Registro.extraer("Tu número de guía DHL es 1234567890").first().first)
    }
}

package com.yokis.uniship

import com.yokis.uniship.ui.Lugares
import org.junit.Assert.assertEquals
import org.junit.Test

class LugaresTest {
    @Test
    fun limpiaEstaciones() {
        assertEquals("Ciudad de México", Lugares.limpiar("Mexico Center"))
        assertEquals("Salina Cruz", Lugares.limpiar("SLC-Salina Cruz.DS"))
        assertEquals("Santo Domingo Tehuantepec", Lugares.limpiar("Santo Domingo Tehuantepec"))
        assertEquals("Cuautitlán Izcalli", Lugares.limpiar("Mexico.SC"))
    }

    @Test
    fun decodificaPolilinea() {
        val p = com.yokis.uniship.ui.Rutas.decodificar("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 1e5)
        assertEquals(3, p.size)
        assertEquals(38.5, p[0].latitude, 1e-6)
        assertEquals(-120.2, p[0].longitude, 1e-6)
        assertEquals(43.252, p[2].latitude, 1e-6)
    }
}

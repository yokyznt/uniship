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
}

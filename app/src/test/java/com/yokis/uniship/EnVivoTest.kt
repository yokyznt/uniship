package com.yokis.uniship

import com.yokis.uniship.paqueterias.CorreosMexico
import com.yokis.uniship.paqueterias.Estafeta
import com.yokis.uniship.paqueterias.IMile
import com.yokis.uniship.paqueterias.NoventaYNueve
import com.yokis.uniship.paqueterias.Paquetexpress
import com.yokis.uniship.paqueterias.Redpack
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class EnVivoTest {
    @Before fun soloConRed() = assumeTrue(System.getenv("UNISHIP_EN_VIVO") == "1")

    private fun mostrar(n: String, bloque: suspend () -> List<com.yokis.uniship.paqueterias.Movimiento>) {
        val r = runCatching { runBlocking { bloque() } }
        println("== $n: " + r.fold({ l -> "${l.size} eventos" + l.take(3).joinToString { "\n   ${it.estado} | ${it.ubicacion} | ${it.descripcion}" } }, { "ERROR ${it.message}" }))
    }

    @Test fun todas() {
        mostrar("Estafeta 22") { Estafeta.rastrear("6015491862604H80002045") }
        mostrar("Estafeta 10") { Estafeta.rastrear("3825671510") }
        mostrar("Paquetexpress") { Paquetexpress.rastrear("MEX14WW83900") }
        mostrar("Correos") { CorreosMexico.rastrear("MP558391618MX") }
        mostrar("iMile") { IMile.rastrear("6120924763844") }
        mostrar("99minutos") { NoventaYNueve.rastrear("2139671481") }
        mostrar("Redpack") { Redpack.rastrear("123456789") }
    }
}

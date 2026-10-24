package com.yokis.uniship.paqueterias

object Registro {
    val todas: List<Paqueteria> = listOf(JtExpress, IMile, Estafeta, Dhl, FedEx, NoventaYNueve, Paquetexpress, CorreosMexico, Redpack)

    fun porId(id: String?): Paqueteria? = id?.let { i -> todas.firstOrNull { it.id == i } }

    private val costo = listOf("jt", "correos", "imile", "paquetexpress", "estafeta", "99minutos", "redpack", "dhl", "fedex")

    fun normalizar(guia: String) = guia.uppercase().replace(Regex("[^A-Z0-9]"), "")

    fun detectar(guia: String): List<Paqueteria> {
        val g = normalizar(guia)
        return todas.filter { it.reconoce(g) }.sortedWith(compareByDescending<Paqueteria> { it.fuerte }.thenBy { costo.indexOf(it.id) })
    }

    private val claves = Regex("(?i)(gu[ií]a|rastreo|rastrear|seguimiento|tracking|n[uú]mero de env[ií]o|paqueter[ií]a)")
    private val candidato = Regex("\\b[A-Z0-9][A-Z0-9-]{7,32}[A-Z0-9]\\b")

    fun extraer(texto: String): List<Pair<String, Paqueteria>> {
        val hayClave = claves.containsMatchIn(texto)
        return candidato.findAll(texto.uppercase()).mapNotNull { m ->
            val g = normalizar(m.value)
            if (!g.any { it.isDigit() }) return@mapNotNull null
            val p = detectar(g).firstOrNull { it.fuerte || hayClave } ?: return@mapNotNull null
            g to p
        }.distinctBy { it.first }.toList()
    }
}

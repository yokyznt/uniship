package com.yokis.uniship.data

import androidx.room.withTransaction
import com.yokis.uniship.paqueterias.Clasificador
import com.yokis.uniship.paqueterias.Movimiento
import com.yokis.uniship.paqueterias.Registro

class Repositorio(private val db: Db) {
    val paquetes = db.paquetes()
    val eventos = db.eventos()

    suspend fun agregarGuia(guia: String, paqueteria: String?, titulo: String?, tienda: String?, origen: String): Paquete {
        val limpia = Registro.normalizar(guia)
        paquetes.porGuia(limpia)?.let { existente ->
            val mejor = existente.copy(
                paqueteria = existente.paqueteria ?: paqueteria,
                tienda = existente.tienda ?: tienda,
                titulo = if (existente.titulo == existente.guia && !titulo.isNullOrBlank()) titulo else existente.titulo
            )
            if (mejor != existente) paquetes.actualizar(mejor)
            return mejor
        }
        val p = Paquete(
            guia = limpia,
            paqueteria = paqueteria ?: Registro.detectar(limpia).firstOrNull()?.id,
            tienda = tienda,
            titulo = titulo?.takeIf { it.isNotBlank() } ?: tienda?.let { "Pedido de $it" } ?: limpia,
            origen = origen
        )
        return p.copy(id = paquetes.insertar(p))
    }

    suspend fun aplicar(paquete: Paquete, movimientos: List<Movimiento>, fuente: String): List<Evento> {
        if (movimientos.isEmpty()) return emptyList()
        return db.withTransaction {
            val nuevos = movimientos.mapNotNull { m ->
                val e = Evento(
                    paqueteId = paquete.id,
                    fecha = m.fecha,
                    descripcion = Clasificador.bonito(m.descripcion),
                    ubicacion = m.ubicacion?.let { Clasificador.lugar(it) }?.takeIf { it.isNotEmpty() },
                    estado = m.estado,
                    fuente = fuente,
                    huella = Clasificador.huella(m)
                )
                val id = eventos.insertar(e)
                if (id > 0) e.copy(id = id) else null
            }
            val ultimo = eventos.listar(paquete.id).firstOrNull()
            val actual = paquetes.porId(paquete.id) ?: paquete
            if (ultimo != null) {
                paquetes.actualizar(
                    actual.copy(
                        estado = ultimo.estado,
                        ultimoEvento = ultimo.descripcion,
                        ultimaUbicacion = ultimo.ubicacion,
                        actualizado = maxOf(actual.actualizado, if (nuevos.isEmpty()) actual.actualizado else System.currentTimeMillis()),
                        fallos = 0
                    )
                )
            }
            nuevos
        }
    }

    suspend fun respaldo() = Respaldo(paquetes = paquetes.todos(), eventos = eventos.todos())

    suspend fun restaurar(r: Respaldo) = db.withTransaction {
        paquetes.insertarTodos(r.paquetes)
        eventos.insertarTodos(r.eventos)
    }
}

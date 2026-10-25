package com.yokis.uniship.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class Estado(val texto: String) {
    PENDIENTE("Pendiente"),
    EN_TRANSITO("En tránsito"),
    EN_REPARTO("En reparto"),
    ENTREGADO("Entregado"),
    INCIDENCIA("Incidencia"),
    SIN_DATOS("Sin datos")
}

@Serializable
@Entity(
    tableName = "paquetes",
    indices = [Index(value = ["guia"], unique = true), Index(value = ["pedidoExterno"], unique = true)]
)
data class Paquete(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val guia: String? = null,
    val paqueteria: String? = null,
    val tienda: String? = null,
    val titulo: String,
    val estado: Estado = Estado.PENDIENTE,
    val ultimoEvento: String? = null,
    val ultimaUbicacion: String? = null,
    val actualizado: Long = System.currentTimeMillis(),
    val creado: Long = System.currentTimeMillis(),
    val origen: String = Origen.MANUAL,
    val archivado: Boolean = false,
    val pedidoExterno: String? = null,
    val fallos: Int = 0,
    val entregaEstimada: String? = null
) {
    val activo get() = !archivado && estado != Estado.ENTREGADO
}

@Serializable
@Entity(
    tableName = "eventos",
    foreignKeys = [ForeignKey(entity = Paquete::class, parentColumns = ["id"], childColumns = ["paqueteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("paqueteId"), Index(value = ["paqueteId", "huella"], unique = true)]
)
data class Evento(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paqueteId: Long,
    val fecha: Long,
    val descripcion: String,
    val ubicacion: String? = null,
    val estado: Estado,
    val fuente: String,
    val huella: String
)

object Origen {
    const val MANUAL = "manual"
    const val ESCANEO = "escaneo"
    const val NOTIFICACION = "notificacion"
    const val MERCADO_LIBRE = "mercadolibre"
    const val ALIEXPRESS = "aliexpress"
    const val TEMU = "temu"
    const val AMAZON = "amazon"
    const val COMPARTIDO = "compartido"
    const val PORTAPAPELES = "portapapeles"
}

object Fuente {
    const val PAQUETERIA = "paqueteria"
    const val APP = "app"
    const val CORREO = "correo"
    const val MERCADO_LIBRE = "mercadolibre"
    const val TIENDA = "tienda"
}

@Serializable
data class Respaldo(val version: Int = 1, val paquetes: List<Paquete>, val eventos: List<Evento>)

package com.yokis.uniship.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PaqueteDao {
    @Query("SELECT * FROM paquetes WHERE archivado = 0 AND estado != 'ENTREGADO' ORDER BY actualizado DESC")
    fun enCamino(): Flow<List<Paquete>>

    @Query("SELECT * FROM paquetes WHERE archivado = 1 OR estado = 'ENTREGADO' ORDER BY actualizado DESC")
    fun terminados(): Flow<List<Paquete>>

    @Query("SELECT * FROM paquetes WHERE id = :id")
    fun observar(id: Long): Flow<Paquete?>

    @Query("SELECT * FROM paquetes WHERE id = :id")
    suspend fun porId(id: Long): Paquete?

    @Query("SELECT * FROM paquetes WHERE guia = :guia LIMIT 1")
    suspend fun porGuia(guia: String): Paquete?

    @Query("SELECT * FROM paquetes WHERE pedidoExterno = :pedido LIMIT 1")
    suspend fun porPedido(pedido: String): Paquete?

    @Query("SELECT * FROM paquetes WHERE archivado = 0 AND estado != 'ENTREGADO' AND guia IS NOT NULL")
    suspend fun rastreables(): List<Paquete>

    @Query("SELECT * FROM paquetes WHERE archivado = 0 AND estado != 'ENTREGADO' AND guia IS NULL AND tienda = :tienda ORDER BY actualizado DESC LIMIT 1")
    suspend fun ultimoSinGuia(tienda: String): Paquete?

    @Query("SELECT * FROM paquetes")
    suspend fun todos(): List<Paquete>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(p: Paquete): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodos(p: List<Paquete>)

    @Update
    suspend fun actualizar(p: Paquete)

    @Query("DELETE FROM paquetes WHERE id = :id")
    suspend fun borrar(id: Long)
}

@Dao
interface EventoDao {
    @Query("SELECT * FROM eventos WHERE paqueteId = :id ORDER BY fecha DESC, id DESC")
    fun dePaquete(id: Long): Flow<List<Evento>>

    @Query("SELECT * FROM eventos WHERE paqueteId = :id ORDER BY fecha DESC, id DESC")
    suspend fun listar(id: Long): List<Evento>

    @Query("SELECT * FROM eventos")
    suspend fun todos(): List<Evento>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(e: Evento): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodos(e: List<Evento>)
}

@Database(entities = [Paquete::class, Evento::class], version = 1, exportSchema = true)
abstract class Db : RoomDatabase() {
    abstract fun paquetes(): PaqueteDao
    abstract fun eventos(): EventoDao

    companion object {
        fun crear(ctx: Context) = Room.databaseBuilder(ctx, Db::class.java, "uniship.db").build()
    }
}
